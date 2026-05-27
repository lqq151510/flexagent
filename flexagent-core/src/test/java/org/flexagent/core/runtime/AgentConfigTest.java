package org.flexagent.core.runtime;

import org.flexagent.core.model.ThinkingMode;
import org.flexagent.core.model.ToolCallPolicy;
import org.flexagent.core.model.ToolDefinition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AgentConfigTest {

    @Test
    public void testConfigGettersSettersAndDefaults() {
        AgentConfig config = new AgentConfig();
        
        // Check defaults
        assertEquals("gemini-3.5-flash", config.getModelName());
        assertEquals("high", config.getThinkingLevel());
        assertEquals(ThinkingMode.NONE, config.getThinkingMode());
        assertEquals(ToolCallPolicy.LENIENT, config.getToolCallPolicy());
        assertTrue(config.getToolObjects().isEmpty());
        assertTrue(config.getTools().isEmpty());

        // Modify values
        config.setBinaryPath("/path/to/bin");
        config.setStorageDirectory("/path/to/storage");
        config.setModelName("custom-model");
        config.setThinkingLevel("low");
        config.setSystemInstruction("You are a helpful assistant");
        config.setThinkingMode(ThinkingMode.XML_THINK_TAG);
        config.setToolCallPolicy(ToolCallPolicy.STRICT);

        // Verify updated values
        assertEquals("/path/to/bin", config.getBinaryPath());
        assertEquals("/path/to/storage", config.getStorageDirectory());
        assertEquals("custom-model", config.getModelName());
        assertEquals("low", config.getThinkingLevel());
        assertEquals("You are a helpful assistant", config.getSystemInstruction());
        assertEquals(ThinkingMode.XML_THINK_TAG, config.getThinkingMode());
        assertEquals(ToolCallPolicy.STRICT, config.getToolCallPolicy());
    }

    @Test
    public void testToolAddition() {
        AgentConfig config = new AgentConfig();
        
        Object myToolObj = new Object();
        config.addToolObject(myToolObj);
        assertEquals(1, config.getToolObjects().size());
        assertEquals(myToolObj, config.getToolObjects().get(0));

        ToolDefinition def = new ToolDefinition("toolName", "description", "{}");
        config.addTool(def);
        assertEquals(1, config.getTools().size());
        assertEquals(def, config.getTools().get(0));

        // Adding nulls shouldn't break or add items
        config.addToolObject(null);
        config.addTool(null);
        assertEquals(1, config.getToolObjects().size());
        assertEquals(1, config.getTools().size());
    }
}
