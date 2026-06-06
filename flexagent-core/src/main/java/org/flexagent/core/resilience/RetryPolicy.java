package org.flexagent.core.resilience;

import java.util.concurrent.Callable;

/**
 * Defines a policy for retrying failed operations.
 */
public interface RetryPolicy {
    
    /**
     * Executes the given operation with retry logic.
     *
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws Exception if all retries fail
     */
    <T> T execute(Callable<T> operation) throws Exception;
}
