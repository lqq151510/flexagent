package org.flexagent.core.memory;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AgentMemoryTest {

    @Test
    public void testInMemoryBasicAndIsolation() {
        InMemoryAgentMemory memory = new InMemoryAgentMemory();

        // 1. Session Isolation
        memory.addMessage("session-A", AgentMessage.user("Hello A"));
        memory.addMessage("session-B", AgentMessage.user("Hello B"));

        List<AgentMessage> messagesA = memory.getMessages("session-A");
        assertEquals(1, messagesA.size());
        assertEquals("Hello A", messagesA.get(0).text());
        assertThrows(UnsupportedOperationException.class, () -> messagesA.add(AgentMessage.user("Mutate")));
        assertEquals(1, memory.getMessages("session-A").size());

        List<AgentMessage> messagesB = memory.getMessages("session-B");
        assertEquals(1, messagesB.size());
        assertEquals("Hello B", messagesB.get(0).text());

        // 2. Clear Session
        memory.clear("session-A");
        assertTrue(memory.getMessages("session-A").isEmpty());
        assertFalse(memory.getMessages("session-B").isEmpty());
    }

    @Test
    public void testInMemoryTtlLazyExpiration() throws InterruptedException {
        // TTL = 100ms
        InMemoryAgentMemory memory = new InMemoryAgentMemory(Duration.ofMillis(100));

        memory.addMessage("session-ttl", AgentMessage.user("Hello TTL"));
        assertFalse(memory.getMessages("session-ttl").isEmpty());

        // Wait for expiration
        Thread.sleep(150);

        // Get should lazily delete it and return empty
        assertTrue(memory.getMessages("session-ttl").isEmpty());
    }

    @Test
    public void testInMemoryConcurrency() throws InterruptedException {
        InMemoryAgentMemory memory = new InMemoryAgentMemory();
        int threadCount = 10;
        int messagesPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < messagesPerThread; j++) {
                        memory.addMessage("session-concurrent", AgentMessage.user("msg-" + index + "-" + j));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        List<AgentMessage> messages = memory.getMessages("session-concurrent");
        assertEquals(threadCount * messagesPerThread, messages.size());
    }

    @Test
    public void testRedisMemoryMock() throws Exception {
        JedisPool mockPool = mock(JedisPool.class);
        Jedis mockJedis = mock(Jedis.class);
        when(mockPool.getResource()).thenReturn(mockJedis);

        RedisAgentMemory redisMemory = new RedisAgentMemory(mockPool, Duration.ofMinutes(30));

        // Mock getMessages
        when(mockJedis.lrange("flexagent:memory:session:session-1", 0, -1))
                .thenReturn(List.of("{\"role\":\"user\",\"text\":\"Hello Redis\",\"toolCalls\":null,\"toolId\":null,\"toolName\":null}"));

        List<AgentMessage> msgs = redisMemory.getMessages("session-1");
        assertEquals(1, msgs.size());
        assertEquals("Hello Redis", msgs.get(0).text());
        verify(mockJedis).expire("flexagent:memory:session:session-1", 1800);

        // Mock addMessage
        redisMemory.addMessage("session-1", AgentMessage.user("Hello Add"));
        verify(mockJedis).rpush(eq("flexagent:memory:session:session-1"), anyString());
        verify(mockJedis, times(2)).expire("flexagent:memory:session:session-1", 1800);

        // Mock clear
        redisMemory.clear("session-1");
        verify(mockJedis).del("flexagent:memory:session:session-1");

        // Mock close
        redisMemory.close();
        verify(mockPool).close();
    }

    @Test
    public void testRedisMemoryIntegration() throws Exception {
        // Attempt to connect to local Redis on default port
        boolean localRedisAvailable = false;
        try (Jedis jedis = new Jedis("localhost", 6379)) {
            jedis.ping();
            localRedisAvailable = true;
        } catch (Exception e) {
            System.out.println("Local physical Redis not available. Skipping Redis integration test.");
        }

        if (!localRedisAvailable) {
            return; // Skip integration test
        }

        try (JedisPool jedisPool = new JedisPool("localhost", 6379)) {
            RedisAgentMemory redisMemory = new RedisAgentMemory(jedisPool, Duration.ofSeconds(2));

            // Clean up left-over from previous runs
            redisMemory.clear("session-integration");

            // Write session data
            redisMemory.addMessage("session-integration", AgentMessage.user("Hello Integration"));
            redisMemory.addMessage("session-integration", AgentMessage.assistant("Hello Back"));

            // Verification of history
            List<AgentMessage> msgs = redisMemory.getMessages("session-integration");
            assertEquals(2, msgs.size());
            assertEquals("Hello Integration", msgs.get(0).text());
            assertEquals("Hello Back", msgs.get(1).text());

            // Test Session isolation
            redisMemory.addMessage("session-integration-other", AgentMessage.user("Other session msg"));
            List<AgentMessage> otherMsgs = redisMemory.getMessages("session-integration-other");
            assertEquals(1, otherMsgs.size());
            assertEquals("Other session msg", otherMsgs.get(0).text());

            // Check session recovery: close current memory instance, start new instance
            // since they share same Redis, history should be retrievable
            try (RedisAgentMemory recoveryMemory = new RedisAgentMemory(jedisPool, Duration.ofSeconds(2))) {
                List<AgentMessage> recovered = recoveryMemory.getMessages("session-integration");
                assertEquals(2, recovered.size());
            }

            // Test expiration
            try {
                Thread.sleep(2200);
            } catch (InterruptedException ignored) {}

            // After sleep, it should be expired and return empty
            assertTrue(redisMemory.getMessages("session-integration").isEmpty());

            redisMemory.clear("session-integration-other");
        }
    }
}
