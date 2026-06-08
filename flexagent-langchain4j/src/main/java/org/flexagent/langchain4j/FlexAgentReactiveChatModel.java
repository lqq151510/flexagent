package org.flexagent.langchain4j;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.flexagent.core.exception.FlexAgentException;
import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.memory.AgentSessionContext;
import org.flexagent.core.runtime.FlexAgentObservationUtils;
import org.flexagent.core.model.Step;
import org.flexagent.core.model.ToolCall;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.tool.CustomToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A reactive wrapper for FlexAgentChatModel, providing non-blocking asynchronous APIs
 * using Project Reactor.
 */
public class FlexAgentReactiveChatModel {
    private static final Logger log = LoggerFactory.getLogger(FlexAgentReactiveChatModel.class);
    private final FlexAgentChatModel delegate;

    public FlexAgentReactiveChatModel(FlexAgentChatModel delegate) {
        this.delegate = delegate;
    }

    public Flux<AgentMessage> generateStream(String prompt) {
        return generateStream("stateless", prompt);
    }

    public Flux<AgentMessage> generateStream(String sessionId, String prompt) {
        return Flux.create((FluxSink<AgentMessage> sink) -> {
            AgentSessionContext.set(sessionId);
            try {
                boolean hasMemory = (delegate.memory != null && sessionId != null && !"stateless".equals(sessionId));

                if (hasMemory) {
                    List<AgentMessage> agentHistory = delegate.memory.getMessages(sessionId);
                    List<ChatMessage> chatHistory = new ArrayList<>();
                    if (agentHistory != null && !agentHistory.isEmpty()) {
                        FlexAgentObservationUtils.recordMemoryHit(sessionId, true);
                        for (AgentMessage am : agentHistory) {
                            chatHistory.add(MessageConverter.toChatMessage(am));
                        }
                    } else {
                        FlexAgentObservationUtils.recordMemoryHit(sessionId, false);
                    }

                    // Sync initial stateless history to memory if memory is empty
                    if (chatHistory.isEmpty()) {
                        chatHistory = new ArrayList<>();
                    }

                    chatHistory = delegate.withInitialSystemMessages(chatHistory);

                    if (delegate.activeRuntime instanceof LangChain4jRuntime lc4jRuntime) {
                        lc4jRuntime.setChatMessages(chatHistory);
                        lc4jRuntime.setSessionId(sessionId);
                    }
                } else {
                    if (delegate.activeRuntime instanceof LangChain4jRuntime lc4jRuntime) {
                        lc4jRuntime.setChatMessages(delegate.withInitialSystemMessages(new ArrayList<>()));
                        lc4jRuntime.setSessionId(sessionId);
                    }
                }

                // Send the prompt asynchronously
                delegate.activeRuntime.send(prompt);

                AtomicBoolean keepPolling = new AtomicBoolean(true);
                StringBuilder contentBuilder = new StringBuilder();

                // Poll steps asynchronously using boundedElastic scheduler
                sink.onCancel(() -> keepPolling.set(false));
                sink.onDispose(() -> keepPolling.set(false));

                Runnable pollingTask = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            while (keepPolling.get()) {
                                Step step = delegate.activeRuntime.pollStep(100, TimeUnit.MILLISECONDS);
                                if (step == null) {
                                    // If no step, yield to not hog thread, though we are blocking briefly in pollStep
                                    continue;
                                }

                                if (step.status() == org.flexagent.core.model.StepStatus.ERROR) {
                                    sink.error(new FlexAgentException("Runtime execution error: " + step.error()));
                                    return;
                                }

                                // Handle tool calls
                                if (step.type() == org.flexagent.core.model.StepType.TOOL_CALL && !step.toolCalls().isEmpty()) {
                                    for (ToolCall toolCall : step.toolCalls()) {
                                        log.info("Executing custom Tool via Reactive: {} (args: {})", toolCall.name(), toolCall.argumentsJson());
                                        ToolResult toolResult = null;
                                        for (CustomToolExecutor executor : delegate.customToolExecutors) {
                                            if (executor.supports(toolCall.name())) {
                                                toolResult = executor.execute(toolCall);
                                                break;
                                            }
                                        }
                                        if (toolResult == null) {
                                            toolResult = delegate.toolAdapter.execute(toolCall);
                                        }
                                        log.info("Tool Result: {}", toolResult);
                                        delegate.activeRuntime.sendToolResult(toolResult);
                                    }
                                }

                                // Emit deltas to sink
                                if (step.contentDelta() != null && !step.contentDelta().isEmpty()) {
                                    contentBuilder.append(step.contentDelta());
                                    sink.next(AgentMessage.assistant(step.contentDelta()));
                                }
                                if (step.thinkingDelta() != null && !step.thinkingDelta().isEmpty()) {
                                    // Optionally emit thinking as part of standard stream, or skip.
                                    // Here we just log, or could emit special AgentMessage.
                                    log.info("[Thinking] {}", step.thinkingDelta().trim());
                                }

                                if (Boolean.TRUE.equals(step.isCompleteResponse()) && step.type() == org.flexagent.core.model.StepType.TEXT_RESPONSE) {
                                    // Writeback to memory
                                    if (hasMemory) {
                                        if (delegate.activeRuntime instanceof LangChain4jRuntime lc4jRuntime) {
                                            List<ChatMessage> updatedMessages = lc4jRuntime.getChatMessages();
                                            List<AgentMessage> updatedAgentMessages = new ArrayList<>();
                                            for (ChatMessage cm : updatedMessages) {
                                                updatedAgentMessages.add(MessageConverter.toAgentMessage(cm, org.flexagent.core.model.ToolCallPolicy.LENIENT));
                                            }
                                            delegate.memory.clear(sessionId);
                                            delegate.memory.addMessages(sessionId, updatedAgentMessages);
                                        } else {
                                            delegate.memory.addMessage(sessionId, AgentMessage.user(prompt));
                                            delegate.memory.addMessage(sessionId, AgentMessage.assistant(contentBuilder.toString()));
                                        }
                                    }
                                    sink.complete();
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            sink.error(new FlexAgentException("FlexAgent reactive agent execution failed", e));
                        } finally {
                            AgentSessionContext.clear();
                        }
                    }
                };

                Schedulers.boundedElastic().schedule(pollingTask);

            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    public FlexAgentChatModel getDelegate() {
        return delegate;
    }
}
