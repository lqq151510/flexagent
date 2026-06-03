package org.flexagent.core.memory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class InMemoryAgentMemory implements AgentMemory {
    private final Map<String, MemoryEntry> storage = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final ScheduledExecutorService cleanupScheduler;

    private static class MemoryEntry {
        final List<AgentMessage> messages;
        volatile long lastAccessTime;

        MemoryEntry(List<AgentMessage> messages) {
            this.messages = messages;
            this.lastAccessTime = System.currentTimeMillis();
        }

        void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }

    public InMemoryAgentMemory() {
        this(null);
    }

    public InMemoryAgentMemory(Duration ttl) {
        this.ttl = ttl;
        if (ttl != null) {
            this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "flexagent-memory-cleanup");
                t.setDaemon(true);
                return t;
            });
            this.cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 1, 1, TimeUnit.MINUTES);
        } else {
            this.cleanupScheduler = null;
        }
    }

    private boolean isExpired(MemoryEntry entry) {
        if (ttl == null || entry == null) {
            return false;
        }
        return (System.currentTimeMillis() - entry.lastAccessTime) > ttl.toMillis();
    }

    private void cleanupExpiredSessions() {
        storage.entrySet().removeIf(entry -> isExpired(entry.getValue()));
    }

    @Override
    public List<AgentMessage> getMessages(String sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        MemoryEntry entry = storage.get(sessionId);
        if (entry == null) {
            return List.of();
        }
        if (isExpired(entry)) {
            storage.remove(sessionId);
            return List.of();
        }
        entry.updateAccessTime();
        return List.copyOf(entry.messages);
    }

    @Override
    public void addMessage(String sessionId, AgentMessage message) {
        if (sessionId == null || message == null) {
            return;
        }
        MemoryEntry entry = storage.get(sessionId);
        if (entry != null && isExpired(entry)) {
            storage.remove(sessionId);
            entry = null;
        }
        if (entry == null) {
            entry = storage.computeIfAbsent(sessionId, k -> new MemoryEntry(new CopyOnWriteArrayList<>()));
        } else {
            entry.updateAccessTime();
        }
        entry.messages.add(message);
    }

    @Override
    public void clear(String sessionId) {
        if (sessionId != null) {
            storage.remove(sessionId);
        }
    }

    @Override
    public void close() throws Exception {
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
