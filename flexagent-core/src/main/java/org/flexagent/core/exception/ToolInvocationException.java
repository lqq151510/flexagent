package org.flexagent.core.exception;

public class ToolInvocationException extends FlexAgentException {

    public ToolInvocationException(String toolName, String message) {
        super(String.format("Error executing tool '%s': %s", toolName, message));
    }

    public ToolInvocationException(String toolName, String message, Throwable cause) {
        super(String.format("Error executing tool '%s': %s", toolName, message), cause);
    }
}
