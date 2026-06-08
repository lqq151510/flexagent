package org.flexagent.core.memory.compaction;

import org.flexagent.core.memory.AgentMessage;
import java.util.ArrayList;
import java.util.List;

public class SlidingWindowCompactionStrategy extends ThresholdCompactionStrategy {
    private final int maxMessages;

    public SlidingWindowCompactionStrategy(int maxMessages) {
        this(maxMessages, maxMessages, null);
    }

    public SlidingWindowCompactionStrategy(int maxMessages, Integer messageThreshold, Integer tokenThreshold) {
        super(messageThreshold, tokenThreshold);
        if (maxMessages < 2) {
            throw new IllegalArgumentException("maxMessages must be at least 2");
        }
        this.maxMessages = maxMessages;
    }

    @Override
    public List<AgentMessage> compact(List<AgentMessage> messages) {
        if (messages == null || messages.size() <= maxMessages) {
            return messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        }

        List<AgentMessage> result = new ArrayList<>();
        List<AgentMessage> systems = new ArrayList<>();
        List<AgentMessage> others = new ArrayList<>();

        for (AgentMessage msg : messages) {
            if ("system".equals(msg.role())) {
                systems.add(msg);
            } else {
                others.add(msg);
            }
        }

        result.addAll(systems);

        int slotsLeft = maxMessages - systems.size();
        if (slotsLeft > 0 && !others.isEmpty()) {
            int startIdx = Math.max(0, others.size() - slotsLeft);
            for (int i = startIdx; i < others.size(); i++) {
                result.add(others.get(i));
            }
        }

        return result;
    }
}
