package org.flexagent.core.memory;

import org.flexagent.core.memory.compaction.MemoryCompactor;

import java.util.List;

/**
 * A memory decorator that triggers a memory compactor (e.g., summarization model)
 * when the history size exceeds the specified threshold.
 */
public class SummarizationMemory implements AgentMemory {
    private final AgentMemory delegate;
    private final int triggerThreshold;
    private final MemoryCompactor compactor;

    public SummarizationMemory(AgentMemory delegate, int triggerThreshold, MemoryCompactor compactor) {
        this.delegate = delegate;
        this.triggerThreshold = triggerThreshold;
        this.compactor = compactor;
    }

    @Override
    public List<AgentMessage> getMessages(String sessionId) {
        return delegate.getMessages(sessionId);
    }

    @Override
    public void addMessage(String sessionId, AgentMessage message) {
        delegate.addMessage(sessionId, message);
        checkAndCompact(sessionId);
    }

    @Override
    public void addMessages(String sessionId, List<AgentMessage> messages) {
        delegate.addMessages(sessionId, messages);
        checkAndCompact(sessionId);
    }

    private void checkAndCompact(String sessionId) {
        List<AgentMessage> all = delegate.getMessages(sessionId);
        if (all == null) return;
        
        if (all.size() > triggerThreshold) {
            List<AgentMessage> compacted = compactor.compact(all);
            if (compacted != null && !compacted.isEmpty()) {
                delegate.clear(sessionId);
                delegate.addMessages(sessionId, compacted);
            }
        }
    }

    @Override
    public void clear(String sessionId) {
        delegate.clear(sessionId);
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }
}
