package org.flexagent.core.memory;

import java.util.ArrayList;
import java.util.List;

/**
 * A memory decorator that retains only the last N messages, preventing context window overflow.
 * It intelligently preserves System messages, ensuring core instructions are not evicted.
 */
public class SlidingWindowMemory implements AgentMemory {
    private final AgentMemory delegate;
    private final int maxMessages;

    public SlidingWindowMemory(AgentMemory delegate, int maxMessages) {
        this.delegate = delegate;
        this.maxMessages = maxMessages;
    }

    @Override
    public List<AgentMessage> getMessages(String sessionId) {
        return delegate.getMessages(sessionId);
    }

    @Override
    public void addMessage(String sessionId, AgentMessage message) {
        delegate.addMessage(sessionId, message);
        enforceWindow(sessionId);
    }

    @Override
    public void addMessages(String sessionId, List<AgentMessage> messages) {
        delegate.addMessages(sessionId, messages);
        enforceWindow(sessionId);
    }

    private void enforceWindow(String sessionId) {
        List<AgentMessage> all = delegate.getMessages(sessionId);
        if (all == null) return;
        
        if (all.size() > maxMessages) {
            List<AgentMessage> systemMessages = new ArrayList<>();
            List<AgentMessage> otherMessages = new ArrayList<>();
            
            for (AgentMessage msg : all) {
                if ("system".equals(msg.role())) {
                    systemMessages.add(msg);
                } else {
                    otherMessages.add(msg);
                }
            }
            
            int slotsForOther = maxMessages - systemMessages.size();
            if (slotsForOther < 0) slotsForOther = 0;
            
            List<AgentMessage> trimmedOthers;
            if (otherMessages.size() > slotsForOther) {
                trimmedOthers = new ArrayList<>(otherMessages.subList(otherMessages.size() - slotsForOther, otherMessages.size()));
            } else {
                trimmedOthers = otherMessages;
            }
            
            List<AgentMessage> finalList = new ArrayList<>();
            finalList.addAll(systemMessages);
            finalList.addAll(trimmedOthers);
            
            delegate.clear(sessionId);
            delegate.addMessages(sessionId, finalList);
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
