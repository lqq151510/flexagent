package org.flexagent.core.memory.compaction;

import org.flexagent.core.memory.AgentMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * Prototype strategy that preserves tool execution result messages while applying
 * a sliding window to the remaining conversation turns.
 */
public class ToolAwareCompactionStrategy extends ThresholdCompactionStrategy {
    private final int maxMessages;
    private final int maxToolMessagesToKeep;

    public ToolAwareCompactionStrategy(int maxMessages) {
        this(maxMessages, maxMessages, null, Math.max(1, maxMessages / 3));
    }

    public ToolAwareCompactionStrategy(
            int maxMessages,
            Integer messageThreshold,
            Integer tokenThreshold,
            int maxToolMessagesToKeep
    ) {
        super(messageThreshold, tokenThreshold);
        if (maxMessages < 3) {
            throw new IllegalArgumentException("maxMessages must be at least 3");
        }
        if (maxToolMessagesToKeep < 1) {
            throw new IllegalArgumentException("maxToolMessagesToKeep must be at least 1");
        }
        this.maxMessages = maxMessages;
        this.maxToolMessagesToKeep = maxToolMessagesToKeep;
    }

    @Override
    public List<AgentMessage> compact(List<AgentMessage> messages) {
        if (messages == null || messages.size() <= maxMessages) {
            return messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        }

        List<AgentMessage> result = new ArrayList<>();
        List<AgentMessage> systems = new ArrayList<>();
        List<AgentMessage> toolMessages = new ArrayList<>();
        List<AgentMessage> others = new ArrayList<>();

        for (AgentMessage msg : messages) {
            if ("system".equals(msg.role())) {
                systems.add(msg);
            } else if ("tool".equals(msg.role())) {
                toolMessages.add(msg);
            } else {
                others.add(msg);
            }
        }

        result.addAll(systems);

        int reservedSlots = Math.min(toolMessages.size(), maxToolMessagesToKeep);
        int remainingSlots = Math.max(0, maxMessages - result.size() - reservedSlots);

        if (remainingSlots > 0 && !others.isEmpty()) {
            int startIdx = Math.max(0, others.size() - remainingSlots);
            for (int i = startIdx; i < others.size(); i++) {
                result.add(others.get(i));
            }
        }

        if (!toolMessages.isEmpty()) {
            int startIdx = Math.max(0, toolMessages.size() - reservedSlots);
            for (int i = startIdx; i < toolMessages.size(); i++) {
                result.add(toolMessages.get(i));
            }
        }

        return result;
    }
}
