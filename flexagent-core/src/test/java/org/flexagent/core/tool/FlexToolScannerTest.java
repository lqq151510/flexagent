package org.flexagent.core.tool;

import org.flexagent.core.model.ToolDefinition;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class FlexToolScannerTest {

    public static class SampleTools {
        @FlexTool(name = "add_numbers", description = "Adds two numbers together")
        public int add(
                @FlexParam(name = "x", description = "First number") int x,
                @FlexParam(name = "y", description = "Second number") int y
        ) {
            return x + y;
        }

        @FlexTool(description = "Say hello to someone")
        public String sayHello(
                @FlexParam(name = "name", description = "Who to say hello to", required = false) String name
        ) {
            return "Hello, " + name;
        }
    }

    @Test
    public void testScanTools() {
        SampleTools toolsObj = new SampleTools();
        List<ToolDefinition> tools = FlexToolScanner.scan(toolsObj);

        assertEquals(2, tools.size());

        ToolDefinition addTool = tools.stream().filter(t -> "add_numbers".equals(t.name())).findFirst().orElse(null);
        assertNotNull(addTool);
        assertEquals("Adds two numbers together", addTool.description());
        assertTrue(addTool.parametersJsonSchema().contains("\"x\""));
        assertTrue(addTool.parametersJsonSchema().contains("\"type\":\"integer\""));
        assertTrue(addTool.parametersJsonSchema().contains("\"description\":\"First number\""));

        ToolDefinition helloTool = tools.stream().filter(t -> "sayHello".equals(t.name())).findFirst().orElse(null);
        assertNotNull(helloTool);
        assertEquals("Say hello to someone", helloTool.description());
        assertTrue(helloTool.parametersJsonSchema().contains("\"name\""));
        assertTrue(helloTool.parametersJsonSchema().contains("\"type\":\"string\""));
        assertTrue(helloTool.parametersJsonSchema().contains("\"description\":\"Who to say hello to\""));
    }
}
