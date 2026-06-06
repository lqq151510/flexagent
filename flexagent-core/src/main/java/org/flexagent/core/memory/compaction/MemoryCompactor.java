package org.flexagent.core.memory.compaction;

import org.flexagent.core.memory.AgentMessage;
import java.util.List;

/**
 * Interface for memory compaction strategies, such as summarization.
 */
public interface MemoryCompactor {
    
    /**
     * Compacts the provided history of messages into a smaller, summarized list of messages.
     *
     * @param history the original message history
     * @return the compacted message history
     */
    List<AgentMessage> compact(List<AgentMessage> history);
}
