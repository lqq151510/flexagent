package org.flexagent.console.controller;

import org.flexagent.core.model.Step;
import org.flexagent.core.model.StepType;
import org.flexagent.core.model.ToolCall;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.runtime.AgentRuntime;
import org.flexagent.langchain4j.FlexAgentChatModel;
import org.flexagent.mcp.McpToolExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final FlexAgentChatModel agentModel;
    private final McpToolExecutor mcpToolExecutor;

    public ChatController(FlexAgentChatModel agentModel, McpToolExecutor mcpToolExecutor) {
        this.agentModel = agentModel;
        this.mcpToolExecutor = mcpToolExecutor;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Step> chatStream(@RequestBody Map<String, String> payload) {
        String message = payload.getOrDefault("message", "");
        AgentRuntime runtime = agentModel.activeRuntime();

        return Flux.<Step>create(sink -> {
            try {
                // Send the prompt asynchronously to the underlying runtime
                runtime.send(message);

                AtomicBoolean keepPolling = new AtomicBoolean(true);
                sink.onCancel(() -> keepPolling.set(false));
                sink.onDispose(() -> keepPolling.set(false));

                Runnable pollingTask = () -> {
                    try {
                        while (keepPolling.get()) {
                            Step step = runtime.pollStep(100, TimeUnit.MILLISECONDS);
                            if (step == null) continue;

                            // Emit the raw step to the frontend
                            sink.next(step);

                            if (step.status() == org.flexagent.core.model.StepStatus.ERROR) {
                                sink.complete();
                                return;
                            }

                            // Handle tool calls
                            if (step.type() == StepType.TOOL_CALL && !step.toolCalls().isEmpty()) {
                                for (ToolCall toolCall : step.toolCalls()) {
                                    ToolResult toolResult = null;
                                    if (mcpToolExecutor.supports(toolCall.name())) {
                                        toolResult = mcpToolExecutor.execute(toolCall);
                                    } else {
                                        toolResult = new ToolResult(toolCall.id(), toolCall.name(), null, "Tool not supported: " + toolCall.name());
                                    }
                                    runtime.sendToolResult(toolResult);
                                    
                                    Step toolDoneStep = new Step(toolCall.id(), 0, StepType.TOOL_CALL, org.flexagent.core.model.StepSource.SYSTEM, null, org.flexagent.core.model.StepStatus.SUCCESS, "TOOL_DONE", null, null, null, null, null, null, null, null);
                                    sink.next(toolDoneStep);
                                }
                            }

                            // Check completion
                            if (Boolean.TRUE.equals(step.isCompleteResponse()) && step.type() == StepType.TEXT_RESPONSE) {
                                sink.complete();
                                return;
                            }
                        }
                    } catch (Exception e) {
                        sink.error(e);
                    }
                };

                Schedulers.boundedElastic().schedule(pollingTask);

            } catch (Exception e) {
                sink.error(e);
            }
        });
    }
}
