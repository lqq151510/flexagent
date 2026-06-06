package org.flexagent.core.memory;

import java.util.List;

/**
 * Defines the contract for an agent's memory storage.
 * Memory stores the conversational history (AgentMessage) across a session,
 * allowing the agent to recall previous context.
 */
public interface AgentMemory extends AutoCloseable {

    /**
     * Retrieves all messages for a given session.
     *
     * @param sessionId the unique identifier for the conversation session
     * @return a list of AgentMessage, ordered chronologically
     */
    List<AgentMessage> getMessages(String sessionId);

    /**
     * Appends a new message to the given session's memory.
     *
     * @param sessionId the unique identifier for the conversation session
     * @param message the AgentMessage to store
     */
    void addMessage(String sessionId, AgentMessage message);

    /**
     * Appends multiple messages to the given session's memory.
     *
     * @param sessionId the unique identifier for the conversation session
     * @param messages the list of AgentMessage to store
     */
    default void addMessages(String sessionId, List<AgentMessage> messages) {
        if (messages != null) {
            for (AgentMessage msg : messages) {
                addMessage(sessionId, msg);
            }
        }
    }

    /**
     * Clears all history for the given session.
     *
     * @param sessionId the unique identifier for the conversation session
     */
    void clear(String sessionId);

    /**
     * Closes any underlying resources (e.g. database connections).
     */
    @Override
    default void close() throws Exception {
        // default no-op
    }
}
