package org.flexagent.springai;

import org.flexagent.core.exception.ToolCallParsingException;
import org.flexagent.core.model.*;
import org.flexagent.core.runtime.AgentConfig;
import org.flexagent.core.runtime.AgentRuntime;
import org.flexagent.core.runtime.XmlThinkTagExtractor;
import org.flexagent.core.memory.compaction.CompactionStrategy;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

public class SpringAiRuntime implements AgentRuntime {
    private static final Logger log = LoggerFactory.getLogger(SpringAiRuntime.class);

    private final ChatModel chatModel;
    private final List<Message> chatMessages = new CopyOnWriteArrayList<>();
    private final BlockingQueue<Step> stepQueue = new LinkedBlockingQueue<>();
    private static final ObjectMapper mapper = new ObjectMapper();

    private AgentConfig config;
    private CompactionStrategy<Message> compactionStrategy;
    private volatile CompletableFuture<Void> idleFuture = CompletableFuture.completedFuture(null);
    private int stepIndex = 0;

    // For handling tool calls manually from FlexAgent side if needed
    private volatile CompletableFuture<String> toolResponseLatch;
    private volatile ToolResult currentToolResult;

    public SpringAiRuntime(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public void setCompactionStrategy(CompactionStrategy<Message> compactionStrategy) {
        this.compactionStrategy = compactionStrategy;
    }

    @Override
    public Set<RuntimeCapability> capabilities() {
        return Set.of(
                RuntimeCapability.TOOL_CALLING,
                RuntimeCapability.THINKING_DELTA,
                RuntimeCapability.COMPACTION
        );
    }

    @Override
    public ThinkingMode thinkingMode() {
        return this.config != null ? this.config.getThinkingMode() : ThinkingMode.NONE;
    }

    @Override
    public ToolCallPolicy toolCallPolicy() {
        return this.config != null ? this.config.getToolCallPolicy() : ToolCallPolicy.LENIENT;
    }

    @Override
    public void initialize(AgentConfig config) throws IOException {
        this.config = config;

        if (chatMessages.isEmpty() && config.getSystemInstruction() != null && !config.getSystemInstruction().isEmpty()) {
            chatMessages.add(new SystemMessage(config.getSystemInstruction()));
        }
        this.idleFuture = CompletableFuture.completedFuture(null);
    }

    @Override
    public void send(String prompt) throws IOException {
        chatMessages.add(new UserMessage(prompt));
        this.idleFuture = new CompletableFuture<>();
        Thread.ofVirtual().name("spring-ai-agent-loop").start(this::runAgentLoop);
    }

    private void runAgentLoop() {
        boolean isStreaming = false;
        try {
            List<Message> messagesToSend = chatMessages;
            if (compactionStrategy != null && compactionStrategy.shouldCompact(chatMessages)) {
                messagesToSend = compactionStrategy.compact(chatMessages);
            }

            // Create prompt
            Prompt prompt;
            if (!config.getTools().isEmpty()) {
                // In Spring AI, we wrap tools into FunctionCallbacks.
                // For simplicity of MVP, we use the model's auto-function-calling if possible,
                // OR we can just register blocking callbacks.
                List<FunctionCallback> callbacks = new ArrayList<>();
                for (ToolDefinition td : config.getTools()) {
                    callbacks.add(new FlexAgentFunctionCallback(td));
                }

                ChatOptions options = (ChatOptions) FunctionCallingOptions.builder()
                        .withFunctionCallbacks(callbacks)
                        .build();
                prompt = new Prompt(messagesToSend, options);
            } else {
                prompt = new Prompt(messagesToSend);
            }

            if (chatModel instanceof org.springframework.ai.chat.model.StreamingChatModel streamingChatModel && streamingChatModel.stream(prompt) != null) {
                reactor.core.publisher.Flux<ChatResponse> flux = streamingChatModel.stream(prompt);
                isStreaming = true;
                
                StringBuilder fullTextBuilder = new StringBuilder();
                
                flux.doOnNext(chunk -> {
                    if (chunk.getResult() != null && chunk.getResult().getOutput() != null) {
                        String content = chunk.getResult().getOutput().getContent();
                        if (content != null && !content.isEmpty()) {
                            fullTextBuilder.append(content);
                            try {
                                Step tokenStep = new Step(
                                        "trajectory-springai-token:" + stepIndex,
                                        stepIndex,
                                        StepType.STREAM_TOKEN,
                                        StepSource.MODEL,
                                        StepTarget.USER,
                                        StepStatus.ACTIVE,
                                        content, content, "", "",
                                        Collections.emptyList(), null, false, null, null
                                );
                                stepQueue.put(tokenStep);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        
                        if (chunk.getMetadata() != null && chunk.getMetadata().getUsage() != null) {
                            org.springframework.ai.chat.metadata.Usage usage = chunk.getMetadata().getUsage();
                            org.flexagent.core.runtime.FlexAgentObservationUtils.recordTokenUsage(
                                    "spring-ai",
                                    usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0,
                                    usage.getGenerationTokens() != null ? usage.getGenerationTokens().intValue() : 0
                            );
                            
                            if (config != null && config.getUsageTracker() != null) {
                                config.getUsageTracker().recordUsage(
                                        config.getSessionId(),
                                        config.getModelName(),
                                        usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0,
                                        usage.getGenerationTokens() != null ? usage.getGenerationTokens().intValue() : 0
                                );
                            }
                        }
                    }
                }).doOnComplete(() -> {
                    String rawText = fullTextBuilder.toString();
                    String thinking = "";
                    String text = rawText;

                    if (!rawText.isEmpty()) {
                        XmlThinkTagExtractor extractor = new XmlThinkTagExtractor();
                        List<AgentEvent> events = extractor.extract(rawText);
                        StringBuilder tBuilder = new StringBuilder();
                        StringBuilder txtBuilder = new StringBuilder();
                        for (AgentEvent event : events) {
                            if (event instanceof ThinkingDelta td) {
                                tBuilder.append(td.text());
                            } else if (event instanceof TextDelta td) {
                                txtBuilder.append(td.text());
                            }
                        }
                        thinking = tBuilder.toString();
                        text = txtBuilder.toString();
                    }
                    
                    chatMessages.add(new AssistantMessage(rawText));

                    Step responseStep = new Step(
                            "trajectory-springai:" + stepIndex,
                            stepIndex++,
                            StepType.TEXT_RESPONSE,
                            StepSource.MODEL,
                            StepTarget.USER,
                            StepStatus.DONE,
                            text, text, thinking, thinking,
                            Collections.emptyList(), null, true, null, null
                    );
                    try {
                        stepQueue.put(responseStep);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    idleFuture.complete(null);
                }).doOnError(e -> {
                    log.error("Spring AI loop error (streaming)", e);
                    try {
                        stepQueue.put(new Step("error", -1, StepType.UNKNOWN, StepSource.MODEL, StepTarget.USER, StepStatus.ERROR, "", "", "", "", Collections.emptyList(), e.getMessage(), true, null, null));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    idleFuture.complete(null);
                }).subscribe();
            } else {
                ChatResponse response = chatModel.call(prompt);
                
                if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                    org.springframework.ai.chat.metadata.Usage usage = response.getMetadata().getUsage();
                    org.flexagent.core.runtime.FlexAgentObservationUtils.recordTokenUsage(
                            "spring-ai",
                            usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0,
                            usage.getGenerationTokens() != null ? usage.getGenerationTokens().intValue() : 0
                    );
                    
                    if (config != null && config.getUsageTracker() != null) {
                        config.getUsageTracker().recordUsage(
                                config.getSessionId(),
                                config.getModelName(),
                                usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0,
                                usage.getGenerationTokens() != null ? usage.getGenerationTokens().intValue() : 0
                        );
                    }
                }

                AssistantMessage assistantMessage = response.getResult().getOutput();
                chatMessages.add(assistantMessage);

                String rawText = assistantMessage.getContent();
                String thinking = "";
                String text = rawText != null ? rawText : "";

                if (rawText != null && !rawText.isEmpty()) {
                    XmlThinkTagExtractor extractor = new XmlThinkTagExtractor();
                    List<AgentEvent> events = extractor.extract(rawText);
                    StringBuilder thinkingBuilder = new StringBuilder();
                    StringBuilder textBuilder = new StringBuilder();
                    for (AgentEvent event : events) {
                        if (event instanceof ThinkingDelta td) {
                            thinkingBuilder.append(td.text());
                        } else if (event instanceof TextDelta td) {
                            textBuilder.append(td.text());
                        }
                    }
                    thinking = thinkingBuilder.toString();
                    text = textBuilder.toString();
                }

                Step responseStep = new Step(
                        "trajectory-springai:" + stepIndex,
                        stepIndex++,
                        StepType.TEXT_RESPONSE,
                        StepSource.MODEL,
                        StepTarget.USER,
                        StepStatus.DONE,
                        text, text, thinking, thinking,
                        Collections.emptyList(), null, true, null, null
                );
                stepQueue.put(responseStep);
            }

        } catch (Exception e) {
            log.error("Spring AI loop error", e);
            try {
                stepQueue.put(new Step("error", -1, StepType.UNKNOWN, StepSource.MODEL, StepTarget.USER, StepStatus.ERROR, "", "", "", "", Collections.emptyList(), e.getMessage(), true, null, null));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        } finally {
            if (!isStreaming) {
                idleFuture.complete(null);
            }
        }
    }

    @Override
    public Step pollStep(long timeout, TimeUnit unit) throws InterruptedException {
        return stepQueue.poll(timeout, unit);
    }

    @Override
    public void sendToolResult(ToolResult result) throws IOException {
        if (toolResponseLatch != null) {
            this.currentToolResult = result;
            String content = result.result() != null ? result.result().toString() : (result.error() != null ? result.error() : "{}");
            toolResponseLatch.complete(content);
        }
    }

    @Override
    public void waitForIdle() {
        try {
            idleFuture.get();
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void close() throws Exception {
    }

    /**
     * Maps FlexAgent Tool to Spring AI FunctionCallback.
     * When Spring AI decides to call the function, this intercepts it,
     * emits a TOOL_CALL step to FlexAgent, and blocks until the user provides the result.
     */
    private class FlexAgentFunctionCallback implements FunctionCallback {
        private final ToolDefinition toolDef;

        public FlexAgentFunctionCallback(ToolDefinition toolDef) {
            this.toolDef = toolDef;
        }

        @Override
        public String getName() {
            return toolDef.name();
        }

        @Override
        public String getDescription() {
            return toolDef.description();
        }

        @Override
        public String getInputTypeSchema() {
            return toolDef.parametersJsonSchema();
        }

        @Override
        public String call(String functionArguments) {
            String callId = UUID.randomUUID().toString();
            Map<String, Object> args;
            try {
                args = mapper.readValue(functionArguments, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.error("Failed to parse tool call arguments: {}", functionArguments, e);
                args = Collections.emptyMap();
            }

            ToolCall toolCall = new ToolCall(callId, getName(), args, functionArguments, null);

            try {
                toolResponseLatch = new CompletableFuture<>();
                Step toolCallStep = new Step(
                        "trajectory-springai-tool:" + stepIndex,
                        stepIndex++,
                        StepType.TOOL_CALL,
                        StepSource.MODEL,
                        StepTarget.ENVIRONMENT,
                        StepStatus.ACTIVE,
                        "", "", "", "",
                        List.of(toolCall), null, false, null, null
                );
                stepQueue.put(toolCallStep);

                // Block until sendToolResult completes the latch
                return toolResponseLatch.get();

            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }
    }
}
