package org.flexagent.core.observation;

/**
 * Registry to manage observations/spans for agents.
 */
public interface AgentObservationRegistry {
    AgentSpan startSpan(String name);

    /**
     * Default NoOp implementation
     */
    AgentObservationRegistry NOOP = name -> new AgentSpan() {
        @Override public void tag(String key, String value) {}
        @Override public void error(Throwable throwable) {}
        @Override public void end() {}
    };
}
