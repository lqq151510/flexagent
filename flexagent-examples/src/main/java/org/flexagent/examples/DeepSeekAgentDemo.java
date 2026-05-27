package org.flexagent.examples;

import org.flexagent.core.model.ThinkingMode;
import org.flexagent.core.model.ToolCallPolicy;
import org.flexagent.langchain4j.FlexAgentChatModel;
import org.flexagent.langchain4j.compaction.SlidingWindowCompactionStrategy;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class DeepSeekAgentDemo {

    public static class WeatherTools {
        @Tool("获取指定城市的当前天气情况")
        public String getWeather(@P("city") String city) {
            System.out.println("[Tool Weather] getWeather called for: " + city);
            return city + "的天气是 25℃，晴天，微风。";
        }
    }

    public static void main(String[] args) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("提示：未检测到 DEEPSEEK_API_KEY 环境变量，本次 Demo 将以配置就绪模式展示。");
            apiKey = "mock-key";
        }

        // 1. 初始化底层的 ChatLanguageModel (可连接远程 DeepSeek R1 或本地 Ollama 端口)
        OpenAiChatModel deepSeekModel = OpenAiChatModel.builder()
                .baseUrl("https://api.deepseek.com/v1")
                .apiKey(apiKey)
                .modelName("deepseek-reasoner") // R1 推理思维链大模型
                .build();

        // 2. 装配通用包装 Model 并启用核心机制
        FlexAgentChatModel agentModel = FlexAgentChatModel.builder()
                .delegateModel(deepSeekModel)
                .thinkingMode(ThinkingMode.XML_THINK_TAG)          // R1 思考标签拦截提取
                .toolCallPolicy(ToolCallPolicy.TEXT_FALLBACK)        // 容错回退机制
                .compactionStrategy(new SlidingWindowCompactionStrategy(5)) // 窗口上下文裁剪
                .addToolObject(new WeatherTools())
                .build();

        System.out.println("=== FlexAgent Java Adapter Demo ===");
        System.out.println("适配层就绪。你可以设置 DEEPSEEK_API_KEY 连通远程服务。");
        System.out.println("通过本适配器，你只需书写常规的 @Tool 方法，便可无缝切换 LocalHarness、Ollama 或国内的 DeepSeek 服务！\n");

        // SPI Runtime Discovery
        String type = "langchain4j";
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
            } else {
                System.out.println("Warning: No SPI provider found for runtime: " + type);
            }
        } catch (Exception e) {
            System.out.println("SPI Diagnostic error: " + e.getMessage());
        }
    }
}
