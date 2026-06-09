package org.flexagent.enterprise;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SseConcurrencyStressTest {

    @LocalServerPort
    private int port;

    @SpringBootApplication
    static class SseServer {
        public static void main(String[] args) {
            SpringApplication.run(SseServer.class, args);
        }

        @RestController
        static class AgentSseController {
            // Simulates an LLM agent streaming out tokens
            @GetMapping("/api/agent/stream")
            public Flux<ServerSentEvent<String>> streamAgentResponse() {
                return Flux.interval(Duration.ofMillis(50))
                           .map(seq -> ServerSentEvent.<String>builder()
                                   .id(String.valueOf(seq))
                                   .event("token")
                                   .data("Generated_Token_" + seq)
                                   .build())
                           .take(20); // Simulates 20 tokens per response
            }
        }
    }

    @Test
    public void testHighConcurrencySseConnections() throws InterruptedException {
        // Here we simulate 1000 concurrent SSE clients connecting to the agent's endpoint
        int numClients = 1000;
        WebClient client = WebClient.builder().baseUrl("http://localhost:" + port).build();
        CountDownLatch latch = new CountDownLatch(numClients);
        List<Throwable> errors = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numClients; i++) {
            client.get()
                  .uri("/api/agent/stream")
                  .retrieve()
                  .bodyToFlux(ServerSentEvent.class)
                  // Wait until all 20 tokens are received for this client
                  .then() 
                  .subscribe(
                          success -> latch.countDown(),
                          error -> {
                              synchronized (errors) { errors.add(error); }
                              latch.countDown();
                          }
                  );
        }

        // Wait for all clients to finish streaming
        latch.await();
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.println("Time taken to process 1000 concurrent SSE streams (each taking ~1s): " + duration + "ms");
        
        // Assert no connection errors or reactor pool exhaustion
        assertTrue(errors.isEmpty(), "There were connection errors during the stress test: " + errors.size());
        
        // Under Netty/WebFlux, this should finish in just slightly over 1 second (1000ms), 
        // as all 1000 connections are handled asynchronously without blocking 1000 OS threads.
        assertTrue(duration < 5000, "Should handle 1000 concurrent streams efficiently without huge delays");
    }
}
