package org.flexagent.langchain4j;

import org.flexagent.core.model.ToolDefinition;
import org.flexagent.core.model.ToolCall;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.tool.FlexParam;
import org.flexagent.core.tool.FlexTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LangChain4jToolCompatibilityTest {

    public static class CombinedTools {
        @FlexTool(name = "flex_add", description = "Add two numbers using FlexTool")
        public int flexAdd(
                @FlexParam(name = "a", description = "First num") int a,
                @FlexParam(name = "b", description = "Second num") int b
        ) {
            return a + b;
        }

        @Tool(name = "lc4j_subtract", value = "lc4j_subtract")
        public int lc4jSubtract(
                @P("x") int x,
                @P("y") int y
        ) {
            return x - y;
        }
    }

    @Test
    public void testDoubleAnnotationSupport() {
        CombinedTools toolsObj = new CombinedTools();
        ToolAdapter adapter = new ToolAdapter(List.of(toolsObj));

        // 1. Check specification extraction
        List<ToolDefinition> tools = adapter.getTools();
        assertEquals(2, tools.size());

        ToolDefinition flexAddDef = tools.stream().filter(t -> "flex_add".equals(t.name())).findFirst().orElse(null);
        assertNotNull(flexAddDef);
        assertEquals("Add two numbers using FlexTool", flexAddDef.description());
        assertTrue(flexAddDef.parametersJsonSchema().contains("\"a\""));
        assertTrue(flexAddDef.parametersJsonSchema().contains("\"type\":\"integer\""));
        assertTrue(flexAddDef.parametersJsonSchema().contains("\"description\":\"First num\""));

        ToolDefinition lc4jSubDef = tools.stream().filter(t -> "lc4j_subtract".equals(t.name())).findFirst().orElse(null);
        assertNotNull(lc4jSubDef);

        // 2. Test execution of FlexTool
        Map<String, Object> flexArgs = new HashMap<>();
        flexArgs.put("a", 10);
        flexArgs.put("b", 20);
        ToolCall flexCall = new ToolCall("call-1", "flex_add", flexArgs, "{\"a\":10,\"b\":20}", null);
        ToolResult flexRes = adapter.execute(flexCall);
        assertNull(flexRes.error());
        assertEquals(30, flexRes.result());

        // 3. Test execution of LangChain4j Tool
        Map<String, Object> lc4jArgs = new HashMap<>();
        lc4jArgs.put("x", 50);
        lc4jArgs.put("y", 15);
        ToolCall lc4jCall = new ToolCall("call-2", "lc4j_subtract", lc4jArgs, "{\"x\":50,\"y\":15}", null);
        ToolResult lc4jRes = adapter.execute(lc4jCall);
        assertNull(lc4jRes.error());
        assertEquals(35, lc4jRes.result());
    }
}
