package org.flexagent.core.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.Callable;

/**
 * A basic implementation of RetryPolicy with exponential backoff.
 */
public class SimpleRetryPolicy implements RetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(SimpleRetryPolicy.class);

    private final int maxRetries;
    private final long initialBackoffMs;

    public SimpleRetryPolicy(int maxRetries, long initialBackoffMs) {
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
    }

    @Override
    public <T> T execute(Callable<T> operation) throws Exception {
        int attempt = 0;
        long backoff = initialBackoffMs;
        
        while (true) {
            try {
                return operation.call();
            } catch (Exception e) {
                attempt++;
                if (attempt > maxRetries) {
                    log.error("Operation failed after {} retries. Last error: {}", maxRetries, e.getMessage());
                    throw e;
                }
                log.warn("Operation failed, retrying (attempt {}/{}). Error: {}", attempt, maxRetries, e.getMessage());
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
                backoff *= 2; // Exponential backoff
            }
        }
    }
}
