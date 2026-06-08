package org.flexagent.core.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flexagent.core.util.FlexObjectMapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class RedisAgentMemory implements AgentMemory {
    private final JedisPool jedisPool;
    private final Duration ttl;
    private final String keyPrefix;
    private final ObjectMapper objectMapper = FlexObjectMapper.getInstance();

    public RedisAgentMemory(JedisPool jedisPool) {
        this(jedisPool, null, "flexagent:memory:session:");
    }

    public RedisAgentMemory(JedisPool jedisPool, Duration ttl) {
        this(jedisPool, ttl, "flexagent:memory:session:");
    }

    public RedisAgentMemory(JedisPool jedisPool, Duration ttl, String keyPrefix) {
        this.jedisPool = jedisPool;
        this.ttl = ttl;
        this.keyPrefix = keyPrefix;
    }

    private String getRedisKey(String sessionId) {
        return keyPrefix + sessionId;
    }

    @Override
    public List<AgentMessage> getMessages(String sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        String key = getRedisKey(sessionId);
        try (Jedis jedis = jedisPool.getResource()) {
            List<String> rawMessages = jedis.lrange(key, 0, -1);
            List<AgentMessage> messages = new ArrayList<>();
            if (rawMessages != null) {
                for (String raw : rawMessages) {
                    try {
                        messages.add(objectMapper.readValue(raw, AgentMessage.class));
                    } catch (Exception e) {
                        // Skip malformed entries and log if necessary
                    }
                }
            }
            if (ttl != null && !messages.isEmpty()) {
                jedis.expire(key, ttl.getSeconds());
            }
            return messages;
        }
    }

    @Override
    public void addMessage(String sessionId, AgentMessage message) {
        if (sessionId == null || message == null) {
            return;
        }
        String key = getRedisKey(sessionId);
        try (Jedis jedis = jedisPool.getResource()) {
            String raw = objectMapper.writeValueAsString(message);
            jedis.rpush(key, raw);
            if (ttl != null) {
                jedis.expire(key, ttl.getSeconds());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to add message to Redis for session " + sessionId, e);
        }
    }

    @Override
    public void addMessages(String sessionId, List<AgentMessage> messages) {
        if (sessionId == null || messages == null || messages.isEmpty()) {
            return;
        }
        String key = getRedisKey(sessionId);
        try (Jedis jedis = jedisPool.getResource()) {
            String[] raws = new String[messages.size()];
            for (int i = 0; i < messages.size(); i++) {
                raws[i] = objectMapper.writeValueAsString(messages.get(i));
            }
            jedis.rpush(key, raws);
            if (ttl != null) {
                jedis.expire(key, ttl.getSeconds());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to add messages bulk to Redis for session " + sessionId, e);
        }
    }

    @Override
    public void clear(String sessionId) {
        if (sessionId == null) {
            return;
        }
        String key = getRedisKey(sessionId);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
        }
    }

    @Override
    public void close() throws Exception {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
}
