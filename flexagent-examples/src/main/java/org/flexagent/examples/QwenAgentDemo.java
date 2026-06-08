package org.flexagent.examples;

import org.flexagent.core.model.ToolCallPolicy;
import org.flexagent.core.runtime.RuntimeTypes;
import org.flexagent.langchain4j.FlexAgentChatModel;
import org.flexagent.core.memory.compaction.SlidingWindowCompactionStrategy;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class QwenAgentDemo {

    public static class CalculatorTools {
        @Tool("计算商品在指定折扣后的最终售价")
        public double calculateDiscountedPrice(@P("originalPrice") double originalPrice, @P("discountRate") double discountRate) {
            System.out.println("\n[Tool execution] CalculatorTools.calculateDiscountedPrice called with: " + originalPrice + ", discountRate: " + discountRate);
            return originalPrice * discountRate;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== FlexAgent Java Adapter Demo (Qwen) ===");

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

        // 2. Check API Key
        String apiKey = System.getenv("QWEN_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("DASHSCOPE_API_KEY");
        }

        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("\n[提示] 未检测到有效的 QWEN_API_KEY 或 DASHSCOPE_API_KEY 环境变量。");
            System.out.println("请在终端中运行以下命令以执行真实的千问大模型调用：");
            System.out.println("  export QWEN_API_KEY=\"你的_DASHSCOPE_API_KEY\"");
            System.out.println("  mvn -pl flexagent-examples exec:java -Dexec.mainClass=\"org.flexagent.examples.QwenAgentDemo\"");
            return;
        }

        System.out.println("QWEN_API_KEY is configured. Launching real LLM interaction...\n");

        // 3. Setup LangChain4j model (Using Qwen compatible OpenAI endpoint)
        OpenAiChatModel qwenModel = OpenAiChatModel.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .apiKey(apiKey)
                .modelName("qwen-plus") // 通义千问模型
                .build();

        // 4. Wrap with FlexAgentChatModel using v0.2.0 API
        try (FlexAgentChatModel agentModel = FlexAgentChatModel.builder()
                .runtime(RuntimeTypes.LANGCHAIN4J)
                .model(qwenModel)
                .modelName("qwen-plus")
                .toolCallPolicy(ToolCallPolicy.LENIENT)              // 宽容解析策略
                .compactionStrategy(new SlidingWindowCompactionStrategy(5))
                .tools(new CalculatorTools())
                .build()) {

            // 5. Run user query triggering a tool call
            String prompt = "一件原价 299 元的外套打 85 折后的价格是多少？";
            System.out.println("User: " + prompt);
            System.out.println("--- Model Output & Execution Trace ---");

            String response = agentModel.generate(prompt);
            System.out.println("\nAssistant: " + response);
            System.out.println("\nAssistant: Done.");
        } catch (Exception e) {
            System.err.println("Execution failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
