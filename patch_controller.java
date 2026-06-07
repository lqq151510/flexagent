package org.flexagent.console.controller;

import org.flexagent.core.model.Step;
import org.flexagent.core.model.StepType;
import org.flexagent.core.model.ToolCall;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.runtime.AgentRuntime;
import org.flexagent.core.memory.AgentMemory;
import org.flexagent.core.memory.AgentMessage;
import org.flexagent.langchain4j.FlexAgentChatModel;
import org.flexagent.mcp.McpToolExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.ObjectProvider;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final FlexAgentChatModel agentModel;
    private final McpToolExecutor mcpToolExecutor;
    private final AgentMemory agentMemory;

    public ChatController(FlexAgentChatModel agentModel, McpToolExecutor mcpToolExecutor, ObjectProvider<AgentMemory> agentMemoryProvider) {
        this.agentModel = agentModel;
        this.mcpToolExecutor = mcpToolExecutor;
        this.agentMemory = agentMemoryProvider.getIfAvailable();
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Step> chatStream(@RequestBody Map<String, String> payload) {
        String sessionId = payload.getOrDefault("sessionId", "default-session");
        String message = payload.getOrDefault("message", "");
        AgentRuntime runtime = agentModel.activeRuntime();

        return Flux.<Step>create(sink -> {
            try {
                org.flexagent.core.memory.AgentSessionContext.set(sessionId);
                boolean hasMemory = (agentMemory != null && sessionId != null);
                
                // Note: SpringAiRuntime does not have setHistoryMessages, so this is best-effort for LangChain4jRuntime
                if (hasMemory) {
                    if (runtime.getClass().getName().contains("LangChain4jRuntime")) {
                        try {
                            List<AgentMessage> history = agentMemory.getMessages(sessionId);
                            if (history == null) {
                                history = new ArrayList<>();
                            }
                            
                            // Use reflection since toChatMessage is package-private
                            java.lang.reflect.Method toChatMessageMethod = agentModel.getClass().getDeclaredMethod("toChatMessage", AgentMessage.class);
                            toChatMessageMethod.setAccessible(true);
                            
                            List<Object> chatHistory = new ArrayList<>();
                            for (AgentMessage am : history) {
                                chatHistory.add(toChatMessageMethod.invoke(agentModel, am));
                            }
                            
                            java.lang.reflect.Method withInitialSystemMessagesMethod = agentModel.getClass().getDeclaredMethod("withInitialSystemMessages", List.class);
                            withInitialSystemMessagesMethod.setAccessible(true);
                            chatHistory = (List<Object>) withInitialSystemMessagesMethod.invoke(agentModel, chatHistory);
                            
                            java.lang.reflect.Method setHistoryMessagesMethod = runtime.getClass().getMethod("setHistoryMessages", List.class);
                            setHistoryMessagesMethod.invoke(runtime, chatHistory);
                            
                            java.lang.reflect.Method setSessionIdMethod = runtime.getClass().getMethod("setSessionId", String.class);
                            setSessionIdMethod.invoke(runtime, sessionId);
                        } catch (Exception e) {
                            // Ignored if methods don't exist
                        }
                    }
                }

                // Send the prompt asynchronously to the underlying runtime
                runtime.send(message);

                AtomicBoolean keepPolling = new AtomicBoolean(true);
                StringBuilder contentBuilder = new StringBuilder();
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
                                    
                                    Step toolDoneStep = new Step(toolCall.id(), 0, StepType.TOOL_CALL, org.flexagent.core.model.StepSource.SYSTEM, null, org.flexagent.core.model.StepStatus.DONE, "TOOL_DONE", null, null, null, null, null, null, null, null);
                                    sink.next(toolDoneStep);
                                }
                            }
                            
                            if (step.contentDelta() != null && !step.contentDelta().isEmpty()) {
                                contentBuilder.append(step.contentDelta());
                            }

                            // Check completion
                            if (Boolean.TRUE.equals(step.isCompleteResponse()) && step.type() == StepType.TEXT_RESPONSE) {
                                if (hasMemory) {
                                    if (runtime.getClass().getName().contains("LangChain4jRuntime")) {
                                        try {
                                            java.lang.reflect.Method getChatMessagesMethod = runtime.getClass().getMethod("getChatMessages");
                                            List<?> updatedMessages = (List<?>) getChatMessagesMethod.invoke(runtime);
                                            
                                            java.lang.reflect.Method toAgentMessageMethod = agentModel.getClass().getDeclaredMethod("toAgentMessage", dev.langchain4j.data.message.ChatMessage.class);
                                            toAgentMessageMethod.setAccessible(true);
                                            
                                            List<AgentMessage> updatedAgentMessages = new ArrayList<>();
                                            for (Object cm : updatedMessages) {
                                                updatedAgentMessages.add((AgentMessage) toAgentMessageMethod.invoke(agentModel, cm));
                                            }
                                            agentMemory.clear(sessionId);
                                            agentMemory.addMessages(sessionId, updatedAgentMessages);
                                        } catch (Exception e) {
                                            // Fallback
                                            agentMemory.addMessage(sessionId, AgentMessage.user(message));
                                            agentMemory.addMessage(sessionId, AgentMessage.assistant(contentBuilder.toString()));
                                        }
                                    } else {
                                        agentMemory.addMessage(sessionId, AgentMessage.user(message));
                                        agentMemory.addMessage(sessionId, AgentMessage.assistant(contentBuilder.toString()));
                                    }
                                }
                                sink.complete();
                                return;
                            }
                        }
                    } catch (Exception e) {
                        sink.error(e);
                    } finally {
                        org.flexagent.core.memory.AgentSessionContext.clear();
                    }
                };

                Schedulers.boundedElastic().schedule(pollingTask);

            } catch (Exception e) {
                sink.error(e);
            }
        });
    }
}
