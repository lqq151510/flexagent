package org.flexagent.core.runtime;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AgentRuntimeConfigTest {

    @Test
    public void testConfigCreationAndGetters() {
        Map<String, Object> options = new HashMap<>();
        options.put("key1", "value1");
        options.put("key2", 123);
        
        AgentRuntimeConfig config = new AgentRuntimeConfig("test-type", "mock-model", options);
        
        assertEquals("test-type", config.type());
        assertEquals("mock-model", config.model());
        assertEquals(2, config.options().size());
        assertEquals("value1", config.option("key1", String.class));
        assertEquals(123, config.option("key2", Integer.class));
    }

    @Test
    public void testNullModelAndCasting() {
        AgentRuntimeConfig config = new AgentRuntimeConfig("test-type", null, null);
        assertNull(config.model());
        assertNull(config.model(String.class));
        assertTrue(config.options().isEmpty());
    }

    @Test
    public void testModelSafeCasting() {
        String testModel = "my-test-model";
        AgentRuntimeConfig config = new AgentRuntimeConfig("test-type", testModel, null);
        
        String casted = config.model(String.class);
        assertEquals(testModel, casted);
        
        assertThrows(ClassCastException.class, () -> {
            config.model(Integer.class);
        });
    }

    @Test
    public void testNullOption() {
        AgentRuntimeConfig config = new AgentRuntimeConfig("test-type", null, null);
        assertNull(config.option("non-existent", String.class));
    }
}
