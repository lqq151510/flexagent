package org.flexagent.core.strategy;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.model.Step;
import org.flexagent.core.model.ToolCall;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.model.StepStatus;
import org.flexagent.core.model.StepType;
import org.flexagent.core.runtime.AgentRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * The default Reasoning + Acting (ReAct) strategy.
 * The Agent generates thoughts, optionally calls tools, observes results, and then generates final output.
 */
public class ReActStrategy implements AgentStrategy {

    private static final Logger log = LoggerFactory.getLogger(ReActStrategy.class);

    @Override
    public AgentMessage execute(String prompt, AgentRuntime runtime, Function<org.flexagent.core.model.ToolCall, org.flexagent.core.model.ToolResult> toolExecutor) throws IOException {
        return executeStream(prompt, runtime, toolExecutor, null);
    }

    @Override
    public AgentMessage executeStream(String prompt, AgentRuntime runtime, Function<org.flexagent.core.model.ToolCall, org.flexagent.core.model.ToolResult> toolExecutor, java.util.function.Consumer<String> tokenHandler) throws IOException {
        if (!(runtime instanceof org.flexagent.core.runtime.ReactiveAgentRuntime)) {
            throw new IllegalArgumentException("Runtime must be an instance of ReactiveAgentRuntime to support reactive execution");
        }
        org.flexagent.core.runtime.ReactiveAgentRuntime reactiveRuntime = (org.flexagent.core.runtime.ReactiveAgentRuntime) runtime;

        StringBuilder contentBuilder = new StringBuilder();

        try {
            reactiveRuntime.generateStream(prompt)
                    .doOnNext(step -> {
                        if (step.status() == StepStatus.ERROR) {
                            throw new RuntimeException(new IOException("Runtime execution error: " + step.error()));
                        }

                        if (step.type() == StepType.STREAM_TOKEN) {
                            if (tokenHandler != null && step.content() != null) {
                                tokenHandler.accept(step.content());
                            }
                            return;
                        }

                        // Check for tool call requests from the model
                        if (step.type() == StepType.TOOL_CALL && !step.toolCalls().isEmpty()) {
                            for (ToolCall toolCall : step.toolCalls()) {
                                log.info("[ReAct] Executing Tool: {} (args: {})", toolCall.name(), toolCall.argumentsJson());
                                ToolResult toolResult = toolExecutor.apply(toolCall);
                                log.info("[ReAct] Tool Result: {}", toolResult);
                                try {
                                    reactiveRuntime.sendToolResult(toolResult);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }

                        if (step.contentDelta() != null && !step.contentDelta().isEmpty()) {
                            contentBuilder.append(step.contentDelta());
                        }
                        if (step.thinkingDelta() != null && !step.thinkingDelta().isEmpty()) {
                            log.info("[ReAct Thinking] {}", step.thinkingDelta().trim());
                        }
                    })
                    .blockLast();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new IOException("Error during reactive step execution", e);
        }

        return AgentMessage.assistant(contentBuilder.toString());
    }
}

