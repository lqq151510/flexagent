package org.flexagent.core.tool;

import org.flexagent.core.model.ToolCall;
import org.flexagent.core.model.ToolResult;

/**
 * Interface to allow custom execution of tools.
 * Implemented by modules like flexagent-mcp to handle remote or dynamic tool invocation
 * without forcing local Java reflection.
 */
public interface CustomToolExecutor {
    /**
     * Checks if this executor supports the specified tool.
     * @param toolName Name of the tool
     * @return true if supported, false otherwise
     */
    boolean supports(String toolName);

    /**
     * Executes the specified tool call.
     * @param toolCall The tool call detail
     * @return ToolResult containing return value or execution error
     */
    ToolResult execute(ToolCall toolCall);
}
