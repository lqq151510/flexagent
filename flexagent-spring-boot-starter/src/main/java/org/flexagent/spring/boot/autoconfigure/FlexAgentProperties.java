package org.flexagent.spring.boot.autoconfigure;

import org.flexagent.core.model.ThinkingMode;
import org.flexagent.core.model.ToolCallPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flexagent")
public class FlexAgentProperties {
    /**
     * The type of runtime to use: e.g., "langchain4j", "localharness".
     */
    private String runtime;

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
}
