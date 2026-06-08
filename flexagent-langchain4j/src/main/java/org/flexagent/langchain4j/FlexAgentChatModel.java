package org.flexagent.langchain4j;

import org.flexagent.core.FlexAgentClient;
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
import org.flexagent.core.memory.compaction.SlidingWindowCompactionStrategy;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.flexagent.core.memory.AgentMemory;
import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.memory.AgentSessionContext;
import org.flexagent.core.tool.CustomToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class FlexAgentChatModel implements ChatLanguageModel, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(FlexAgentChatModel.class);

    private final FlexAgentClient coreClient;
    private final ToolCallPolicy toolCallPolicy;
    final AgentRuntime activeRuntime;
    private final List<Object> toolObjects;
    final AgentMemory memory;
    final List<CustomToolExecutor> customToolExecutors;
    final ToolAdapter toolAdapter;
    final List<ChatMessage> initialSystemMessages;

    private FlexAgentChatModel(Builder builder) {
        this.toolCallPolicy = builder.toolCallPolicy != null ? builder.toolCallPolicy : ToolCallPolicy.LENIENT;
        this.toolObjects = new ArrayList<>(builder.toolObjects);
        this.memory = builder.memory;
        this.customToolExecutors = new ArrayList<>(builder.customToolExecutors);
        
        try {
            this.toolAdapter = new ToolAdapter(this.toolObjects);
            this.activeRuntime = initRuntime(builder);
            this.initialSystemMessages = snapshotInitialSystemMessages(builder.systemInstruction);
            
            List<AgentMessage> initialAgentMessages = new ArrayList<>();
            for (ChatMessage cm : this.initialSystemMessages) {
                initialAgentMessages.add(MessageConverter.toAgentMessage(cm, this.toolCallPolicy));
            }

            this.coreClient = FlexAgentClient.builder()
                    .activeRuntime(this.activeRuntime)
                    .strategy(builder.strategy != null ? builder.strategy : new org.flexagent.core.strategy.ReActStrategy())
                    .memory(builder.memory)
                    .initialSystemMessages(initialAgentMessages)
                    .toolExecutor(toolCall -> {
                        log.info("Executing custom Tool: {} (args: {})", toolCall.name(), toolCall.argumentsJson());
                        ToolResult toolResult = null;
                        for (CustomToolExecutor executor : builder.customToolExecutors) {
                            if (executor.supports(toolCall.name())) {
                                toolResult = executor.execute(toolCall);
                                break;
                            }
                        }
                        if (toolResult == null) {
                            toolResult = toolAdapter.execute(toolCall);
                        }
                        return toolResult;
                    })
                    .build();
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

    private AgentRuntime initRuntime(Builder builder) {
        AgentRuntime runtime = builder.customRuntime;
        if (runtime == null) {
            String type = builder.runtimeType;
            if (type == null || type.isEmpty()) {
                type = (builder.binaryPath != null && !builder.binaryPath.isEmpty()) ? RuntimeTypes.LOCAL_HARNESS : RuntimeTypes.LANGCHAIN4J;
            }

            if (builder.binaryPath == null && builder.delegateModel == null && RuntimeTypes.LANGCHAIN4J.equals(type)) {
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
            
            org.flexagent.core.runtime.AgentRuntimeProvider provider = matchedProviders.get(0);
            log.info("FlexAgent runtime loaded via SPI: {}", finalType);
            
            AgentRuntimeConfig runtimeConfig = new AgentRuntimeConfig(finalType, builder.delegateModel, null);
            runtime = provider.create(runtimeConfig);
        }

        // Prepare configuration
        AgentConfig config = new AgentConfig();
        config.setBinaryPath(builder.binaryPath);
        config.setStorageDirectory(builder.storageDirectory);
        config.setModelName(builder.modelName);
        config.setThinkingLevel(builder.thinkingLevel);
        config.setSystemInstruction(builder.systemInstruction);
        config.setThinkingMode(builder.thinkingMode != null ? builder.thinkingMode : ThinkingMode.NONE);
        config.setToolCallPolicy(this.toolCallPolicy);
        if (this.toolObjects != null) {
            for (Object tool : this.toolObjects) {
                config.addToolObject(tool);
            }
        }
        if (this.toolAdapter != null) {
            for (org.flexagent.core.model.ToolDefinition toolDef : this.toolAdapter.getTools()) {
                config.addTool(toolDef);
            }
        }

        try {
            runtime.initialize(config);
        } catch (IOException e) {
            throw new RuntimeInitializationException(builder.runtimeType, "Initialize failed", e);
        }

        // Apply compaction strategy if LangChain4jRuntime is used
        if (runtime instanceof LangChain4jRuntime lc4jRuntime) {
            if (builder.compactionStrategy != null) {
                lc4jRuntime.setCompactionStrategy(builder.compactionStrategy);
            }
        }

        return runtime;
    }

    private List<ChatMessage> snapshotInitialSystemMessages(String systemInstruction) {
        List<ChatMessage> snapshot = new ArrayList<>();
        if (this.activeRuntime instanceof LangChain4jRuntime lc4jRuntime) {
            List<ChatMessage> runtimeHistory = lc4jRuntime.getChatMessages();
            if (runtimeHistory != null) {
                for (ChatMessage message : runtimeHistory) {
                    if (message instanceof SystemMessage) {
                        snapshot.add(message);
                    }
                }
            }
        } else if (systemInstruction != null && !systemInstruction.isBlank()) {
            snapshot.add(SystemMessage.from(systemInstruction));
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

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        List<AgentMessage> agentMessages = new ArrayList<>();
        for (ChatMessage cm : messages) {
            agentMessages.add(MessageConverter.toAgentMessage(cm, this.toolCallPolicy));
        }
        
        AgentMessage responseMsg = this.coreClient.generate(agentMessages);
        ChatMessage chatMsg = MessageConverter.toChatMessage(responseMsg);
        if (chatMsg instanceof AiMessage ai) {
            return Response.from(ai);
        } else {
            return Response.from(AiMessage.from(chatMsg.text()));
        }
    }

    public Response<AiMessage> generate(String sessionId, String userMessage) {
        AgentSessionContext.set(sessionId);
        try {
            AgentMessage msg = this.coreClient.generate(userMessage);
            ChatMessage chatMsg = MessageConverter.toChatMessage(msg);
            if (chatMsg instanceof AiMessage ai) {
                return Response.from(ai);
            } else {
                return Response.from(AiMessage.from(chatMsg.text()));
            }
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

    public Flux<String> stream(List<ChatMessage> messages) {
        List<AgentMessage> agentMessages = new ArrayList<>();
        for (ChatMessage cm : messages) {
            agentMessages.add(MessageConverter.toAgentMessage(cm, this.toolCallPolicy));
        }
        return this.coreClient.stream(agentMessages);
    }

    public Flux<String> stream(String sessionId, String userMessage) {
        return this.coreClient.stream(sessionId, userMessage);
    }

    public Flux<String> stream(String sessionId, List<ChatMessage> messages) {
        AgentSessionContext.set(sessionId);
        try {
            return stream(messages);
        } finally {
            AgentSessionContext.clear();
        }
    }

    @Override
    public void close() throws Exception {
        if (this.coreClient != null) {
            this.coreClient.close();
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

    public FlexAgentClient getCoreClient() {
        return this.coreClient;
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
        private CompactionStrategy<AgentMessage> compactionStrategy;
        private AgentMemory memory;
        private Integer compactionMaxMessages;
        private final List<CustomToolExecutor> customToolExecutors = new ArrayList<>();
        private Integer compactionMessageThreshold;
        private Integer compactionTokenThreshold;

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

        public Builder addToolObject(Object toolObject) {
            if (toolObject != null) {
                this.toolObjects.add(toolObject);
            }
            return this;
        }

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

        public Builder delegateModel(Object delegateModel) {
            this.delegateModel = delegateModel;
            return this;
        }

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

        public Builder compactionStrategy(CompactionStrategy<AgentMessage> compactionStrategy) {
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

            if (this.enableThinkingExtraction != null) {
                this.thinkingMode = this.enableThinkingExtraction ? ThinkingMode.XML_THINK_TAG : ThinkingMode.NONE;
            } else {
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
                }
            }

            String selectedRuntime = this.runtimeType;
            if (selectedRuntime == null || selectedRuntime.isBlank()) {
                selectedRuntime = (this.binaryPath != null && !this.binaryPath.isBlank())
                        ? RuntimeTypes.LOCAL_HARNESS
                        : RuntimeTypes.LANGCHAIN4J;
            }
            this.runtimeType = selectedRuntime;

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

            return new FlexAgentChatModel(this);
        }
    }
}
