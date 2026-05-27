package org.flexagent.localharness;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flexagent.core.model.Step;
import org.flexagent.core.model.ToolCall;
import org.flexagent.core.model.ToolResult;
import org.flexagent.localharness.proto.*;
import com.google.protobuf.util.JsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class FlexAgentConnection implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(FlexAgentConnection.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final HarnessProcessManager processManager;
    private final BlockingQueue<Step> stepQueue = new LinkedBlockingQueue<>();
    
    private WebSocket webSocket;
    private CompletableFuture<Void> idleFuture = new CompletableFuture<>();
    private Consumer<ToolCall> toolCallHandler;

    public FlexAgentConnection(HarnessProcessManager processManager) {
        this.processManager = processManager;
    }

    public void connect() throws IOException {
        int port = processManager.getPort();
        String apiKey = processManager.getApiKey();
        String wsUrl = "ws://127.0.0.1:" + port + "/";
        log.info("Connecting to WebSocket: {}", wsUrl);

        HttpClient client = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        try {
            this.webSocket = client.newWebSocketBuilder()
                    .header("x-goog-api-key", apiKey)
                    .buildAsync(URI.create(wsUrl), new HarnessWebSocketListener())
                    .get(10, TimeUnit.SECONDS);
            log.info("WebSocket connected successfully.");
        } catch (Exception e) {
            log.error("Failed to connect to WebSocket at {}", wsUrl, e);
            throw new IOException("WebSocket connection failed", e);
        }
    }

    public void initialize(HarnessConfig config) throws IOException {
        InitializeConversationEvent event = InitializeConversationEvent.newBuilder()
                .setConfig(config)
                .build();
        sendJson(JsonFormat.printer().print(event));
    }

    public void send(String prompt) throws IOException {
        idleFuture = new CompletableFuture<>();
        InputEvent event = InputEvent.newBuilder()
                .setUserInput(prompt != null ? prompt : "")
                .build();
        sendJson(JsonFormat.printer().print(event));
    }

    public void sendToolResult(ToolResult result) throws IOException {
        String responseJson = "{}";
        if (result.result() != null) {
            try {
                responseJson = mapper.writeValueAsString(result.result());
            } catch (Exception e) {
                log.warn("Failed to serialize tool result to JSON", e);
            }
        }

        ToolResponse.Builder toolResponse = ToolResponse.newBuilder()
                .setId(result.id() != null ? result.id() : "")
                .setResponseJson(responseJson);
        
        InputEvent event = InputEvent.newBuilder()
                .setToolResponse(toolResponse)
                .build();
        sendJson(JsonFormat.printer().print(event));
    }

    public void setToolCallHandler(Consumer<ToolCall> toolCallHandler) {
        this.toolCallHandler = toolCallHandler;
    }

    public Step nextStep() throws InterruptedException {
        return stepQueue.take();
    }

    public Step pollStep(long timeout, TimeUnit unit) throws InterruptedException {
        return stepQueue.poll(timeout, unit);
    }

    public void waitForIdle() {
        try {
            idleFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            log.error("Error waiting for turn to become idle", e);
        }
    }

    private void sendJson(String json) {
        if (webSocket == null || webSocket.isOutputClosed()) {
            throw new IllegalStateException("WebSocket is closed, cannot send message");
        }
        webSocket.sendText(json, true);
    }

    @Override
    public void close() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "SDK close");
        }
        processManager.close();
    }

    private class HarnessWebSocketListener implements WebSocket.Listener {
        private final StringBuilder wsBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            log.info("Harness WebSocket session opened.");
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            wsBuffer.append(data);
            if (last) {
                String completeMessage = wsBuffer.toString();
                wsBuffer.setLength(0);
                Thread.ofVirtual().name("harness-ws-handler").start(() -> handleMessage(completeMessage));
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("Harness WebSocket session closed with status: {}, reason: {}", statusCode, reason);
            idleFuture.complete(null);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.error("Harness WebSocket error", error);
            idleFuture.completeExceptionally(error);
        }
    }

    private void handleMessage(String jsonString) {
        try {
            OutputEvent.Builder builder = OutputEvent.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(jsonString, builder);
            OutputEvent event = builder.build();

            if (event.hasStepUpdate()) {
                Step step = EventParser.parseStep(event.getStepUpdate(), event.getUsageMetadata());
                stepQueue.put(step);
            } else if (event.hasTrajectoryStateUpdate()) {
                TrajectoryStateUpdate tsu = event.getTrajectoryStateUpdate();
                if (tsu.getState() == TrajectoryStateUpdate.State.STATE_IDLE) {
                    log.debug("Trajectory state changed to IDLE for trajectory: {}", tsu.getTrajectoryId());
                    idleFuture.complete(null);
                }
            } else if (event.hasToolCall()) {
                ToolCall toolCall = EventParser.parseToolCall(event.getToolCall());
                Step toolCallStep = new Step(
                        toolCall.id(),
                        -1,
                        org.flexagent.core.model.StepType.TOOL_CALL,
                        org.flexagent.core.model.StepSource.MODEL,
                        org.flexagent.core.model.StepTarget.ENVIRONMENT,
                        org.flexagent.core.model.StepStatus.ACTIVE,
                        "",
                        "",
                        "",
                        "",
                        java.util.List.of(toolCall),
                        null,
                        false,
                        null,
                        null
                );
                stepQueue.put(toolCallStep);
                if (toolCallHandler != null) {
                    Thread.ofVirtual().name("harness-tool-executor").start(() -> toolCallHandler.accept(toolCall));
                }
            }
        } catch (Exception e) {
            log.error("Error handling incoming WebSocket message: {}", jsonString, e);
        }
    }
}
