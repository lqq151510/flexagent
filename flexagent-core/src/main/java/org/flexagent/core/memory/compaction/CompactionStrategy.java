package org.flexagent.core.memory.compaction;

import java.util.List;

/**
 * Generic interface for compaction strategies, applicable across different runtimes (Langchain4j, Spring AI).
 * @param <M> The message type for the specific runtime.
 */
public interface CompactionStrategy<M> {
    /**
     * Compacts the message context list.
     *
     * @param messages The original messages.
     * @return The compacted messages.
     */
    List<M> compact(List<M> messages);

    /**
     * Indicates whether the current context should be compacted.
     *
     * @param messages The current messages.
     * @return true if compaction should run, otherwise false.
     */
    default boolean shouldCompact(List<M> messages) {
        return true;
    }

    /**
     * Returns the reason for compaction decision.
     */
    default String compactionReason(List<M> messages) {
        return "no-threshold";
    }

    /**
     * Returns an estimated token count for current context.
     */
    default int estimateTokenCount(List<M> messages) {
        return 0; // Default implementation, meant to be overridden or injected via an estimator.
    }
}
