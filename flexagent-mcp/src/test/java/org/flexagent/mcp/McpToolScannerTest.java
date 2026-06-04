package org.flexagent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flexagent.core.model.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class McpToolScannerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void fetchToolsReturnsValidToolSchema() throws Exception {
        McpToolScanner scanner = new McpToolScanner("http://localhost:3000");

        List<ToolDefinition> tools = scanner.fetchTools();

        assertEquals(1, tools.size());
        ToolDefinition tool = tools.get(0);
        assertEquals("mcp_search_knowledge", tool.name());
        assertFalse(tool.description().isBlank());

        JsonNode schema = MAPPER.readTree(tool.parametersJsonSchema());
        assertEquals("object", schema.path("type").asText());
        assertTrue(schema.path("properties").has("query"));
        assertEquals("string", schema.path("properties").path("query").path("type").asText());
        assertEquals("query", schema.path("required").get(0).asText());
    }
}
