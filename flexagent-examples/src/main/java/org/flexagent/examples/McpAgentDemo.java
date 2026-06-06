package org.flexagent.examples;

import org.flexagent.core.model.ToolCall;
import org.flexagent.core.model.ToolDefinition;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.runtime.RuntimeTypes;
import org.flexagent.langchain4j.FlexAgentChatModel;
import org.flexagent.mcp.McpClient;
import org.flexagent.mcp.McpToolExecutor;
import org.flexagent.mcp.McpToolScanner;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class McpAgentDemo {

    public static void main(String[] args) {
        System.out.println("=== FlexAgent Java Adapter Demo (MCP Native stdio) ===");

        String classpath = System.getProperty("java.class.path");
        List<String> command = List.of(
                "java",
                "-cp",
                classpath,
                "org.flexagent.examples.McpAgentDemo$DemoMcpServer"
        );

        // 1. Start Native MCP Client
        try (McpClient mcpClient = new McpClient(command)) {
            mcpClient.start();

            // 2. Scan tools using McpToolScanner
            McpToolScanner scanner = new McpToolScanner(mcpClient);
            List<ToolDefinition> mcpTools = scanner.fetchTools();

            System.out.println("\n[MCP Client] Successfully fetched tools from external MCP server:");
            for (ToolDefinition tool : mcpTools) {
                System.out.println("  - Name: " + tool.name());
                System.out.println("    Description: " + tool.description());
                System.out.println("    Schema: " + tool.parametersJsonSchema());
            }

            // 3. Create McpToolExecutor
            List<String> toolNames = mcpTools.stream().map(ToolDefinition::name).toList();
            McpToolExecutor mcpExecutor = new McpToolExecutor(mcpClient, toolNames);

            // 4. Try loading LLM model
            String apiKey = System.getenv("DEEPSEEK_API_KEY");
            if (apiKey != null && !apiKey.isEmpty() && !apiKey.equals("mock-key")) {
                System.out.println("\nDEEPSEEK_API_KEY is configured. Running online LLM interaction...");
                OpenAiChatModel deepSeekModel = OpenAiChatModel.builder()
                        .baseUrl("https://api.deepseek.com/v1")
                        .apiKey(apiKey)
                        .modelName("deepseek-chat")
                        .build();

                try (FlexAgentChatModel agentModel = FlexAgentChatModel.builder()
                        .runtime(RuntimeTypes.LANGCHAIN4J)
                        .model(deepSeekModel)
                        .tools(mcpTools.toArray())
                        .customToolExecutor(mcpExecutor)
                        .build()) {

                    String prompt = "我的身高是 1.75 米，体重 70 公斤，请调用 mcp_calculate_bmi 工具帮我计算 BMI，并根据结果给我一些健康建议。";
                    System.out.println("User: " + prompt);
                    System.out.println("--- Model Output & Execution Trace ---");

                    String response = agentModel.generate(prompt);
                    System.out.println("\nAssistant: " + response);
                }
            } else {
                System.out.println("\n[提示] 未检测到 DEEPSEEK_API_KEY，进入 Offline Simulation 离线演练。");
                System.out.println("我们将手动构造一个大模型工具调用请求，直接由 FlexAgent 工具路由到外部 MCP 子进程中执行。");

                // Simulate a tool call emitted by an LLM
                String callId = UUID.randomUUID().toString();
                String toolName = "mcp_calculate_bmi";
                Map<String, Object> arguments = Map.of("height", 1.75, "weight", 70.0);
                ToolCall toolCall = new ToolCall(callId, toolName, arguments, "{\"height\":1.75,\"weight\":70.0}", null);

                System.out.println("\n[Simulation] Emitting tool call request: " + toolName + " with arguments " + arguments);
                if (mcpExecutor.supports(toolName)) {
                    System.out.println("[Simulation] Routing tool call to McpToolExecutor...");
                    ToolResult result = mcpExecutor.execute(toolCall);
                    System.out.println("\n[Simulation] Received execution response from external MCP Process:");
                    System.out.println("  - Status: " + (result.error() == null ? "SUCCESS" : "FAILED"));
                    System.out.println("  - Result: " + result.result());
                } else {
                    System.out.println("[Simulation] Tool not supported!");
                }
            }

        } catch (Exception e) {
            System.err.println("Demo execution failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * An external MCP server mock process that calculates BMI indices.
     */
    public static class DemoMcpServer {
        public static void main(String[] args) throws Exception {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (line.contains("\"method\":\"initialize\"")) {
                    long id = extractId(line);
                    System.out.println("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"serverInfo\":{\"name\":\"BmiMcpServer\",\"version\":\"1.0\"}}}");
                } else if (line.contains("\"method\":\"tools/list\"")) {
                    long id = extractId(line);
                    System.out.println("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"tools\":[{\"name\":\"mcp_calculate_bmi\",\"description\":\"Calculates Body Mass Index (BMI) using height (m) and weight (kg)\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"height\":{\"type\":\"number\",\"description\":\"Height in meters\"},\"weight\":{\"type\":\"number\",\"description\":\"Weight in kilograms\"}},\"required\":[\"height\",\"weight\"]}}]}}");
                } else if (line.contains("\"method\":\"tools/call\"")) {
                    long id = extractId(line);
                    // Parse double arguments height and weight roughly
                    double height = 1.75;
                    double weight = 70.0;
                    try {
                        if (line.contains("\"height\":")) {
                            int hIdx = line.indexOf("\"height\":");
                            int commaIdx = line.indexOf(",", hIdx);
                            if (commaIdx == -1) commaIdx = line.indexOf("}", hIdx);
                            height = Double.parseDouble(line.substring(hIdx + 9, commaIdx).trim());
                        }
                        if (line.contains("\"weight\":")) {
                            int wIdx = line.indexOf("\"weight\":");
                            int commaIdx = line.indexOf(",", wIdx);
                            if (commaIdx == -1) commaIdx = line.indexOf("}", wIdx);
                            weight = Double.parseDouble(line.substring(wIdx + 9, commaIdx).trim());
                        }
                    } catch (Exception ignored) {}

                    double bmi = weight / (height * height);
                    String resultText = String.format("您的 BMI 指数是: %.2f。健康范围为 18.5 - 24.9。", bmi);

                    System.out.println("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"" + resultText + "\"}]}}");
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
