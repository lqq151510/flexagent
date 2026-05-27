package org.flexagent.examples;

import org.flexagent.core.model.ToolCallPolicy;
import org.flexagent.core.runtime.RuntimeTypes;
import org.flexagent.langchain4j.FlexAgentChatModel;
import org.flexagent.langchain4j.compaction.SlidingWindowCompactionStrategy;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class DeepSeekAgentDemo {

    public static class WeatherTools {
        @Tool("获取指定城市的当前天气情况")
        public String getWeather(@P("city") String city) {
            System.out.println("\n[Tool execution] WeatherTools.getWeather called with city: " + city);
            return city + "的天气是 22℃，晴转多云，微风。";
        }
    }

    public static void main(String[] args) {
        System.out.println("=== FlexAgent Java Adapter Demo (DeepSeek) ===");

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
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("mock-key")) {
            System.out.println("\n[提示] 未检测到有效的 DEEPSEEK_API_KEY 环境变量。");
            System.out.println("请在终端中运行以下命令以执行真实的远程大模型调用：");
            System.out.println("  export DEEPSEEK_API_KEY=\"你的_DEEPSEEK_API_KEY\"");
            System.out.println("  mvn -pl flexagent-examples exec:java -Dexec.mainClass=\"org.flexagent.examples.DeepSeekAgentDemo\"");
            return;
        }

        System.out.println("DEEPSEEK_API_KEY is configured. Launching real LLM interaction...\n");

        // 3. Setup LangChain4j model
        OpenAiChatModel deepSeekModel = OpenAiChatModel.builder()
                .baseUrl("https://api.deepseek.com/v1")
                .apiKey(apiKey)
                .modelName("deepseek-reasoner") // R1 推理思维链大模型
                .build();

        // 4. Wrap with FlexAgentChatModel using v0.2.0 API
        try (FlexAgentChatModel agentModel = FlexAgentChatModel.builder()
                .runtime(RuntimeTypes.LANGCHAIN4J)
                .model(deepSeekModel)
                .modelName("deepseek-reasoner")                      // R1 思考标签会根据模型名自动启用推理流拦截提取
                .toolCallPolicy(ToolCallPolicy.TEXT_FALLBACK)        // 容错回退机制
                .compactionStrategy(new SlidingWindowCompactionStrategy(5)) // 窗口上下文裁剪
                .tools(new WeatherTools())
                .build()) {

            // 5. Run user query triggering a tool call
            String prompt = "北京天气怎么样？获取完后请写出一首有关此时北京的诗。";
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
