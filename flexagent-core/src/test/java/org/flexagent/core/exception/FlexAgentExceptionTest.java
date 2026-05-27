package org.flexagent.core.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FlexAgentExceptionTest {

    @Test
    public void testFlexAgentExceptionInheritance() {
        FlexAgentException base = new FlexAgentException("base error");
        assertTrue(base instanceof RuntimeException);
        assertEquals("base error", base.getMessage());

        Throwable cause = new IllegalArgumentException("invalid argument");
        FlexAgentException baseWithCause = new FlexAgentException("base error with cause", cause);
        assertEquals(cause, baseWithCause.getCause());
    }

    @Test
    public void testProviderNotFoundException() {
        ProviderNotFoundException ex1 = new ProviderNotFoundException("test-runtime");
        assertTrue(ex1 instanceof FlexAgentException);
        assertTrue(ex1.getMessage().contains("No AgentRuntimeProvider found"));
        assertTrue(ex1.getMessage().contains("test-runtime"));

        ProviderNotFoundException ex2 = new ProviderNotFoundException("test-runtime", 2);
        assertTrue(ex2.getMessage().contains("Multiple AgentRuntimeProviders"));
        assertTrue(ex2.getMessage().contains("2 matches"));
    }

    @Test
    public void testRuntimeInitializationException() {
        RuntimeInitializationException ex = new RuntimeInitializationException("test-runtime", "connection failed");
        assertTrue(ex instanceof FlexAgentException);
        assertTrue(ex.getMessage().contains("Failed to initialize AgentRuntime"));
        assertTrue(ex.getMessage().contains("test-runtime"));
        assertTrue(ex.getMessage().contains("connection failed"));
    }

    @Test
    public void testToolInvocationException() {
        Throwable cause = new NullPointerException("npe");
        ToolInvocationException ex = new ToolInvocationException("my-tool", "reflection error", cause);
        assertTrue(ex instanceof FlexAgentException);
        assertTrue(ex.getMessage().contains("Error executing tool"));
        assertTrue(ex.getMessage().contains("my-tool"));
        assertEquals(cause, ex.getCause());
    }
}
