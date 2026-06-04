package org.flexagent.spring.boot.autoconfigure;

import org.flexagent.core.model.ThinkingMode;
import org.flexagent.core.model.ToolCallPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "flexagent")
public class FlexAgentProperties {
    /**
     * The type of runtime to use: e.g., "langchain4j", "localharness".
     */
    private String runtime;

    /**
     * Memory configuration.
     */
    private final MemoryProperties memory = new MemoryProperties();

    /**
     * Path to the localharness binary (only applicable for localharness runtime).
     */
    private String binaryPath;

    /**
     * Directory for storage/sessions.
     */
    private String storageDirectory;

    /**
     * Model name override.
     */
    private String modelName = "gemini-3.5-flash";

    /**
     * Thinking level (e.g., "high", "low").
     */
    private String thinkingLevel = "high";

    /**
     * System instructions or persona prompts.
     */
    private String systemInstruction;

    /**
     * Thinking mode (NONE, XML_THINK_TAG).
     */
    private ThinkingMode thinkingMode = ThinkingMode.NONE;

    /**
     * Tool call resolution policy (STRICT, LENIENT, TEXT_FALLBACK).
     */
    private ToolCallPolicy toolCallPolicy = ToolCallPolicy.LENIENT;

    public String getRuntime() {
        return runtime;
    }

    public void setRuntime(String runtime) {
        this.runtime = runtime;
    }

    public String getBinaryPath() {
        return binaryPath;
    }

    public void setBinaryPath(String binaryPath) {
        this.binaryPath = binaryPath;
    }

    public String getStorageDirectory() {
        return storageDirectory;
    }

    public void setStorageDirectory(String storageDirectory) {
        this.storageDirectory = storageDirectory;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getThinkingLevel() {
        return thinkingLevel;
    }

    public void setThinkingLevel(String thinkingLevel) {
        this.thinkingLevel = thinkingLevel;
    }

    public String getSystemInstruction() {
        return systemInstruction;
    }

    public void setSystemInstruction(String systemInstruction) {
        this.systemInstruction = systemInstruction;
    }

    public ThinkingMode getThinkingMode() {
        return thinkingMode;
    }

    public void setThinkingMode(ThinkingMode thinkingMode) {
        this.thinkingMode = thinkingMode;
    }

    public ToolCallPolicy getToolCallPolicy() {
        return toolCallPolicy;
    }

    public void setToolCallPolicy(ToolCallPolicy toolCallPolicy) {
        this.toolCallPolicy = toolCallPolicy;
    }

    public MemoryProperties getMemory() {
        return memory;
    }

    public static class MemoryProperties {
        /**
         * Memory type: "in-memory" or "redis".
         */
        private String type = "in-memory";

        /**
         * Session TTL. E.g., "30m" for 30 minutes, "1h" for 1 hour.
         * If null, sessions do not expire.
         */
        private Duration ttl;

        private final RedisProperties redis = new RedisProperties();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public RedisProperties getRedis() {
            return redis;
        }

        public static class RedisProperties {
            private String host = "localhost";
            private int port = 6379;
            private String password;
            private int database = 0;
            private int timeout = 2000; // ms

            public String getHost() {
                return host;
            }

            public void setHost(String host) {
                this.host = host;
            }

            public int getPort() {
                return port;
            }

            public void setPort(int port) {
                this.port = port;
            }

            public String getPassword() {
                return password;
            }

            public void setPassword(String password) {
                this.password = password;
            }

            public int getDatabase() {
                return database;
            }

            public void setDatabase(int database) {
                this.database = database;
            }

            public int getTimeout() {
                return timeout;
            }

            public void setTimeout(int timeout) {
                this.timeout = timeout;
            }
        }
    }
}
