package org.flexagent.core.orchestration;

import org.flexagent.core.memory.AgentMessage;

/**
 * Represents a message exchanged within a group chat, binding the sender's identity
 * to the underlying AgentMessage content.
 */
public record GroupChatMessage(String sender, AgentMessage message) {
    public String text() {
        return message != null ? message.text() : "";
    }
}
