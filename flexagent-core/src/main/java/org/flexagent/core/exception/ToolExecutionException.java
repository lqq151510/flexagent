package org.flexagent.core.exception;

/**
 * Exception thrown when a tool execution fails.
 */
public class ToolExecutionException extends FlexAgentException {
    public ToolExecutionException(String message) {
        super(message);
    }

    public ToolExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
