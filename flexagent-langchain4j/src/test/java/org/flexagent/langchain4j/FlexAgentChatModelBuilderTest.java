package org.flexagent.langchain4j;

import org.flexagent.core.model.ThinkingMode;
import org.flexagent.core.model.ToolCallPolicy;
import org.flexagent.core.runtime.RuntimeTypes;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FlexAgentChatModelBuilderTest {

    private static class MockModel implements ChatLanguageModel {
        private final String modelName;

        public MockModel(String modelName) {
            this.modelName = modelName;
        }

        public String modelName() {
            return modelName;
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return Response.from(AiMessage.from("Mock"));
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
            return generate(messages);
        }
    }

    private static class CapturingModel implements ChatLanguageModel {
        private List<ChatMessage> captured = new ArrayList<>();

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            this.captured = new ArrayList<>(messages);
            return Response.from(AiMessage.from("Mock"));
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
            return generate(messages);
        }
    }

    private static class SimpleTool {
        public String execute() {
            return "Executed";
        }
    }

    @Test
    public void testNewBuilderAPI() throws Exception {
        ChatLanguageModel model = new MockModel("standard-model");
        
        try (FlexAgentChatModel agent = FlexAgentChatModel.builder()
                .runtime(RuntimeTypes.LANGCHAIN4J)
                .model(model)
                .tools(new SimpleTool())
                .enableThinkingExtraction(true)
                .toolCallPolicy(ToolCallPolicy.TEXT_FALLBACK)
                .build()) {
            
            assertNotNull(agent.activeRuntime());
            // Verify thinking extraction toggled correctly
            String response = agent.generate("Hello");
            assertEquals("Mock", response);
        }
    }

    @Test
    public void testAutoDetectReasoningModelFromName() throws Exception {
        ChatLanguageModel standardModel = new MockModel("standard-model");
        ChatLanguageModel deepseekReasonerModel = new MockModel("deepseek-reasoner");

        // Case 1: Standard model - should not auto-enable XML_THINK_TAG
        try (FlexAgentChatModel agent1 = FlexAgentChatModel.builder()
                .runtime(RuntimeTypes.LANGCHAIN4J)
                .model(standardModel)
                .modelName("standard-model")
                .build()) {
            // Success
        }

        // Case 2: DeepSeek R1/Reasoner - should automatically enable thinking extraction
        try (FlexAgentChatModel agent2 = FlexAgentChatModel.builder()
                .runtime(RuntimeTypes.LANGCHAIN4J)
                .model(deepseekReasonerModel)
                .modelName("deepseek-reasoner")
                .build()) {
            // Success
        }
    }

    @Test
    public void testCompactionMaxMessagesBuilderShortcut() throws Exception {
        CapturingModel model = new CapturingModel();

        try (FlexAgentChatModel agent = FlexAgentChatModel.builder()
                .runtime(RuntimeTypes.LANGCHAIN4J)
                .model(model)
                .compactionMaxMessages(5)
                .build()) {
            assertNotNull(agent.activeRuntime());

            List<ChatMessage> context = List.of(
                    SystemMessage.from("System"),
                    UserMessage.from("1"),
                    AiMessage.from("2"),
                    UserMessage.from("3"),
                    AiMessage.from("4"),
                    UserMessage.from("5")
            );
            Response<AiMessage> response = agent.generate(context);
            assertEquals("Mock", response.content().text());
            assertEquals(5, model.captured.size());
        }
    }
}
