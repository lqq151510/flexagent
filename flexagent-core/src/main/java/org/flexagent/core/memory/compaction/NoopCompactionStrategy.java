package org.flexagent.core.memory.compaction;

import org.flexagent.core.memory.AgentMessage;
import java.util.ArrayList;
import java.util.List;

public class NoopCompactionStrategy implements CompactionStrategy<AgentMessage> {
    @Override
    public boolean shouldCompact(List<AgentMessage> messages) {
        return false;
    }

    @Override
    public List<AgentMessage> compact(List<AgentMessage> messages) {
        return messages != null ? new ArrayList<>(messages) : new ArrayList<>();
    }
}
