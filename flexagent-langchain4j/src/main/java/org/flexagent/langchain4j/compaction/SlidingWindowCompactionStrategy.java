package org.flexagent.langchain4j.compaction;

import org.flexagent.core.memory.compaction.CompactionStrategy;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
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
    public List<ChatMessage> compact(List<ChatMessage> messages) {
        if (messages == null || messages.size() <= maxMessages) {
            return messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        }

        List<ChatMessage> result = new ArrayList<>();
        List<ChatMessage> systems = new ArrayList<>();
        List<ChatMessage> others = new ArrayList<>();

        for (ChatMessage msg : messages) {
            if (msg instanceof SystemMessage) {
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
