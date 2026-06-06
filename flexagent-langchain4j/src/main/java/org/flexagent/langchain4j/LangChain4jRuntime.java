package org.flexagent.langchain4j;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flexagent.core.exception.ToolCallParsingException;
import org.flexagent.core.model.*;
import org.flexagent.core.runtime.AgentConfig;
import org.flexagent.core.runtime.AgentRuntime;
import org.flexagent.core.runtime.XmlThinkTagExtractor;
import org.flexagent.core.memory.compaction.CompactionStrategy;
import org.flexagent.langchain4j.compaction.NoopCompactionStrategy;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public class LangChain4jRuntime implements AgentRuntime {
    private static final Logger log = LoggerFactory.getLogger(LangChain4jRuntime.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatLanguageModel model;
    private final List<ChatMessage> chatMessages = new CopyOnWriteArrayList<>();
    private final BlockingQueue<Step> stepQueue = new LinkedBlockingQueue<>();
    
    private AgentConfig config;
    private ToolAdapter toolAdapter;
    private CompactionStrategy<ChatMessage> compactionStrategy = new NoopCompactionStrategy();
    private volatile String sessionId = "stateless";
    
    private volatile CompletableFuture<Void> idleFuture = CompletableFuture.completedFuture(null);
    private volatile CompletableFuture<Void> toolResponseLatch = new CompletableFuture<>();
    
    private final Set<String> pendingToolCallIds = ConcurrentHashMap.newKeySet();
    private final List<ToolExecutionResultMessage> currentTurnToolResults = new CopyOnWriteArrayList<>();
    private int stepIndex = 0;

    public LangChain4jRuntime(ChatLanguageModel model) {
        this.model = Objects.requireNonNull(model, "model cannot be null");
    }

    public void setCompactionStrategy(CompactionStrategy<ChatMessage> strategy) {
        if (strategy != null) {
            this.compactionStrategy = strategy;
        }
    }

    public void setHistoryMessages(List<ChatMessage> messages) {
        if (messages != null) {
            this.chatMessages.clear();
            this.chatMessages.addAll(messages);
        }
    }

    public void setSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            this.sessionId = "stateless";
            return;
        }
        this.sessionId = sessionId;
    }

    public List<ChatMessage> getChatMessages() {
        return new ArrayList<>(this.chatMessages);
    }

    @Override
    public Set<RuntimeCapability> capabilities() {
        return Set.of(
                RuntimeCapability.STREAMING,
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
        this.toolAdapter = new ToolAdapter(config.getToolObjects());

        // Sync scanned tools back to config
        for (ToolDefinition td : toolAdapter.getTools()) {
            config.addTool(td);
        }

        // Initialize system instruction message only if history is not already present
        if (chatMessages.isEmpty() && config.getSystemInstruction() != null && !config.getSystemInstruction().isEmpty()) {
            chatMessages.add(SystemMessage.from(config.getSystemInstruction()));
        }

        this.idleFuture = CompletableFuture.completedFuture(null);
        log.info("LangChain4jRuntime initialized successfully.");
    }

    @Override
    public void send(String prompt) throws IOException {
        chatMessages.add(UserMessage.from(prompt));
        
        // Mark trajectory as active
        this.idleFuture = new CompletableFuture<>();
        
        // Spawn a virtual thread to drive the Agent execution loop
        Thread.ofVirtual().name("langchain4j-agent-loop").start(this::runAgentLoop);
    }

    private void runAgentLoop() {
        try {
            boolean keepRunning = true;
            while (keepRunning) {
                List<ToolSpecification> toolSpecs = toolAdapter.getToolSpecifications();
                Response<AiMessage> response;
                
                int beforeMessageCount = chatMessages.size();
                int beforeTokenCount = compactionStrategy.estimateTokenCount(chatMessages);
                boolean shouldCompact = compactionStrategy.shouldCompact(chatMessages);
                String reason = compactionStrategy.compactionReason(chatMessages);
                List<ChatMessage> compacted = shouldCompact
                        ? compactionStrategy.compact(chatMessages)
                        : new ArrayList<>(chatMessages);
                int afterMessageCount = compacted.size();
                int afterTokenCount = compactionStrategy.estimateTokenCount(compacted);

                if (shouldCompact) {
                    log.info(
                            "Compaction triggered. sessionId={}, reason={}, messages:{}->{}, tokens:{}->{}",
                            sessionId, reason, beforeMessageCount, afterMessageCount, beforeTokenCount, afterTokenCount
                    );
                } else {
                    log.debug(
                            "Compaction skipped. sessionId={}, reason={}, messages={}, tokens={}",
                            sessionId, reason, beforeMessageCount, beforeTokenCount
                    );
                }
                log.info("Invoking LangChain4j delegate model (compacted context size: {})...", compacted.size());
                
                if (model instanceof dev.langchain4j.model.chat.StreamingChatLanguageModel streamingModel) {
                    CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();
                    dev.langchain4j.model.StreamingResponseHandler<AiMessage> handler = new dev.langchain4j.model.StreamingResponseHandler<>() {
                        @Override
                        public void onNext(String token) {
                            try {
                                stepQueue.put(new Step("trajectory-lc4j:stream", -1, StepType.STREAM_TOKEN, StepSource.MODEL, StepTarget.USER, StepStatus.ACTIVE, token, token, "", "", Collections.emptyList(), null, false, null, null));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }

                        @Override
                        public void onComplete(Response<AiMessage> res) {
                            futureResponse.complete(res);
                        }

                        @Override
                        public void onError(Throwable error) {
                            futureResponse.completeExceptionally(error);
                        }
                    };

                    if (toolSpecs != null && !toolSpecs.isEmpty()) {
                        streamingModel.generate(compacted, toolSpecs, handler);
                    } else {
                        streamingModel.generate(compacted, handler);
                    }
                    response = futureResponse.join();
                } else {
                    if (toolSpecs != null && !toolSpecs.isEmpty()) {
                        response = model.generate(compacted, toolSpecs);
                    } else {
                        response = model.generate(compacted);
                    }
                }
                
                if (response.tokenUsage() != null) {
                    org.flexagent.core.runtime.FlexAgentObservationUtils.recordTokenUsage(
                            "langchain4j",
                            response.tokenUsage().inputTokenCount() != null ? response.tokenUsage().inputTokenCount() : 0,
                            response.tokenUsage().outputTokenCount() != null ? response.tokenUsage().outputTokenCount() : 0
                    );
                }
                
                AiMessage aiMessage = response.content();
                chatMessages.add(aiMessage);

                // 1. Process Thinking and text deltas via XmlThinkTagExtractor
                String rawText = aiMessage.text();
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

                boolean isComplete = !aiMessage.hasToolExecutionRequests();
                
                // 2. Put text response into queue
                Step responseStep = new Step(
                        "trajectory-lc4j:" + stepIndex,
                        stepIndex++,
                        StepType.TEXT_RESPONSE,
                        StepSource.MODEL,
                        StepTarget.USER,
                        StepStatus.DONE,
                        text,
                        text,
                        thinking,
                        thinking,
                        Collections.emptyList(),
                        null,
                        isComplete,
                        null,
                        new UsageMetadata(
                                response.tokenUsage() != null ? response.tokenUsage().inputTokenCount() : 0,
                                0,
                                response.tokenUsage() != null ? response.tokenUsage().outputTokenCount() : 0,
                                0,
                                response.tokenUsage() != null ? response.tokenUsage().totalTokenCount() : 0
                        )
                );
                stepQueue.put(responseStep);

                // 3. Process custom tools execution requests
                if (aiMessage.hasToolExecutionRequests()) {
                    ToolCallParsingException fallbackError = null;
                    List<String> repairedArguments = new ArrayList<>();
                    
                    for (dev.langchain4j.agent.tool.ToolExecutionRequest req : aiMessage.toolExecutionRequests()) {
                        String args = req.arguments();
                        if (args != null && !args.trim().isEmpty()) {
                            try {
                                repairedArguments.add(validateOrRepairArguments(req, args));
                            } catch (ToolCallParsingException e) {
                                if (toolCallPolicy() == ToolCallPolicy.TEXT_FALLBACK) {
                                    fallbackError = e;
                                    break;
                                }
                                throw e;
                            }
                        } else {
                            repairedArguments.add(args);
                        }
                    }

                    if (fallbackError != null && toolCallPolicy() == ToolCallPolicy.TEXT_FALLBACK) {
                        log.info("Fallback policy triggered: converting tool call to plain text due to JSON parsing error.");
                        String fallbackText = text;
                        if (fallbackText == null || fallbackText.isBlank()) {
                            fallbackText = "Tool call skipped because arguments were not valid JSON.";
                        }
                        fallbackText += "\n[Tool Call Fallback: " + fallbackError.getMessage() + "]";
                        Step fallbackStep = new Step(
                                "trajectory-lc4j:" + stepIndex,
                                stepIndex++,
                                StepType.TEXT_RESPONSE,
                                StepSource.MODEL,
                                StepTarget.USER,
                                StepStatus.DONE,
                                fallbackText,
                                fallbackText,
                                thinking,
                                thinking,
                                Collections.emptyList(),
                                null,
                                true,
                                null,
                                null
                        );
                        stepQueue.put(fallbackStep);
                        keepRunning = false;
                        continue;
                    }

                    List<ToolCall> toolCalls = new ArrayList<>();
                    pendingToolCallIds.clear();
                    
                    List<dev.langchain4j.agent.tool.ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
                    for (int i = 0; i < requests.size(); i++) {
                        dev.langchain4j.agent.tool.ToolExecutionRequest req = requests.get(i);
                        String tcId = req.id() != null ? req.id() : UUID.randomUUID().toString();
                        pendingToolCallIds.add(tcId);
                        
                        String finalArgsJson = (i < repairedArguments.size()) ? repairedArguments.get(i) : req.arguments();
                        ToolCall toolCall = new ToolCall(
                                tcId,
                                req.name(),
                                parseArguments(finalArgsJson),
                                finalArgsJson,
                                null
                        );
                        toolCalls.add(toolCall);
                    }

                    // Reset Latch to wait for responses
                    toolResponseLatch = new CompletableFuture<>();

                    Step toolCallStep = new Step(
                            "trajectory-lc4j:" + stepIndex,
                            stepIndex++,
                            StepType.TOOL_CALL,
                            StepSource.MODEL,
                            StepTarget.ENVIRONMENT,
                            StepStatus.ACTIVE,
                            "", "", "", "",
                            toolCalls,
                            null,
                            false,
                            null,
                            null
                    );
                    stepQueue.put(toolCallStep);

                    // Await external execution of tool calls
                    try {
                        toolResponseLatch.get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        keepRunning = false;
                    } catch (ExecutionException e) {
                        log.error("Failed waiting for tool result", e);
                        keepRunning = false;
                    }

                    // Append execution results to message context
                    chatMessages.addAll(currentTurnToolResults);
                    currentTurnToolResults.clear();

                } else {
                    keepRunning = false;
                }
            }
        } catch (Exception e) {
            log.error("Unexpected error in LangChain4j Agent Loop", e);
            try {
                stepQueue.put(new Step(
                        "trajectory-lc4j:error",
                        -1,
                        StepType.UNKNOWN,
                        StepSource.MODEL,
                        StepTarget.USER,
                        StepStatus.ERROR,
                        "", "", "", "",
                        Collections.emptyList(),
                        e.getMessage(),
                        true,
                        null,
                        null
                ));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        } finally {
            idleFuture.complete(null);
        }
    }

    @Override
    public Step pollStep(long timeout, TimeUnit unit) throws InterruptedException {
        return stepQueue.poll(timeout, unit);
    }

    @Override
    public void sendToolResult(ToolResult result) throws IOException {
        if (!pendingToolCallIds.contains(result.id())) {
            log.warn("Received tool result for non-pending tool call ID: {}", result.id());
            return;
        }

        String content = "{}";
        if (result.result() != null) {
            content = objectMapper.writeValueAsString(result.result());
        } else if (result.error() != null) {
            content = result.error();
        }

        ToolExecutionResultMessage message = ToolExecutionResultMessage.from(
                result.id(),
                result.name(),
                content
        );

        currentTurnToolResults.add(message);
        pendingToolCallIds.remove(result.id());

        if (pendingToolCallIds.isEmpty()) {
            toolResponseLatch.complete(null);
        }
    }

    @Override
    public void waitForIdle() {
        try {
            idleFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            log.error("Error waiting for idle in LangChain4jRuntime", e);
        }
    }

    @Override
    public void close() throws Exception {
        // No-op
    }

    private Map<String, Object> parseArguments(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            ToolCallPolicy policy = toolCallPolicy();
            if (policy == ToolCallPolicy.LENIENT) {
                log.info("Attempting to repair JSON arguments: {}", json);
                String repaired = tryRepairJson(json);
                try {
                    return objectMapper.readValue(repaired, new TypeReference<Map<String, Object>>() {});
                } catch (Exception ex) {
                    log.warn("Failed to parse tool execution request arguments even after lenient repair: {}", repaired, ex);
                }
            } else {
                log.warn("Failed to parse tool execution request arguments JSON: {}", json, e);
            }
            return Collections.emptyMap();
        }
    }

    private String validateOrRepairArguments(
            dev.langchain4j.agent.tool.ToolExecutionRequest request,
            String argumentsJson
    ) {
        try {
            objectMapper.readTree(argumentsJson);
            return argumentsJson;
        } catch (Exception parseError) {
            ToolCallPolicy policy = toolCallPolicy();
            if (policy == ToolCallPolicy.STRICT || policy == ToolCallPolicy.TEXT_FALLBACK) {
                throw new ToolCallParsingException(
                        request.name(),
                        request.id(),
                        policy,
                        argumentsJson,
                        parseError.getMessage(),
                        parseError
                );
            }

            log.info("Lenient policy: attempting to repair invalid JSON arguments: {}", argumentsJson);
            String repaired = tryRepairJson(argumentsJson);
            try {
                objectMapper.readTree(repaired);
                log.info("Successfully repaired invalid JSON to: {}", repaired);
                return repaired;
            } catch (Exception repairError) {
                throw new ToolCallParsingException(
                        request.name(),
                        request.id(),
                        policy,
                        argumentsJson,
                        "lenient repair failed: " + repairError.getMessage(),
                        repairError
                );
            }
        }
    }

    private String tryRepairJson(String json) {
        if (json == null) {
            return null;
        }
        String trimmed = json.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        // 1. Convert single quotes to double quotes
        trimmed = trimmed.replace('\'', '"');

        // 2. Fix missing closing brace
        if (trimmed.startsWith("{") && !trimmed.endsWith("}")) {
            trimmed = trimmed + "}";
        }

        // 3. Remove trailing comma before closing brace: e.g., , } or ,}
        trimmed = trimmed.replaceAll(",\\s*\\}$", "}");

        // 4. Try to add double quotes to unquoted keys
        trimmed = trimmed.replaceAll("([{,]\\s*)([a-zA-Z_][a-zA-Z0-9_]*)(\\s*:)", "$1\"$2\"$3");

        return trimmed;
    }
}
