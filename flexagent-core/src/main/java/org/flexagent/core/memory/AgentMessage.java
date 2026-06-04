package org.flexagent.core.memory;

import org.flexagent.core.model.ToolCall;
import java.util.List;

public record AgentMessage(
    String role,         // "system", "user", "assistant", "tool"
    String text,
    List<ToolCall> toolCalls,
    String toolId,
    String toolName
) {
    public AgentMessage {
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }
        if (toolCalls != null) {
            toolCalls = List.copyOf(toolCalls);
        }
    }

    public static AgentMessage system(String text) {
        return new AgentMessage("system", text, null, null, null);
    }

    public static AgentMessage user(String text) {
        return new AgentMessage("user", text, null, null, null);
    }

    public static AgentMessage assistant(String text) {
        return new AgentMessage("assistant", text, null, null, null);
    }

    public static AgentMessage assistant(String text, List<ToolCall> toolCalls) {
        return new AgentMessage("assistant", text, toolCalls, null, null);
    }

    public static AgentMessage tool(String toolId, String toolName, String text) {
        return new AgentMessage("tool", text, null, toolId, toolName);
    }
}
