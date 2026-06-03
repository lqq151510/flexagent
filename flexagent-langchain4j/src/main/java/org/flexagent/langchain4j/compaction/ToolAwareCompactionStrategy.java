package org.flexagent.langchain4j.compaction;

import org.flexagent.core.memory.compaction.CompactionStrategy;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

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
    public List<ChatMessage> compact(List<ChatMessage> messages) {
        if (messages == null || messages.size() <= maxMessages) {
            return messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        }

        List<ChatMessage> result = new ArrayList<>();
        List<ChatMessage> systems = new ArrayList<>();
        List<ChatMessage> toolMessages = new ArrayList<>();
        List<ChatMessage> others = new ArrayList<>();

        for (ChatMessage msg : messages) {
            if (msg instanceof SystemMessage) {
                systems.add(msg);
            } else if (msg instanceof ToolExecutionResultMessage) {
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
