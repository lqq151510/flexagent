package org.flexagent.core.memory.compaction;

import org.flexagent.core.memory.AgentMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * A lightweight summary compaction strategy that preserves the latest conversation turns
 * while folding older content into a synthetic system summary.
 */
public class SummaryCompactionStrategy extends ThresholdCompactionStrategy {
    private final int maxMessages;
    private final int summaryMaxChars;
    private final int minTailMessages;

    public SummaryCompactionStrategy(int maxMessages) {
        this(maxMessages, maxMessages, null, 120, 2);
    }

    public SummaryCompactionStrategy(
            int maxMessages,
            Integer messageThreshold,
            Integer tokenThreshold,
            int summaryMaxChars,
            int minTailMessages
    ) {
        super(messageThreshold, tokenThreshold);
        if (maxMessages < 3) {
            throw new IllegalArgumentException("maxMessages must be at least 3");
        }
        if (summaryMaxChars < 40) {
            throw new IllegalArgumentException("summaryMaxChars must be at least 40");
        }
        if (minTailMessages < 1) {
            throw new IllegalArgumentException("minTailMessages must be at least 1");
        }
        this.maxMessages = maxMessages;
        this.summaryMaxChars = summaryMaxChars;
        this.minTailMessages = minTailMessages;
    }

    @Override
    public List<AgentMessage> compact(List<AgentMessage> messages) {
        if (messages == null || messages.size() <= maxMessages) {
            return messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        }

        List<AgentMessage> result = new ArrayList<>();
        List<AgentMessage> systems = new ArrayList<>();
        List<AgentMessage> tail = new ArrayList<>();

        for (AgentMessage msg : messages) {
            if ("system".equals(msg.role())) {
                systems.add(msg);
            }
        }

        int tailSlots = Math.max(minTailMessages, maxMessages - systems.size() - 1);
        int startIdx = Math.max(0, messages.size() - tailSlots);
        for (int i = startIdx; i < messages.size(); i++) {
            AgentMessage msg = messages.get(i);
            if (!"system".equals(msg.role())) {
                tail.add(msg);
            }
        }

        result.addAll(systems);
        result.add(AgentMessage.system(buildSummary(messages, startIdx)));
        result.addAll(tail);
        return result;
    }

    private String buildSummary(List<AgentMessage> messages, int startIdx) {
        StringBuilder summary = new StringBuilder("Conversation summary of earlier context: ");
        boolean first = true;
        for (int i = 0; i < startIdx; i++) {
            AgentMessage msg = messages.get(i);
            if ("system".equals(msg.role())) {
                continue;
            }
            if (!first) {
                summary.append(" | ");
            }
            first = false;
            summary.append(label(msg)).append(": ").append(shortText(msg));
        }
        if (first) {
            summary.append("No earlier user-visible turns.");
        }
        return summary.toString();
    }

    private String label(AgentMessage msg) {
        if ("user".equals(msg.role())) {
            return "User";
        }
        if ("assistant".equals(msg.role())) {
            return "Assistant";
        }
        if ("tool".equals(msg.role())) {
            return "ToolResult";
        }
        return msg.role();
    }

    private String shortText(AgentMessage msg) {
        String text = msg.text();
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > summaryMaxChars
                ? normalized.substring(0, summaryMaxChars) + "..."
                : normalized;
    }
}
