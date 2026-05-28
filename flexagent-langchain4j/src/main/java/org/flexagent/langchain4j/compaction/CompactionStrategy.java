package org.flexagent.langchain4j.compaction;

import dev.langchain4j.data.message.ChatMessage;
import java.util.List;

public interface CompactionStrategy {
    /**
     * Compacts the message context list.
     *
     * @param messages The original messages.
     * @return The compacted messages.
     */
    List<ChatMessage> compact(List<ChatMessage> messages);

    /**
     * Indicates whether the current context should be compacted.
     *
     * @param messages The current messages.
     * @return true if compaction should run, otherwise false.
     */
    default boolean shouldCompact(List<ChatMessage> messages) {
        return false;
    }

    /**
     * Returns the reason for compaction decision.
     */
    default String compactionReason(List<ChatMessage> messages) {
        return "no-threshold";
    }

    /**
     * Returns an estimated token count for current context.
     */
    default int estimateTokenCount(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int chars = 0;
        for (ChatMessage message : messages) {
            if (message != null && message.text() != null) {
                chars += message.text().length();
            }
        }
        return Math.max(1, (int) Math.ceil(chars / 4.0));
    }
}
