package org.flexagent.langchain4j;

import org.flexagent.core.model.Step;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.model.ThinkingMode;
import org.flexagent.core.model.ToolCallPolicy;
import org.flexagent.core.runtime.AgentConfig;
import org.flexagent.core.runtime.AgentRuntime;
import org.flexagent.core.runtime.AgentRuntimeConfig;
import org.flexagent.core.runtime.RuntimeTypes;
import org.flexagent.langchain4j.compaction.CompactionStrategy;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FlexAgentChatModel implements ChatLanguageModel {
    private static final Logger log = LoggerFactory.getLogger(FlexAgentChatModel.class);

    private final String binaryPath;
    private final String storageDirectory;
    private final String modelName;
    private final String thinkingLevel;
    private final String systemInstruction;
    private final List<Object> toolObjects;
    private final ChatLanguageModel delegateModel;
    private final AgentRuntime customRuntime;
    private final ThinkingMode thinkingMode;
    private final ToolCallPolicy toolCallPolicy;
    private final CompactionStrategy compactionStrategy;

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
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        String prompt = "";
        String systemInstructionToUse = this.systemInstruction;

        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage sysMsg) {
                systemInstructionToUse = sysMsg.text();
            } else if (message instanceof UserMessage userMsg) {
                prompt = userMsg.text();
            }
        }

        log.info("Generating response for prompt length: {}", prompt.length());

        // 1. Determine Runtime Backend
        AgentRuntime runtime = this.customRuntime;
        if (runtime == null) {
            String type = (this.binaryPath != null && !this.binaryPath.isEmpty()) ? RuntimeTypes.LOCAL_HARNESS : RuntimeTypes.LANGCHAIN4J;
            if (this.binaryPath == null && this.delegateModel == null) {
                throw new IllegalStateException("Either binaryPath (for LocalHarness) or delegateModel (for LangChain4j) must be specified.");
            }
            ServiceLoader<org.flexagent.core.runtime.AgentRuntimeProvider> loader = 
                    ServiceLoader.load(org.flexagent.core.runtime.AgentRuntimeProvider.class);
            
            List<org.flexagent.core.runtime.AgentRuntimeProvider> matchedProviders = loader.stream()
                    .map(ServiceLoader.Provider::get)
                    .filter(provider -> provider.supports(type))
                    .toList();
            
            if (matchedProviders.isEmpty()) {
                throw new IllegalStateException("No AgentRuntimeProvider found for type: " + type +
                        ". Make sure the corresponding module (flexagent-localharness or flexagent-langchain4j) is on the classpath.");
            }
            if (matchedProviders.size() > 1) {
                throw new IllegalStateException("Multiple AgentRuntimeProviders found for type: " + type +
                        ". Conflict detected between provider implementations.");
            }
            
            org.flexagent.core.runtime.AgentRuntimeProvider provider = matchedProviders.get(0);
            log.info("FlexAgent runtime loaded: {}", type);
            log.info("Provider: {}", provider.getClass().getName());
            
            AgentRuntimeConfig runtimeConfig = new AgentRuntimeConfig(type, this.delegateModel, null);
            runtime = provider.create(runtimeConfig);
        }

        // Apply compaction strategy and history context if LangChain4jRuntime is used
        if (runtime instanceof LangChain4jRuntime lc4jRuntime) {
            if (this.compactionStrategy != null) {
                lc4jRuntime.setCompactionStrategy(this.compactionStrategy);
            }
            if (messages != null && messages.size() > 1) {
                lc4jRuntime.setHistoryMessages(messages.subList(0, messages.size() - 1));
            }
        }

        // 2. Prepare AgentConfig
        AgentConfig config = new AgentConfig();
        config.setBinaryPath(this.binaryPath);
        config.setStorageDirectory(this.storageDirectory);
        config.setModelName(this.modelName);
        config.setThinkingLevel(this.thinkingLevel);
        config.setSystemInstruction(systemInstructionToUse);
        config.setThinkingMode(this.thinkingMode != null ? this.thinkingMode : ThinkingMode.NONE);
        config.setToolCallPolicy(this.toolCallPolicy != null ? this.toolCallPolicy : ToolCallPolicy.LENIENT);
        if (this.toolObjects != null) {
            for (Object tool : this.toolObjects) {
                config.addToolObject(tool);
            }
        }

        try (AgentRuntime activeRuntime = runtime) {
            // Initialize runtime
            activeRuntime.initialize(config);

            // Set up local tool executor from the initialized config
            ToolAdapter toolAdapter = new ToolAdapter(this.toolObjects);

            activeRuntime.send(prompt);

            // Wait for trajectory to complete in virtual thread
            CompletableFuture<Void> waitFuture = CompletableFuture.runAsync(
                    activeRuntime::waitForIdle,
                    Executors.newVirtualThreadPerTaskExecutor()
            );

            StringBuilder contentBuilder = new StringBuilder();

            while (true) {
                Step step = activeRuntime.pollStep(100, TimeUnit.MILLISECONDS);
                if (step == null) {
                    if (waitFuture.isDone()) {
                        step = activeRuntime.pollStep(10, TimeUnit.MILLISECONDS);
                        if (step == null) {
                            break;
                        }
                    }
                }

                if (step != null) {
                    if (step.status() == org.flexagent.core.model.StepStatus.ERROR) {
                        throw new RuntimeException("Runtime execution error: " + step.error());
                    }
                    // Check for tool call requests from the model
                    if (step.type() == org.flexagent.core.model.StepType.TOOL_CALL && !step.toolCalls().isEmpty()) {
                        for (org.flexagent.core.model.ToolCall toolCall : step.toolCalls()) {
                            log.info("Executing custom Tool: {} (args: {})", toolCall.name(), toolCall.argumentsJson());
                            ToolResult toolResult = toolAdapter.execute(toolCall);
                            log.info("Tool Result: {}", toolResult);
                            activeRuntime.sendToolResult(toolResult);
                        }
                    }

                    if (step.contentDelta() != null && !step.contentDelta().isEmpty()) {
                        contentBuilder.append(step.contentDelta());
                    }
                    if (step.thinkingDelta() != null && !step.thinkingDelta().isEmpty()) {
                        log.info("[Thinking] {}", step.thinkingDelta().trim());
                    }
                }
            }

            AiMessage aiMessage = AiMessage.from(contentBuilder.toString());
            return Response.from(aiMessage);

        } catch (Exception e) {
            log.error("Error executing FlexAgent Agent Runtime", e);
            throw new RuntimeException("FlexAgent agent execution failed", e);
        }
    }

    public static class Builder {
        private String binaryPath;
        private String storageDirectory;
        private String modelName = "gemini-3.5-flash";
        private String thinkingLevel = "high";
        private String systemInstruction;
        private final List<Object> toolObjects = new ArrayList<>();
        private ChatLanguageModel delegateModel;
        private AgentRuntime customRuntime;
        private ThinkingMode thinkingMode = ThinkingMode.NONE;
        private ToolCallPolicy toolCallPolicy = ToolCallPolicy.LENIENT;
        private CompactionStrategy compactionStrategy;

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

        public Builder toolObjects(List<Object> toolObjects) {
            if (toolObjects != null) {
                this.toolObjects.addAll(toolObjects);
            }
            return this;
        }

        public Builder delegateModel(ChatLanguageModel delegateModel) {
            this.delegateModel = delegateModel;
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

        public Builder toolCallPolicy(ToolCallPolicy toolCallPolicy) {
            this.toolCallPolicy = toolCallPolicy;
            return this;
        }

        public Builder compactionStrategy(CompactionStrategy compactionStrategy) {
            this.compactionStrategy = compactionStrategy;
            return this;
        }

        public FlexAgentChatModel build() {
            return new FlexAgentChatModel(this);
        }
    }
}
