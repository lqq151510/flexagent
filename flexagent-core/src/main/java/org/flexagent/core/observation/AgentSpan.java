package org.flexagent.core.observation;

/**
 * Represents a logical span of execution for an agent or tool.
 */
public interface AgentSpan extends AutoCloseable {
    void tag(String key, String value);
    void error(Throwable throwable);
    void end();

    @Override
    default void close() {
        end();
    }
}
