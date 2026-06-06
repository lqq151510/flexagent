package org.flexagent.core.resilience;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RetryPolicyTest {

    @Test
    public void testSuccessfulExecutionWithoutRetry() throws Exception {
        RetryPolicy policy = new SimpleRetryPolicy(3, 10);
        String result = policy.execute(() -> "Success");
        assertEquals("Success", result);
    }

    @Test
    public void testSuccessfulExecutionAfterRetries() throws Exception {
        RetryPolicy policy = new SimpleRetryPolicy(3, 10);
        
        Callable<String> operation = new Callable<>() {
            int attempts = 0;
            @Override
            public String call() throws Exception {
                attempts++;
                if (attempts < 3) {
                    throw new RuntimeException("Temporary failure");
                }
                return "Success on attempt " + attempts;
            }
        };

        String result = policy.execute(operation);
        assertEquals("Success on attempt 3", result);
    }

    @Test
    public void testFailureAfterMaxRetries() {
        RetryPolicy policy = new SimpleRetryPolicy(2, 10);
        
        Callable<String> operation = () -> {
            throw new RuntimeException("Permanent failure");
        };

        assertThrows(RuntimeException.class, () -> {
            policy.execute(operation);
        });
    }
}
