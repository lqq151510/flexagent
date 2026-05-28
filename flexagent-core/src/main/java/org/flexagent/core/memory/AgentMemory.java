package org.flexagent.core.memory;

import java.util.List;

public interface AgentMemory {
    List<AgentMessage> getMessages(String sessionId);
    void addMessage(String sessionId, AgentMessage message);
    default void addMessages(String sessionId, List<AgentMessage> messages) {
        if (messages != null) {
            for (AgentMessage msg : messages) {
                addMessage(sessionId, msg);
            }
        }
    }
    void clear(String sessionId);
}
