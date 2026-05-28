package org.flexagent.core.memory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryAgentMemory implements AgentMemory {
    private final Map<String, List<AgentMessage>> storage = new ConcurrentHashMap<>();

    @Override
    public List<AgentMessage> getMessages(String sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        return storage.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>());
    }

    @Override
    public void addMessage(String sessionId, AgentMessage message) {
        if (sessionId == null || message == null) {
            return;
        }
        storage.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(message);
    }

    @Override
    public void clear(String sessionId) {
        if (sessionId != null) {
            storage.remove(sessionId);
        }
    }
}
