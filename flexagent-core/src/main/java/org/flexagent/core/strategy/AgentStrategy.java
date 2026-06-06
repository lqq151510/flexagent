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
}
