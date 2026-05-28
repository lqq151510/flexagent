package org.flexagent.langchain4j.compaction;

import dev.langchain4j.data.message.ChatMessage;
import java.util.ArrayList;
import java.util.List;

public class NoopCompactionStrategy implements CompactionStrategy {
    @Override
    public List<ChatMessage> compact(List<ChatMessage> messages) {
        if (messages == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(messages);
    }

    @Override
    public boolean shouldCompact(List<ChatMessage> messages) {
        return false;
    }
}
