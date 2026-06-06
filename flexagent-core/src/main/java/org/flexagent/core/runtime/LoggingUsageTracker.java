package org.flexagent.core.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple implementation of UsageTracker that logs token consumption.
 */
public class LoggingUsageTracker implements UsageTracker {
    private static final Logger log = LoggerFactory.getLogger(LoggingUsageTracker.class);

    @Override
    public void recordUsage(String sessionId, String modelName, int inputTokens, int outputTokens) {
        log.info("[UsageTracker] Session: {}, Model: {}, Input: {}, Output: {}, Total: {}",
                sessionId, modelName, inputTokens, outputTokens, (inputTokens + outputTokens));
    }
}
