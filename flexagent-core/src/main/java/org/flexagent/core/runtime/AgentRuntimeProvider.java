package org.flexagent.core.runtime;

public interface AgentRuntimeProvider {
    /**
     * Checks if this provider supports the given runtime backend type.
     *
     * @param type "localharness" or "langchain4j"
     * @return true if supported
     */
    boolean supports(String type);

    /**
     * Creates an instance of the AgentRuntime.
     *
     * @param config runtime configuration properties
     * @return AgentRuntime instance
     */
    AgentRuntime create(AgentRuntimeConfig config);
}
