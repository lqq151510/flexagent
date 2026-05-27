package org.flexagent.langchain4j;

import org.flexagent.core.model.ToolCall;
import org.flexagent.core.model.ToolDefinition;
import org.flexagent.core.model.ToolResult;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ToolAdapterTest {

    public static class MockTools {
        @Tool("Mock addition tool")
        public int add(@P("num1") int a, @P("num2") int b) {
            return a + b;
        }

        @Tool("Mock greet tool")
        public String greet(@P("name") String name) {
            return "Hello, " + name;
        }
    }

    @Test
    public void testToolRegistration() {
        MockTools toolsObj = new MockTools();
        ToolAdapter adapter = new ToolAdapter(List.of(toolsObj));

        List<ToolDefinition> tools = adapter.getTools();
        assertEquals(2, tools.size());

        ToolDefinition addTool = tools.stream().filter(t -> t.name().equals("add")).findFirst().orElse(null);
        assertNotNull(addTool);
        assertEquals("Mock addition tool", addTool.description());

        ToolDefinition greetTool = tools.stream().filter(t -> t.name().equals("greet")).findFirst().orElse(null);
        assertNotNull(greetTool);
        assertEquals("Mock greet tool", greetTool.description());
    }

    @Test
    public void testToolExecutionSuccessful() {
        MockTools toolsObj = new MockTools();
        ToolAdapter adapter = new ToolAdapter(List.of(toolsObj));

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("num1", 5);
        arguments.put("num2", 7);

        ToolCall call = new ToolCall("c1", "add", arguments, "{\"num1\": 5, \"num2\": 7}", "MockTools.add");
        ToolResult result = adapter.execute(call);

        assertNull(result.error());
        assertEquals(12, result.result());
        assertEquals("add", result.name());
        assertEquals("c1", result.id());
    }

    @Test
    public void testToolExecutionNotFound() {
        MockTools toolsObj = new MockTools();
        ToolAdapter adapter = new ToolAdapter(List.of(toolsObj));

        Map<String, Object> emptyArgs = new HashMap<>();
        ToolCall call = new ToolCall("c2", "nonExistentTool", emptyArgs, "{}", "MockTools.nonExistentTool");
        ToolResult result = adapter.execute(call);

        assertNotNull(result.error());
        assertTrue(result.error().contains("Tool not found"));
    }
}
