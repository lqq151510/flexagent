package org.flexagent.langchain4j.compaction;

import dev.langchain4j.data.message.ChatMessage;
import org.flexagent.core.memory.compaction.CompactionStrategy;

import java.util.List;

public class NoopCompactionStrategy implements CompactionStrategy<ChatMessage> {
    @Override
    public List<ChatMessage> compact(List<ChatMessage> messages) {
        return messages;
    }
}
