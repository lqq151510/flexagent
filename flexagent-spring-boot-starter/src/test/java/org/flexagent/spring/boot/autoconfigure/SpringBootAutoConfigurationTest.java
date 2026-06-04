package org.flexagent.spring.boot.autoconfigure;

import org.flexagent.core.tool.FlexParam;
import org.flexagent.core.tool.FlexTool;
import org.flexagent.langchain4j.FlexAgentChatModel;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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

    static class TrackingChatLanguageModel implements ChatLanguageModel {
        private final List<List<ChatMessage>> capturedMessages = new CopyOnWriteArrayList<>();

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            capturedMessages.add(new ArrayList<>(messages));
            ChatMessage last = messages.get(messages.size() - 1);
            return Response.from(AiMessage.from("Reply to: " + last.text()));
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
            return generate(messages);
        }

        List<List<ChatMessage>> capturedMessages() {
            return capturedMessages;
        }
    }

    @Configuration
    static class TrackingModelConfig {
        @Bean
        public TrackingChatLanguageModel trackingChatLanguageModel() {
            return new TrackingChatLanguageModel();
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

    @Test
    public void testAutoConfigurationWithInMemoryMemory() {
        this.contextRunner
                .withUserConfiguration(MockModelConfig.class)
                .withPropertyValues(
                        "flexagent.memory.type=in-memory",
                        "flexagent.memory.ttl=30m"
                )
                .run((context) -> {
                    assertThat(context).hasSingleBean(org.flexagent.core.memory.AgentMemory.class);
                    org.flexagent.core.memory.AgentMemory memory = context.getBean(org.flexagent.core.memory.AgentMemory.class);
                    assertThat(memory).isInstanceOf(org.flexagent.core.memory.InMemoryAgentMemory.class);
                });
    }

    @Test
    public void testAutoConfigurationWithRedisMemory() {
        this.contextRunner
                .withUserConfiguration(MockModelConfig.class)
                .withPropertyValues(
                        "flexagent.memory.type=redis",
                        "flexagent.memory.ttl=1h",
                        "flexagent.memory.redis.host=localhost",
                        "flexagent.memory.redis.port=6379"
                )
                .run((context) -> {
                    assertThat(context).hasSingleBean(org.flexagent.core.memory.AgentMemory.class);
                    org.flexagent.core.memory.AgentMemory memory = context.getBean(org.flexagent.core.memory.AgentMemory.class);
                    assertThat(memory).isInstanceOf(org.flexagent.core.memory.RedisAgentMemory.class);
                });
    }

    @Test
    public void testAutoConfiguredMemoryPreservesSessionIsolation() {
        this.contextRunner
                .withUserConfiguration(TrackingModelConfig.class)
                .withPropertyValues(
                        "flexagent.runtime=langchain4j",
                        "flexagent.memory.type=in-memory",
                        "flexagent.memory.ttl=5m"
                )
                .run((context) -> {
                    FlexAgentChatModel chatModel = context.getBean(FlexAgentChatModel.class);
                    TrackingChatLanguageModel trackingModel = context.getBean(TrackingChatLanguageModel.class);

                    chatModel.generate("session-A", "Apple");
                    chatModel.generate("session-B", "Banana");
                    chatModel.generate("session-A", "Next");

                    List<List<ChatMessage>> captured = trackingModel.capturedMessages();
                    assertThat(captured).hasSize(3);
                    assertThat(captured.get(0))
                            .hasSize(1)
                            .allMatch(message -> message instanceof UserMessage);
                    assertThat(captured.get(0).get(0).text()).isEqualTo("Apple");

                    assertThat(captured.get(1))
                            .hasSize(1)
                            .allMatch(message -> message instanceof UserMessage);
                    assertThat(captured.get(1).get(0).text()).isEqualTo("Banana");

                    assertThat(captured.get(2)).hasSize(3);
                    assertThat(captured.get(2).get(0).text()).isEqualTo("Apple");
                    assertThat(captured.get(2).get(1).text()).isEqualTo("Reply to: Apple");
                    assertThat(captured.get(2).get(2).text()).isEqualTo("Next");
                });
    }

    @Test
    public void testAutoConfiguredMemoryHonorsTtlExpiration() {
        this.contextRunner
                .withUserConfiguration(TrackingModelConfig.class)
                .withPropertyValues(
                        "flexagent.runtime=langchain4j",
                        "flexagent.memory.type=in-memory",
                        "flexagent.memory.ttl=50ms"
                )
                .run((context) -> {
                    FlexAgentChatModel chatModel = context.getBean(FlexAgentChatModel.class);
                    TrackingChatLanguageModel trackingModel = context.getBean(TrackingChatLanguageModel.class);

                    chatModel.generate("session-ttl", "Hello");
                    try {
                        Thread.sleep(120);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("TTL verification interrupted", e);
                    }
                    chatModel.generate("session-ttl", "Again");

                    List<List<ChatMessage>> captured = trackingModel.capturedMessages();
                    assertThat(captured).hasSize(2);
                    assertThat(captured.get(0)).hasSize(1);
                    assertThat(captured.get(0).get(0).text()).isEqualTo("Hello");
                    assertThat(captured.get(1)).hasSize(1);
                    assertThat(captured.get(1).get(0).text()).isEqualTo("Again");
                });
    }
}
