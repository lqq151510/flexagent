package org.flexagent.mcp;

import org.flexagent.core.model.ToolDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class McpClientTest {

    private McpClient client;

    @BeforeEach
    public void setUp() throws Exception {
        String classpath = System.getProperty("java.class.path");
        List<String> command = List.of(
                "java",
                "-cp",
                classpath,
                "org.flexagent.mcp.McpClientTest$MockMcpServer"
        );
        client = new McpClient(command);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @Test
    public void testMcpClientLifecycleAndExecution() throws Exception {
        // 1. Handshake success
        assertFalse(client.isRunning());
        client.start();
        assertTrue(client.isRunning());

        // 2. Fetch tools mapping
        List<ToolDefinition> tools = client.listTools();
        assertEquals(1, tools.size());
        ToolDefinition tool = tools.get(0);
        assertEquals("mock_tool", tool.name());
        assertEquals("A mock tool", tool.description());
        assertTrue(tool.parametersJsonSchema().contains("input"));

        // 3. Call tool mapping
        String result = client.callTool("mock_tool", Map.of("input", "hello"));
        assertEquals("mock_result", result);
    }

    /**
     * A Mock MCP Server running inside a Java subprocess for reliable local pipeline verification.
     */
    public static class MockMcpServer {
        public static void main(String[] args) throws Exception {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // Simple JSON-RPC pattern matching
                if (line.contains("\"method\":\"initialize\"")) {
                    long id = extractId(line);
                    System.out.println("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"serverInfo\":{\"name\":\"MockServer\",\"version\":\"1.0\"}}}");
                } else if (line.contains("\"method\":\"tools/list\"")) {
                    long id = extractId(line);
                    System.out.println("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"tools\":[{\"name\":\"mock_tool\",\"description\":\"A mock tool\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"input\":{\"type\":\"string\"}},\"required\":[\"input\"]}}]}}");
                } else if (line.contains("\"method\":\"tools/call\"")) {
                    long id = extractId(line);
                    System.out.println("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"mock_result\"}]}}");
                }
                System.out.flush();
            }
        }

        private static long extractId(String line) {
            int idIdx = line.indexOf("\"id\":");
            if (idIdx == -1) {
                return 1;
            }
            int start = idIdx + 5;
            int end = start;
            while (end < line.length() && Character.isDigit(line.charAt(end))) {
                end++;
            }
            if (start == end) {
                return 1;
            }
            try {
                return Long.parseLong(line.substring(start, end));
            } catch (Exception e) {
                return 1;
            }
        }
    }
}
