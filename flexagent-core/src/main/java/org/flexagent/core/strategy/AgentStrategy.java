package org.flexagent.core.strategy;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.runtime.AgentRuntime;
import java.util.function.Function;
import java.io.IOException;

/**
 * Defines a high-level reasoning strategy for the Agent.
 * This strategy dictates the loop of thought, tool execution, and response generation.
 */
public interface AgentStrategy {

    /**
     * Executes the user prompt according to the strategy's reasoning pattern.
     *
     * @param prompt the user prompt
     * @param runtime the initialized agent runtime
     * @param toolExecutor the function to execute tool calls
     * @return the final response message
     * @throws IOException if an error occurs
     */
    AgentMessage execute(String prompt, AgentRuntime runtime, Function<org.flexagent.core.model.ToolCall, org.flexagent.core.model.ToolResult> toolExecutor) throws IOException;

    /**
     * Executes the user prompt according to the strategy's reasoning pattern with streaming token support.
     *
     * @param prompt the user prompt
     * @param runtime the initialized agent runtime
     * @param toolExecutor the function to execute tool calls
     * @param tokenHandler the consumer for streaming tokens
     * @return the final response message
     * @throws IOException if an error occurs
     */
    default AgentMessage executeStream(String prompt, AgentRuntime runtime, Function<org.flexagent.core.model.ToolCall, org.flexagent.core.model.ToolResult> toolExecutor, java.util.function.Consumer<String> tokenHandler) throws IOException {
        // default fallback to synchronous execution
        AgentMessage message = execute(prompt, runtime, toolExecutor);
        if (tokenHandler != null && message.text() != null) {
            tokenHandler.accept(message.text());
        }
        return message;
    }
}
