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
}
