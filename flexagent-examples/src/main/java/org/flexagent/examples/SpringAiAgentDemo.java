package org.flexagent.examples;

import org.flexagent.core.model.ToolCallPolicy;
import org.flexagent.core.runtime.RuntimeTypes;
import org.flexagent.langchain4j.FlexAgentChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class SpringAiAgentDemo {

    public static class CalculatorTools {
        @Tool("计算两个整数的加法")
        public int add(@P("a") int a, @P("b") int b) {
            System.out.println("\n[Tool execution] CalculatorTools.add called with: a=" + a + ", b=" + b);
            return a + b;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== FlexAgent Java Adapter Demo (Spring AI) ===");

        // 1. SPI Diagnostic
        String type = RuntimeTypes.SPRING_AI;
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
            System.out.println("Error: Cannot proceed without spring-ai runtime provider.");
            return;
        }

        // 2. Check API Key
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("mock-key")) {
            System.out.println("\n[提示] 未检测到有效的 OPENAI_API_KEY 环境变量。");
            System.out.println("请在终端中运行以下命令以执行真实的远程大模型调用：");
            System.out.println("  export OPENAI_API_KEY=\"你的_OPENAI_API_KEY\"");
            System.out.println("  mvn -pl flexagent-examples exec:java -Dexec.mainClass=\"org.flexagent.examples.SpringAiAgentDemo\"");
            return;
        }

        System.out.println("OPENAI_API_KEY is configured. Launching real LLM interaction...\n");

        // 3. Setup Spring AI model
        OpenAiApi openAiApi = new OpenAiApi(apiKey);
        OpenAiChatModel springAiModel = new OpenAiChatModel(openAiApi);

        // 4. Wrap with FlexAgentChatModel using Spring AI runtime
        try (FlexAgentChatModel agentModel = FlexAgentChatModel.builder()
                .runtime(RuntimeTypes.SPRING_AI)
                .model(springAiModel)
                .modelName("gpt-4o-mini")
                .toolCallPolicy(ToolCallPolicy.LENIENT)
                .tools(new CalculatorTools())
                .build()) {

            // 5. Run user query triggering a tool call
            String prompt = "请计算 12345 加 54321 是多少？然后回答北京今天的天气（提示：天气不知道就直说不知道）。";
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
