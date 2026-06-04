package org.flexagent.core.memory.compaction;

/**
 * Interface to inspect the runtime-specific message object `<M>`.
 */
public interface MessageInspector<M> {

    /**
     * Estimates token count for this message.
     */
    int estimateTokenCount(M message);

    /**
     * Is this a system message?
     */
    boolean isSystemMessage(M message);

    /**
     * Is this a tool execution request message? (e.g., assistant invoking a tool)
     */
    boolean isToolRequestMessage(M message);

    /**
     * Is this a tool execution result message? (e.g., tool response)
     */
    boolean isToolResultMessage(M message);

    /**
     * Does the tool request message `request` match the tool result message `result`?
     * Return true if they are paired in the context of the underlying runtime.
     * If the runtime groups them by ID, this checks the ID.
     */
    boolean isMatchingToolPair(M request, M result);
}
