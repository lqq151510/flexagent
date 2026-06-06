package org.flexagent.core.runtime;

/**
 * Interface for tracking API usage and costs (e.g. LLM tokens).
 */
public interface UsageTracker {
    /**
     * Records token usage.
     *
     * @param sessionId the session identifier
     * @param modelName the model name or provider
     * @param inputTokens number of input (prompt) tokens
     * @param outputTokens number of output (completion) tokens
     */
    void recordUsage(String sessionId, String modelName, int inputTokens, int outputTokens);
}
