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
}
