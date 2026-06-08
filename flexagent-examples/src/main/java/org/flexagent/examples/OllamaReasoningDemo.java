package org.flexagent.examples;

import org.flexagent.core.model.ToolCallPolicy;
import org.flexagent.core.runtime.RuntimeTypes;
import org.flexagent.langchain4j.FlexAgentChatModel;
import org.flexagent.core.memory.compaction.SlidingWindowCompactionStrategy;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class OllamaReasoningDemo {

    public static class DatabaseTools {
        @Tool("根据用户ID查询数据库中的用户基本信息")
        public String getUserInfo(@P("userId") String userId) {
            System.out.println("\n[Tool execution] DatabaseTools.getUserInfo called for: " + userId);
            if ("1001".equals(userId)) {
                return "{\"id\":\"1001\",\"name\":\"张三\",\"role\":\"管理员\",\"status\":\"active\"}";
            }
            return "{\"error\":\"User not found\"}";
        }
    }

    public static void main(String[] args) {
        System.out.println("=== FlexAgent Java Adapter Demo (Ollama) ===");

        // 1. SPI Diagnostic
        String type = RuntimeTypes.LANGCHAIN4J;
        boolean hasProvider = false;
        try {
            java.util.ServiceLoader<org.flexagent.core.runtime.AgentRuntimeProvider> loader =
                    java.util.ServiceLoader.load(org.flexagent.core.runtime.AgentRuntimeProvider.class);
            org.flexagent.core.runtime.AgentRuntimeProvider activeProvider = loader.stream()
                    .map(java.util.ServiceLoader.Provider::get)
                    .filter(p -> p.supports(type))
                    .findFirst()
                    .orElse(null);

            if (activeProvider != null) {
                System.out.println("FlexAgent runtime loaded: " + type);
                System.out.println("Provider: " + activeProvider.getClass().getName());
                hasProvider = true;
            } else {
                System.out.println("Warning: No SPI provider found for runtime: " + type);
            }
        } catch (Exception e) {
            System.out.println("SPI Diagnostic error: " + e.getMessage());
        }

        if (!hasProvider) {
            System.out.println("Error: Cannot proceed without langchain4j runtime provider.");
            return;
        }

        // 2. Local Ollama Info
        String ollamaUrl = System.getenv("OLLAMA_BASE_URL");
        if (ollamaUrl == null || ollamaUrl.isEmpty()) {
            ollamaUrl = "http://localhost:11434/v1";
        }
        String modelName = System.getenv("OLLAMA_MODEL_NAME");
        if (modelName == null || modelName.isEmpty()) {
            modelName = "deepseek-r1:8b";
        }

        System.out.println("\n[提示] 本 Demo 默认连接本地 Ollama 服务进行测试：");
        System.out.println("  服务地址 (OLLAMA_BASE_URL): " + ollamaUrl);
        System.out.println("  模型名称 (OLLAMA_MODEL_NAME): " + modelName);
        System.out.println("请确保你已经在本地通过如下命令启动了 Ollama 服务：");
        System.out.println("  ollama run " + modelName);
        System.out.println("你可以通过修改上述环境变量来切换你要调试的本地模型。\n");

        System.out.println("Initializing model and connecting to local Ollama...");

        // 3. Setup Ollama OpenAI-compatible model
        OpenAiChatModel ollamaModel = OpenAiChatModel.builder()
                .baseUrl(ollamaUrl)
                .apiKey("ollama") // Ollama 不需要真实 API Key，但有些 client 校验必填
                .modelName(modelName)
                .build();

        // 4. Wrap with FlexAgentChatModel using v0.2.0 API
        try (FlexAgentChatModel agentModel = FlexAgentChatModel.builder()
                .runtime(RuntimeTypes.LANGCHAIN4J)
                .model(ollamaModel)
                .modelName(modelName)                                // 推理模型名会自动启动 R1 思考拦截
                .toolCallPolicy(ToolCallPolicy.TEXT_FALLBACK)        // 容错回退机制
                .compactionStrategy(new SlidingWindowCompactionStrategy(5))
                .tools(new DatabaseTools())
                .build()) {

            // 5. Run user query triggering a tool call
            String prompt = "查询用户 ID 为 1001 的基本信息，获取后向他打个招呼。";
            System.out.println("User: " + prompt);
            System.out.println("--- Model Output & Execution Trace ---");

            String response = agentModel.generate(prompt);
            System.out.println("\nAssistant: " + response);
            System.out.println("\nAssistant: Done.");
        } catch (Exception e) {
            System.out.println("\n[提示] 连接本地 Ollama 交互失败，可能原因为本地服务未开启或模型未加载。");
            System.out.println("详细错误信息: " + e.getMessage());
        }
    }
}
