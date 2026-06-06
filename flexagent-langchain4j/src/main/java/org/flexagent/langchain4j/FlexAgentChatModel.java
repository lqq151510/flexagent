package org.flexagent.langchain4j;

import org.flexagent.core.model.Step;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.model.ThinkingMode;
import org.flexagent.core.model.ToolCallPolicy;
import org.flexagent.core.runtime.AgentConfig;
import org.flexagent.core.runtime.AgentRuntime;
import org.flexagent.core.runtime.AgentRuntimeConfig;
import org.flexagent.core.runtime.RuntimeTypes;
import org.flexagent.core.exception.ProviderNotFoundException;
import org.flexagent.core.exception.RuntimeInitializationException;
import org.flexagent.core.exception.FlexAgentException;
import org.flexagent.core.memory.compaction.CompactionStrategy;
import org.flexagent.langchain4j.compaction.SlidingWindowCompactionStrategy;
import org.flexagent.core.runtime.FlexAgentObservationUtils;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.flexagent.core.memory.AgentMemory;
import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.memory.AgentSessionContext;
import org.flexagent.core.model.ToolCall;
import org.flexagent.core.tool.CustomToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.Collections;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FlexAgentChatModel implements ChatLanguageModel, AutoCloseable {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(FlexAgentChatModel.class);

    private final String binaryPath;
    private final String storageDirectory;
    private final String modelName;
    private final String thinkingLevel;
    private final String systemInstruction;
    private final List<Object> toolObjects;
    private final Object delegateModel;
    private final AgentRuntime customRuntime;
    private final ThinkingMode thinkingMode;
    private final ToolCallPolicy toolCallPolicy;
    private final CompactionStrategy compactionStrategy;
    final AgentMemory memory;
    final List<CustomToolExecutor> customToolExecutors;

    // Persistent runtime and tool adapter
    final AgentRuntime activeRuntime;
    final ToolAdapter toolAdapter;
    final org.flexagent.core.strategy.AgentStrategy strategy;
    final List<ChatMessage> initialSystemMessages;

    private FlexAgentChatModel(Builder builder) {
        this.binaryPath = builder.binaryPath;
        this.storageDirectory = builder.storageDirectory;
        this.modelName = builder.modelName;
        this.thinkingLevel = builder.thinkingLevel;
        this.systemInstruction = builder.systemInstruction;
        this.toolObjects = new ArrayList<>(builder.toolObjects);
        this.delegateModel = builder.delegateModel;
        this.customRuntime = builder.customRuntime;
        this.thinkingMode = builder.thinkingMode;
        this.toolCallPolicy = builder.toolCallPolicy;
        this.compactionStrategy = builder.compactionStrategy;
        this.memory = builder.memory;
        this.customToolExecutors = new ArrayList<>(builder.customToolExecutors);
        this.strategy = builder.strategy != null ? builder.strategy : new org.flexagent.core.strategy.ReActStrategy();

        // Initialize persistent runtime and adapter
        try {
            this.activeRuntime = initRuntime(builder.runtimeType);
            this.toolAdapter = new ToolAdapter(this.toolObjects);
            this.initialSystemMessages = snapshotInitialSystemMessages();
        } catch (FlexAgentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeInitializationException(
                    builder.runtimeType != null ? builder.runtimeType : "auto", 
                    e.getMessage(), e
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private AgentRuntime initRuntime(String selectedRuntimeType) {
        AgentRuntime runtime = this.customRuntime;
        if (runtime == null) {
            String type = selectedRuntimeType;
            if (type == null || type.isEmpty()) {
                type = (this.binaryPath != null && !this.binaryPath.isEmpty()) ? RuntimeTypes.LOCAL_HARNESS : RuntimeTypes.LANGCHAIN4J;
            }

            if (this.binaryPath == null && this.delegateModel == null && RuntimeTypes.LANGCHAIN4J.equals(type)) {
                throw new IllegalStateException("delegateModel (for LangChain4j) must be specified when using langchain4j runtime.");
            }

            final String finalType = type;
            ServiceLoader<org.flexagent.core.runtime.AgentRuntimeProvider> loader = 
                    ServiceLoader.load(org.flexagent.core.runtime.AgentRuntimeProvider.class);
            
            List<org.flexagent.core.runtime.AgentRuntimeProvider> matchedProviders = loader.stream()
                    .map(ServiceLoader.Provider::get)
                    .filter(provider -> provider.supports(finalType))
                    .toList();
            
            if (matchedProviders.isEmpty()) {
                throw new ProviderNotFoundException(finalType);
            }
            if (matchedProviders.size() > 1) {
                throw new ProviderNotFoundException(finalType, matchedProviders.size());
            }
            
            org.flexagent.core.runtime.AgentRuntimeProvider provider = matchedProviders.get(0);
            log.info("FlexAgent runtime loaded via SPI: {}", finalType);
            
            AgentRuntimeConfig runtimeConfig = new AgentRuntimeConfig(finalType, this.delegateModel, null);
            runtime = provider.create(runtimeConfig);
        }

        // Prepare configuration
        AgentConfig config = new AgentConfig();
        config.setBinaryPath(this.binaryPath);
        config.setStorageDirectory(this.storageDirectory);
        config.setModelName(this.modelName);
        config.setThinkingLevel(this.thinkingLevel);
        config.setSystemInstruction(this.systemInstruction);
        config.setThinkingMode(this.thinkingMode != null ? this.thinkingMode : ThinkingMode.NONE);
        config.setToolCallPolicy(this.toolCallPolicy != null ? this.toolCallPolicy : ToolCallPolicy.LENIENT);
        if (this.toolObjects != null) {
            for (Object tool : this.toolObjects) {
                config.addToolObject(tool);
            }
        }

        try {
            runtime.initialize(config);
        } catch (IOException e) {
            throw new RuntimeInitializationException(selectedRuntimeType, "Initialize failed", e);
        }

        // Apply compaction strategy if LangChain4jRuntime is used
        if (runtime instanceof org.flexagent.langchain4j.LangChain4jRuntime lc4jRuntime) {
            if (this.compactionStrategy != null) {
                lc4jRuntime.setCompactionStrategy(this.compactionStrategy);
            }
        }

        return runtime;
    }

    @Override
    public synchronized Response<AiMessage> generate(List<ChatMessage> messages) {
        String prompt = "";
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage userMsg) {
                prompt = userMsg.text();
            }
        }

        log.info("Generating response for prompt length: {}", prompt.length());

        String sessionId = AgentSessionContext.get();
        boolean hasMemory = (this.memory != null && sessionId != null);

        if (hasMemory) {
            List<AgentMessage> agentHistory = this.memory.getMessages(sessionId);
            List<ChatMessage> chatHistory = new ArrayList<>();
            if (agentHistory != null && !agentHistory.isEmpty()) {
                FlexAgentObservationUtils.recordMemoryHit(sessionId, true);
                for (AgentMessage am : agentHistory) {
                    chatHistory.add(toChatMessage(am));
                }
            } else {
                FlexAgentObservationUtils.recordMemoryHit(sessionId, false);
            }

            // Sync initial stateless history to memory if memory is empty
            if (chatHistory.isEmpty() && messages != null && messages.size() > 1) {
                List<ChatMessage> initialHistory = new ArrayList<>(messages.subList(0, messages.size() - 1));
                for (ChatMessage cm : initialHistory) {
                    this.memory.addMessage(sessionId, toAgentMessage(cm));
                }
                chatHistory.addAll(initialHistory);
            }

            chatHistory = withInitialSystemMessages(chatHistory);

            if (this.activeRuntime instanceof org.flexagent.langchain4j.LangChain4jRuntime lc4jRuntime) {
                lc4jRuntime.setHistoryMessages(chatHistory);
                lc4jRuntime.setSessionId(sessionId);
            }
        } else {
            // Update conversation history dynamically for the persistent LangChain4j runtime
            if (this.activeRuntime instanceof org.flexagent.langchain4j.LangChain4jRuntime lc4jRuntime) {
                if (messages != null && messages.size() > 1) {
                    lc4jRuntime.setHistoryMessages(withInitialSystemMessages(new ArrayList<>(messages.subList(0, messages.size() - 1))));
                } else {
                    lc4jRuntime.setHistoryMessages(withInitialSystemMessages(new ArrayList<>()));
                }
                lc4jRuntime.setSessionId(sessionId);
            }
        }

        try {
            AgentMessage resultMessage = this.strategy.execute(prompt, this.activeRuntime, toolCall -> {
                log.info("Executing custom Tool: {} (args: {})", toolCall.name(), toolCall.argumentsJson());
                ToolResult toolResult = null;
                for (CustomToolExecutor executor : this.customToolExecutors) {
                    if (executor.supports(toolCall.name())) {
                        toolResult = executor.execute(toolCall);
                        break;
                    }
                }
                if (toolResult == null) {
                    toolResult = this.toolAdapter.execute(toolCall);
                }
                return toolResult;
            });
            AiMessage aiMessage = AiMessage.from(resultMessage.text());

            // Save conversation history back to memory
            if (hasMemory) {
                if (this.activeRuntime instanceof org.flexagent.langchain4j.LangChain4jRuntime lc4jRuntime) {
                    List<ChatMessage> updatedMessages = lc4jRuntime.getChatMessages();
                    List<AgentMessage> updatedAgentMessages = new ArrayList<>();
                    for (ChatMessage cm : updatedMessages) {
                        updatedAgentMessages.add(toAgentMessage(cm));
                    }
                    this.memory.clear(sessionId);
                    this.memory.addMessages(sessionId, updatedAgentMessages);
                } else {
                    // For non-langchain4j runtime, manually record user prompt and assistant response
                    this.memory.addMessage(sessionId, AgentMessage.user(prompt));
                    this.memory.addMessage(sessionId, AgentMessage.assistant(resultMessage.text()));
                }
            }

            return Response.from(aiMessage);

        } catch (FlexAgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error executing FlexAgent Agent Runtime", e);
            throw new FlexAgentException("FlexAgent agent execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws Exception {
        if (this.activeRuntime != null) {
            this.activeRuntime.close();
            log.info("FlexAgentChatModel active runtime successfully closed.");
        }
    }

    // Exposed getter for testing/diagnostic purposes
    public AgentRuntime activeRuntime() {
        return this.activeRuntime;
    }

    public List<Object> toolObjects() {
        return this.toolObjects;
    }

    public Response<AiMessage> generate(String sessionId, String userMessage) {
        AgentSessionContext.set(sessionId);
        try {
            return generate(List.of(UserMessage.from(userMessage)));
        } finally {
            AgentSessionContext.clear();
        }
    }

    public Response<AiMessage> generate(String sessionId, List<ChatMessage> messages) {
        AgentSessionContext.set(sessionId);
        try {
            return generate(messages);
        } finally {
            AgentSessionContext.clear();
        }
    }

    private List<ChatMessage> snapshotInitialSystemMessages() {
        List<ChatMessage> snapshot = new ArrayList<>();
        if (this.activeRuntime instanceof org.flexagent.langchain4j.LangChain4jRuntime lc4jRuntime) {
            List<ChatMessage> runtimeHistory = lc4jRuntime.getChatMessages();
            if (runtimeHistory != null) {
                for (ChatMessage message : runtimeHistory) {
                    if (message instanceof SystemMessage) {
                        snapshot.add(message);
                    }
                }
            }
        } else if (this.systemInstruction != null && !this.systemInstruction.isBlank()) {
            snapshot.add(SystemMessage.from(this.systemInstruction));
        }
        return List.copyOf(snapshot);
    }

    List<ChatMessage> withInitialSystemMessages(List<ChatMessage> messages) {
        List<ChatMessage> history = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        boolean hasSystem = false;
        for (ChatMessage message : history) {
            if (message instanceof SystemMessage) {
                hasSystem = true;
                break;
            }
        }
        if (!hasSystem && !this.initialSystemMessages.isEmpty()) {
            List<ChatMessage> merged = new ArrayList<>(this.initialSystemMessages.size() + history.size());
            merged.addAll(this.initialSystemMessages);
            merged.addAll(history);
            return merged;
        }
        return history;
    }

    ChatMessage toChatMessage(AgentMessage msg) {
        if (msg == null) {
            return null;
        }
        switch (msg.role()) {
            case "system":
                return SystemMessage.from(msg.text());
            case "user":
                return UserMessage.from(msg.text());
            case "assistant":
            case "ai":
                if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                    List<dev.langchain4j.agent.tool.ToolExecutionRequest> requests = new ArrayList<>();
                    for (ToolCall tc : msg.toolCalls()) {
                        requests.add(dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                                .id(tc.id())
                                .name(tc.name())
                                .arguments(tc.argumentsJson())
                                .build());
                    }
                    if (msg.text() != null && !msg.text().isEmpty()) {
                        return new AiMessage(msg.text(), requests);
                    } else {
                        return AiMessage.from(requests);
                    }
                }
                return AiMessage.from(msg.text());
            case "tool":
                return ToolExecutionResultMessage.from(msg.toolId(), msg.toolName(), msg.text());
            default:
                throw new IllegalArgumentException("Unknown AgentMessage role: " + msg.role());
        }
    }

    AgentMessage toAgentMessage(ChatMessage msg) {
        if (msg == null) {
            return null;
        }
        if (msg instanceof SystemMessage) {
            return AgentMessage.system(msg.text());
        } else if (msg instanceof UserMessage) {
            return AgentMessage.user(msg.text());
        } else if (msg instanceof AiMessage aiMsg) {
            if (aiMsg.hasToolExecutionRequests()) {
                List<ToolCall> toolCalls = new ArrayList<>();
                for (dev.langchain4j.agent.tool.ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
                    try {
                        toolCalls.add(new ToolCall(
                                req.id(),
                                req.name(),
                                parseJsonToMapStrict(req.arguments()),
                                req.arguments(),
                                null
                        ));
                    } catch (Exception e) {
                        if (this.toolCallPolicy == ToolCallPolicy.TEXT_FALLBACK) {
                            return AgentMessage.assistant("Tool Call Fallback: " + req.id() + " failed to parse json. " + e.getMessage());
                        } else if (this.toolCallPolicy == ToolCallPolicy.STRICT) {
                            throw new RuntimeException("Failed to parse tool call arguments under STRICT policy: " + req.arguments(), e);
                        } else {
                            // LENIENT
                            log.warn("LENIENT: Failed to parse tool call JSON", e);
                            toolCalls.add(new ToolCall(
                                    req.id(),
                                    req.name(),
                                    Collections.emptyMap(),
                                    req.arguments(),
                                    null
                            ));
                        }
                    }
                }
                return AgentMessage.assistant(aiMsg.text(), toolCalls);
            }
            return AgentMessage.assistant(aiMsg.text());
        } else if (msg instanceof ToolExecutionResultMessage toolMsg) {
            return AgentMessage.tool(toolMsg.id(), toolMsg.toolName(), toolMsg.text());
        } else {
            throw new IllegalArgumentException("Unknown ChatMessage type: " + msg.getClass().getName());
        }
    }

    private Map<String, Object> parseJsonToMapStrict(String json) throws Exception {
        if (json == null || json.isEmpty()) {
            return Collections.emptyMap();
        }
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    public static class Builder {
        private String binaryPath;
        private String storageDirectory;
        private String modelName = "gemini-3.5-flash";
        private String thinkingLevel = "high";
        private String systemInstruction;
        private final List<Object> toolObjects = new ArrayList<>();
        private Object delegateModel;
        private AgentRuntime customRuntime;
        private ThinkingMode thinkingMode = ThinkingMode.NONE;
        private ToolCallPolicy toolCallPolicy = ToolCallPolicy.LENIENT;
        private CompactionStrategy<ChatMessage> compactionStrategy;
        private AgentMemory memory;
        private Integer compactionMaxMessages;
        private final List<CustomToolExecutor> customToolExecutors = new ArrayList<>();
        private Integer compactionMessageThreshold;
        private Integer compactionTokenThreshold;

        // v0.2.0 Streamlined API options
        private String runtimeType;
        private Boolean enableThinkingExtraction;
        private org.flexagent.core.strategy.AgentStrategy strategy;

        public Builder strategy(org.flexagent.core.strategy.AgentStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder memory(AgentMemory memory) {
            this.memory = memory;
            return this;
        }

        public Builder customToolExecutor(CustomToolExecutor executor) {
            if (executor != null) {
                this.customToolExecutors.add(executor);
            }
            return this;
        }

        public Builder binaryPath(String binaryPath) {
            this.binaryPath = binaryPath;
            return this;
        }

        public Builder storageDirectory(String storageDirectory) {
            this.storageDirectory = storageDirectory;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder thinkingLevel(String thinkingLevel) {
            this.thinkingLevel = thinkingLevel;
            return this;
        }

        public Builder systemInstruction(String systemInstruction) {
            this.systemInstruction = systemInstruction;
            return this;
        }

        // Legacy compatibility
        public Builder addToolObject(Object toolObject) {
            if (toolObject != null) {
                this.toolObjects.add(toolObject);
            }
            return this;
        }

        // v0.2.0 simplified tools builder
        public Builder tools(Object... tools) {
            if (tools != null) {
                for (Object tool : tools) {
                    if (tool != null) {
                        this.toolObjects.add(tool);
                    }
                }
            }
            return this;
        }

        public Builder toolObjects(List<Object> toolObjects) {
            if (toolObjects != null) {
                this.toolObjects.addAll(toolObjects);
            }
            return this;
        }

        // Legacy compatibility
        public Builder delegateModel(Object delegateModel) {
            this.delegateModel = delegateModel;
            return this;
        }

        // v0.2.0 simplified model injector
        public Builder model(Object model) {
            this.delegateModel = model;
            return this;
        }

        public Builder customRuntime(AgentRuntime customRuntime) {
            this.customRuntime = customRuntime;
            return this;
        }

        public Builder thinkingMode(ThinkingMode thinkingMode) {
            this.thinkingMode = thinkingMode;
            return this;
        }

        // v0.2.0 simplified runtime selection
        public Builder runtime(String runtimeType) {
            this.runtimeType = runtimeType;
            return this;
        }

        public Builder langChain4j(Object model) {
            this.runtimeType = RuntimeTypes.LANGCHAIN4J;
            this.delegateModel = model;
            return this;
        }

        public Builder localHarness(String binaryPath, String storageDirectory) {
            this.runtimeType = RuntimeTypes.LOCAL_HARNESS;
            this.binaryPath = binaryPath;
            this.storageDirectory = storageDirectory;
            return this;
        }

        // v0.2.0 simplified thinking extractor toggle
        public Builder enableThinkingExtraction(boolean enable) {
            this.enableThinkingExtraction = enable;
            return this;
        }

        public Builder toolCallPolicy(ToolCallPolicy toolCallPolicy) {
            this.toolCallPolicy = toolCallPolicy;
            return this;
        }

        public Builder strictToolCalls() {
            this.toolCallPolicy = ToolCallPolicy.STRICT;
            return this;
        }

        public Builder lenientToolCalls() {
            this.toolCallPolicy = ToolCallPolicy.LENIENT;
            return this;
        }

        public Builder textFallbackToolCalls() {
            this.toolCallPolicy = ToolCallPolicy.TEXT_FALLBACK;
            return this;
        }

        public Builder compactionStrategy(CompactionStrategy<ChatMessage> compactionStrategy) {
            this.compactionStrategy = compactionStrategy;
            return this;
        }

        public Builder compactionMaxMessages(int maxMessages) {
            this.compactionMaxMessages = maxMessages;
            return this;
        }

        public Builder compactionMessageThreshold(int messageThreshold) {
            this.compactionMessageThreshold = messageThreshold;
            return this;
        }

        public Builder compactionTokenThreshold(int tokenThreshold) {
            this.compactionTokenThreshold = tokenThreshold;
            return this;
        }

        public FlexAgentChatModel build() {
            validate();

            if (this.compactionStrategy == null
                    && (this.compactionMaxMessages != null
                    || this.compactionMessageThreshold != null
                    || this.compactionTokenThreshold != null)) {
                int maxMessages = this.compactionMaxMessages != null ? this.compactionMaxMessages : 12;
                Integer messageThreshold = this.compactionMessageThreshold != null
                        ? this.compactionMessageThreshold
                        : this.compactionMaxMessages;
                this.compactionStrategy = new SlidingWindowCompactionStrategy(
                        maxMessages,
                        messageThreshold,
                        this.compactionTokenThreshold
                );
            }

            // Apply thinking extraction toggle
            if (this.enableThinkingExtraction != null) {
                this.thinkingMode = this.enableThinkingExtraction ? ThinkingMode.XML_THINK_TAG : ThinkingMode.NONE;
            } else {
                // Auto-detect reasoning model
                boolean isReasoning = false;
                if (this.modelName != null && (this.modelName.contains("reasoner") || this.modelName.contains("r1"))) {
                    isReasoning = true;
                } else if (this.delegateModel != null) {
                    try {
                        String detectedModel = null;
                        try {
                            java.lang.reflect.Method m = delegateModel.getClass().getMethod("modelName");
                            detectedModel = (String) m.invoke(delegateModel);
                        } catch (Exception ignored) {
                            try {
                                java.lang.reflect.Method m = delegateModel.getClass().getMethod("getModelName");
                                detectedModel = (String) m.invoke(delegateModel);
                            } catch (Exception ignored2) {}
                        }
                        if (detectedModel != null && (detectedModel.contains("reasoner") || detectedModel.contains("r1"))) {
                            isReasoning = true;
                        }
                    } catch (Exception ignored) {}
                }

                if (isReasoning) {
                    this.thinkingMode = ThinkingMode.XML_THINK_TAG;
                    log.info("Auto-enabled thinking extraction for detected reasoning model.");
                }
            }

            return new FlexAgentChatModel(this);
        }

        private void validate() {
            String selectedRuntime = this.runtimeType;
            if (selectedRuntime == null || selectedRuntime.isBlank()) {
                selectedRuntime = (this.binaryPath != null && !this.binaryPath.isBlank())
                        ? RuntimeTypes.LOCAL_HARNESS
                        : RuntimeTypes.LANGCHAIN4J;
            }

            if (this.customRuntime == null
                    && RuntimeTypes.LANGCHAIN4J.equals(selectedRuntime)
                    && this.delegateModel == null) {
                throw new RuntimeInitializationException(
                        selectedRuntime,
                        "delegate model is required. Fix: call FlexAgentChatModel.builder().langChain4j(model) or .model(model)."
                );
            }

            if (this.customRuntime == null
                    && RuntimeTypes.LOCAL_HARNESS.equals(selectedRuntime)
                    && (this.binaryPath == null || this.binaryPath.isBlank())) {
                throw new RuntimeInitializationException(
                        selectedRuntime,
                        "binary path is required. Fix: call .localHarness(binaryPath, storageDirectory) or .binaryPath(path)."
                );
            }
        }
    }
}
