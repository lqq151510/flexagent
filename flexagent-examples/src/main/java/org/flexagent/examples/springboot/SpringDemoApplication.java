package org.flexagent.examples.springboot;

import org.flexagent.core.tool.FlexParam;
import org.flexagent.core.tool.FlexTool;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@SpringBootApplication
public class SpringDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringDemoApplication.class, args);
    }

    /**
     * Define the delegate model that FlexAgent will auto-configure.
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = "mock-key"; // Fallback for context loading
        }
        return OpenAiChatModel.builder()
                .baseUrl("https://api.deepseek.com/v1")
                .apiKey(apiKey)
                .modelName("deepseek-chat")
                .build();
    }

    /**
     * Define a tool bean. FlexAgentAutoConfiguration will automatically scan
     * and register this tool to the FlexAgentChatModel bean.
     */
    @Component
    public static class OrderTools {
        @FlexTool(name = "calculate_order_price", description = "Calculate order price after discount")
        public BigDecimal calculate(
                @FlexParam(name = "price", description = "Original order price") BigDecimal price,
                @FlexParam(name = "discount", description = "Discount rate (e.g. 0.1 for 10% off)") BigDecimal discount
        ) {
            System.out.println("[OrderTools] calculate called with price=" + price + ", discount=" + discount);
            return price.multiply(BigDecimal.ONE.subtract(discount));
        }
    }
}
