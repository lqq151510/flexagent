package org.flexagent.core.memory.compaction;

import org.flexagent.core.memory.AgentMessage;
import java.util.List;

/**
 * Base strategy with configurable message/token thresholds.
 */
public abstract class ThresholdCompactionStrategy implements CompactionStrategy<AgentMessage> {
    private final Integer messageThreshold;
    private final Integer tokenThreshold;

    protected ThresholdCompactionStrategy(Integer messageThreshold, Integer tokenThreshold) {
        if (messageThreshold == null && tokenThreshold == null) {
            throw new IllegalArgumentException("At least one threshold (message or token) must be configured.");
        }
        if (messageThreshold != null && messageThreshold <= 0) {
            throw new IllegalArgumentException("messageThreshold must be > 0");
        }
        if (tokenThreshold != null && tokenThreshold <= 0) {
            throw new IllegalArgumentException("tokenThreshold must be > 0");
        }
        this.messageThreshold = messageThreshold;
        this.tokenThreshold = tokenThreshold;
    }

    @Override
    public boolean shouldCompact(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        int messageCount = messages.size();
        int tokenCount = estimateTokenCount(messages);
        boolean messageExceeded = messageThreshold != null && messageCount > messageThreshold;
        boolean tokenExceeded = tokenThreshold != null && tokenCount > tokenThreshold;
        return messageExceeded || tokenExceeded;
    }

    @Override
    public String compactionReason(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "empty-context";
        }
        int messageCount = messages.size();
        int tokenCount = estimateTokenCount(messages);
        boolean messageExceeded = messageThreshold != null && messageCount > messageThreshold;
        boolean tokenExceeded = tokenThreshold != null && tokenCount > tokenThreshold;
        if (messageExceeded && tokenExceeded) {
            return "message-threshold+token-threshold";
        }
        if (messageExceeded) {
            return "message-threshold";
        }
        if (tokenExceeded) {
            return "token-threshold";
        }
        return "below-threshold";
    }

    protected Integer messageThreshold() {
        return messageThreshold;
    }

    protected Integer tokenThreshold() {
        return tokenThreshold;
    }

    @Override
    public int estimateTokenCount(List<AgentMessage> messages) {
        if (messages == null) return 0;
        int count = 0;
        for (AgentMessage message : messages) {
            String text = message.text();
            if (text == null) text = "";
            count += Math.max(1, (int) Math.ceil(text.length() / 4.0));
        }
        return count;
    }
}
