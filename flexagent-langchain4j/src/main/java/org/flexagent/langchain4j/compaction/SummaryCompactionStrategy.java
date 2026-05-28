package org.flexagent.langchain4j.compaction;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

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
    public List<ChatMessage> compact(List<ChatMessage> messages) {
        if (messages == null || messages.size() <= maxMessages) {
            return messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        }

        List<ChatMessage> result = new ArrayList<>();
        List<ChatMessage> systems = new ArrayList<>();
        List<ChatMessage> tail = new ArrayList<>();

        for (ChatMessage msg : messages) {
            if (msg instanceof SystemMessage) {
                systems.add(msg);
            }
        }

        int tailSlots = Math.max(minTailMessages, maxMessages - systems.size() - 1);
        int startIdx = Math.max(0, messages.size() - tailSlots);
        for (int i = startIdx; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (!(msg instanceof SystemMessage)) {
                tail.add(msg);
            }
        }

        result.addAll(systems);
        result.add(SystemMessage.from(buildSummary(messages, startIdx)));
        result.addAll(tail);
        return result;
    }

    private String buildSummary(List<ChatMessage> messages, int startIdx) {
        StringBuilder summary = new StringBuilder("Conversation summary of earlier context: ");
        boolean first = true;
        for (int i = 0; i < startIdx; i++) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof SystemMessage) {
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

    private String label(ChatMessage msg) {
        if (msg instanceof UserMessage) {
            return "User";
        }
        if (msg instanceof AiMessage) {
            return "Assistant";
        }
        if (msg instanceof ToolExecutionResultMessage) {
            return "ToolResult";
        }
        return msg.getClass().getSimpleName();
    }

    private String shortText(ChatMessage msg) {
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
