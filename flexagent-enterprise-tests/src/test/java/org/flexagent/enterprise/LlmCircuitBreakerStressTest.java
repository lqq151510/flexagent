package org.flexagent.enterprise;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.flexagent.core.memory.AgentMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LlmCircuitBreakerStressTest {

    private final AtomicInteger deepSeekCalls = new AtomicInteger(0);
    private final AtomicInteger qwenCalls = new AtomicInteger(0);

    // Mock an unstable cloud API like DeepSeek which fails 80% of the time under high load
    private AgentMessage callDeepSeekCloudApi() {
        deepSeekCalls.incrementAndGet();
        if (Math.random() < 0.8) {
            throw new RuntimeException("HTTP 429 Too Many Requests from DeepSeek");
        }
        return AgentMessage.assistant("DeepSeek: Success");
    }

    // Mock a local fallback model like Qwen3.5
    private AgentMessage callLocalQwenFallback(Throwable t) {
        qwenCalls.incrementAndGet();
        return AgentMessage.assistant("Qwen Local: Recovered from " + t.getMessage());
    }

    @Test
    public void testHeterogeneousModelFallback() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // Open circuit if 50% requests fail
                .slidingWindowSize(10) // Window size of 10 requests
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("LLM", config);

        // Decorate the DeepSeek call
        Supplier<AgentMessage> decoratedDeepSeek = CircuitBreaker
                .decorateSupplier(circuitBreaker, this::callDeepSeekCloudApi);

        int totalRequests = 100;

        for (int i = 0; i < totalRequests; i++) {
            AgentMessage result;
            try {
                result = decoratedDeepSeek.get();
            } catch (Exception e) {
                // If it fails (either real 429 or CircuitBreakerOpenException), route to Qwen
                result = callLocalQwenFallback(e);
            }
            
            // System.out.println(result.content());
        }

        // Verify that circuit breaker tripped and prevented all 100 requests going to DeepSeek
        int cloudCalls = deepSeekCalls.get();
        int localCalls = qwenCalls.get();
        
        System.out.println("DeepSeek actual cloud calls: " + cloudCalls);
        System.out.println("Qwen local fallback calls: " + localCalls);

        // Since it fails 80% of the time, the circuit breaker should trip within the first 10 calls.
        // It should drop fast and block the rest of the 90 requests!
        assertTrue(cloudCalls < 30, "Circuit breaker didn't open fast enough. Cloud calls: " + cloudCalls);
        assertTrue(localCalls > 70, "Fallback to local model didn't happen for most requests");
        assertEquals(totalRequests, cloudCalls + localCalls - deepSeekCalls.get() /* some successes */, 
                     "Some requests were lost!"); // Rough metric check
    }
}
