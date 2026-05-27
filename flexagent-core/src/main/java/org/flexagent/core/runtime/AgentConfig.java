package org.flexagent.core.runtime;

import java.util.ArrayList;
import java.util.List;

public class AgentConfig {
    private String binaryPath;
    private String storageDirectory;
    private String modelName = "gemini-3.5-flash";
    private String thinkingLevel = "high";
    private String systemInstruction;
    private final List<Object> toolObjects = new ArrayList<>();

    private final List<org.flexagent.core.model.ToolDefinition> tools = new ArrayList<>();

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

    public List<Object> getToolObjects() {
        return toolObjects;
    }

    public void addToolObject(Object toolObject) {
        if (toolObject != null) {
            this.toolObjects.add(toolObject);
        }
    }

    public List<org.flexagent.core.model.ToolDefinition> getTools() {
        return tools;
    }

    public void addTool(org.flexagent.core.model.ToolDefinition tool) {
        if (tool != null) {
            this.tools.add(tool);
        }
    }

    private org.flexagent.core.model.ThinkingMode thinkingMode = org.flexagent.core.model.ThinkingMode.NONE;
    private org.flexagent.core.model.ToolCallPolicy toolCallPolicy = org.flexagent.core.model.ToolCallPolicy.LENIENT;

    public org.flexagent.core.model.ThinkingMode getThinkingMode() {
        return thinkingMode;
    }

    public void setThinkingMode(org.flexagent.core.model.ThinkingMode thinkingMode) {
        this.thinkingMode = thinkingMode;
    }

    public org.flexagent.core.model.ToolCallPolicy getToolCallPolicy() {
        return toolCallPolicy;
    }

    public void setToolCallPolicy(org.flexagent.core.model.ToolCallPolicy toolCallPolicy) {
        this.toolCallPolicy = toolCallPolicy;
    }
}

