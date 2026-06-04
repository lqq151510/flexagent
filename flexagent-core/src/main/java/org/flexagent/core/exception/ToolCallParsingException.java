package org.flexagent.core.exception;

import org.flexagent.core.model.ToolCallPolicy;

public class ToolCallParsingException extends FlexAgentException {
    private final String toolName;
    private final String toolCallId;
    private final ToolCallPolicy policy;
    private final String argumentsJson;

    public ToolCallParsingException(
            String toolName,
            String toolCallId,
            ToolCallPolicy policy,
            String argumentsJson,
            String message,
            Throwable cause
    ) {
        super(String.format(
                "Failed to parse tool call arguments for tool '%s' (id: %s, policy: %s): %s. " +
                        "Fix: return valid JSON object arguments or switch ToolCallPolicy to LENIENT/TEXT_FALLBACK.",
                toolName,
                toolCallId != null ? toolCallId : "unknown",
                policy,
                message
        ), cause);
        this.toolName = toolName;
        this.toolCallId = toolCallId;
        this.policy = policy;
        this.argumentsJson = argumentsJson;
    }

    public String toolName() {
        return toolName;
    }

    public String toolCallId() {
        return toolCallId;
    }

    public ToolCallPolicy policy() {
        return policy;
    }

    public String argumentsJson() {
        return argumentsJson;
    }
}
