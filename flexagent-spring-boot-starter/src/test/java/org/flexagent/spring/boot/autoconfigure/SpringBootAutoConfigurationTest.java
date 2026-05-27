package org.flexagent.spring.boot.autoconfigure;

import org.flexagent.core.model.ThinkingMode;
import org.flexagent.core.model.ToolCallPolicy;
import org.flexagent.core.runtime.RuntimeTypes;
import org.flexagent.core.tool.FlexParam;
import org.flexagent.core.tool.FlexTool;
import org.flexagent.langchain4j.FlexAgentChatModel;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringBootAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlexAgentAutoConfiguration.class));

    @Configuration
    static class MockModelConfig {
        @Bean
        public ChatLanguageModel mockDelegateModel() {
            return new ChatLanguageModel() {
                @Override
                public Response<AiMessage> generate(List<ChatMessage> messages) {
                    return Response.from(AiMessage.from("Mock"));
                }
                @Override
                public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
                    return Response.from(AiMessage.from("Mock with tools"));
                }
            };
        }
    }

    @Configuration
    static class SpringBeanToolsConfig {
        @Bean
        public SampleSpringTool sampleSpringTool() {
            return new SampleSpringTool();
        }
    }

    static class SampleSpringTool {
        @FlexTool(name = "spring_add", description = "Add numbers using Spring Bean tool")
        public int springAdd(
                @FlexParam(name = "x", description = "X value") int x,
                @FlexParam(name = "y", description = "Y value") int y
        ) {
            return x + y;
        }
    }

    @Test
    public void testAutoConfigurationWithProperties() {
        this.contextRunner
                .withUserConfiguration(MockModelConfig.class)
                .withPropertyValues(
                        "flexagent.runtime=langchain4j",
                        "flexagent.model-name=custom-reasoner-model",
                        "flexagent.thinking-mode=XML_THINK_TAG",
                        "flexagent.tool-call-policy=TEXT_FALLBACK",
                        "flexagent.system-instruction=Test System Prompt"
                )
                .run((context) -> {
                    assertThat(context).hasSingleBean(FlexAgentChatModel.class);
                    FlexAgentChatModel chatModel = context.getBean(FlexAgentChatModel.class);
                    assertThat(chatModel).isNotNull();
                    
                    assertThat(chatModel.activeRuntime()).isNotNull();
                });
    }

    @Test
    public void testAutoConfigurationWithSpringBeanTools() {
        this.contextRunner
                .withUserConfiguration(MockModelConfig.class, SpringBeanToolsConfig.class)
                .withPropertyValues("flexagent.runtime=langchain4j")
                .run((context) -> {
                    assertThat(context).hasSingleBean(FlexAgentChatModel.class);
                    FlexAgentChatModel chatModel = context.getBean(FlexAgentChatModel.class);
                    assertThat(chatModel).isNotNull();
                    
                    assertThat(chatModel.toolObjects()).hasSize(1);
                    Object toolBean = chatModel.toolObjects().get(0);
                    assertThat(toolBean).isInstanceOf(SampleSpringTool.class);
                });
    }
}
