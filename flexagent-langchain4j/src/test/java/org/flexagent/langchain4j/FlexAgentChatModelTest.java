package org.flexagent.langchain4j;

import org.flexagent.core.model.ToolCallPolicy;
import org.flexagent.core.memory.InMemoryAgentMemory;
import org.flexagent.langchain4j.compaction.SlidingWindowCompactionStrategy;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FlexAgentChatModelTest {
    private static final Logger log = LoggerFactory.getLogger(FlexAgentChatModelTest.class);

    public static class Calculator {
        @Tool("Adds two integers")
        public int add(@P("a") int a, @P("b") int b) {
            log.info("Java Tool add invoked with a={}, b={}", a, b);
            return a + b;
        }
    }

    @Test
    public void testGenerateWithMockHarnessAndToolExecution() {
        // Navigate to project parent root if running inside the submodule
        File rootDir;
        try {
            File currentDir = new File(".").getCanonicalFile();
            if (currentDir.getName().equals("flexagent-langchain4j")) {
                rootDir = currentDir.getParentFile();
            } else {
                rootDir = currentDir;
            }
        } catch (Exception e) {
            rootDir = new File(".").getAbsoluteFile();
        }
        
        File mockScript = new File(rootDir, "flexagent-localharness/src/test/resources/run_mock.sh");
        String binaryPath = mockScript.getAbsolutePath();
        String storageDir = new File(rootDir, "target/mock-storage").getAbsolutePath();

        log.info("Using mock wrapper binary path: {}", binaryPath);

        FlexAgentChatModel model = FlexAgentChatModel.builder()
                .binaryPath(binaryPath)
                .storageDirectory(storageDir)
                .modelName("gemini-3.5-flash")
                .thinkingLevel("high")
                .addToolObject(new Calculator())
                .build();

        Response<AiMessage> response = model.generate(List.of(new UserMessage("Calculate 10 + 20")));

        assertNotNull(response);
        assertNotNull(response.content());
        
        String responseText = response.content().text();
        log.info("Agent response text: {}", responseText);

        // Verify that the mock harness received '30' and responded with success text
        assertTrue(responseText.contains("Result is 30"), "Response text should contain 'Result is 30' from tool output");
    }

    @Test
    public void testToolCallPolicyTextFallback() {
        ChatLanguageModel mockModel = new ChatLanguageModel() {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                ToolExecutionRequest invalidRequest = ToolExecutionRequest.builder()
                        .id("call-fail")
                        .name("add")
                        .arguments("{broken json here")
                        .build();
                AiMessage aiMessage = AiMessage.from(invalidRequest);
                return Response.from(aiMessage);
            }

            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
                return generate(messages);
            }
        };

        FlexAgentChatModel model = FlexAgentChatModel.builder()
                .delegateModel(mockModel)
                .toolCallPolicy(ToolCallPolicy.TEXT_FALLBACK)
                .build();

        Response<AiMessage> response = model.generate(List.of(new UserMessage("Fail this tool call")));
        assertNotNull(response);
        assertNotNull(response.content());
        
        String text = response.content().text();
        assertTrue(text.contains("Tool Call Fallback"), "Should fallback to normal text if JSON fails to parse under TEXT_FALLBACK");
        assertTrue(text.contains("call-fail"));
    }

    @Test
    public void testSlidingWindowCompactionStrategy() {
        final List<List<ChatMessage>> capturedMessages = new ArrayList<>();
        ChatLanguageModel mockModel = new ChatLanguageModel() {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                capturedMessages.add(messages);
                return Response.from(AiMessage.from("Compacted"));
            }

            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
                return null;
            }
        };

        FlexAgentChatModel model = FlexAgentChatModel.builder()
                .delegateModel(mockModel)
                .compactionStrategy(new SlidingWindowCompactionStrategy(3))
                .systemInstruction("System Prompt")
                .build();

        List<ChatMessage> context = List.of(
                new SystemMessage("System Prompt"),
                new UserMessage("Msg 1"),
                new AiMessage("Reply 1"),
                new UserMessage("Msg 2"),
                new AiMessage("Reply 2"),
                new UserMessage("Msg 3")
        );
        Response<AiMessage> response = model.generate(context);
        
        assertNotNull(response);
        assertEquals(1, capturedMessages.size());
        List<ChatMessage> actualSent = capturedMessages.get(0);
        
        assertEquals(3, actualSent.size());
        assertTrue(actualSent.get(0) instanceof SystemMessage);
        assertEquals("System Prompt", ((SystemMessage) actualSent.get(0)).text());
        assertEquals("Reply 2", ((AiMessage) actualSent.get(1)).text());
        assertEquals("Msg 3", ((UserMessage) actualSent.get(2)).text());
    }

    @Test
    public void testToolCallPolicyLenientWithBrokenJson() {
        final int[] turn = {0};
        ChatLanguageModel mockModel = new ChatLanguageModel() {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                if (turn[0] == 0) {
                    turn[0]++;
                    ToolExecutionRequest unquotedRequest = ToolExecutionRequest.builder()
                            .id("call-lenient")
                            .name("add")
                            .arguments("{a: 10, b: 20}") // unquoted JSON keys
                            .build();
                    AiMessage aiMessage = AiMessage.from(unquotedRequest);
                    return Response.from(aiMessage);
                } else {
                    return Response.from(AiMessage.from("Done"));
                }
            }

            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
                return generate(messages);
            }
        };

        FlexAgentChatModel model = FlexAgentChatModel.builder()
                .delegateModel(mockModel)
                .toolCallPolicy(ToolCallPolicy.LENIENT)
                .addToolObject(new Calculator())
                .build();

        Response<AiMessage> response = model.generate(List.of(new UserMessage("Add 10 and 20")));
        assertNotNull(response);
        assertTrue(response.content().text().contains("Done"));
    }

    @Test
    public void testToolCallPolicyStrictWithBrokenJson() {
        ChatLanguageModel mockModel = new ChatLanguageModel() {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                ToolExecutionRequest brokenRequest = ToolExecutionRequest.builder()
                        .id("call-strict")
                        .name("add")
                        .arguments("{broken json")
                        .build();
                AiMessage aiMessage = AiMessage.from(brokenRequest);
                return Response.from(aiMessage);
            }

            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
                return generate(messages);
            }
        };

        FlexAgentChatModel model = FlexAgentChatModel.builder()
                .delegateModel(mockModel)
                .toolCallPolicy(ToolCallPolicy.STRICT)
                .addToolObject(new Calculator())
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            model.generate(List.of(new UserMessage("This should throw exception")));
        });
        assertTrue(ex.getMessage().contains("Failed to parse tool call arguments"));
        assertTrue(ex.getMessage().contains("STRICT"));
    }

    @Test
    public void testGenerateWithSessionMemoryPreservesSystemPrompt() {
        List<List<ChatMessage>> capturedMessages = new ArrayList<>();
        ChatLanguageModel capturingModel = new ChatLanguageModel() {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                capturedMessages.add(new ArrayList<>(messages));
                return Response.from(AiMessage.from("ok"));
            }

            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
                return generate(messages);
            }
        };

        FlexAgentChatModel model = FlexAgentChatModel.builder()
                .delegateModel(capturingModel)
                .memory(new InMemoryAgentMemory())
                .systemInstruction("System Prompt")
                .build();

        Response<AiMessage> response = model.generate("session-1", "Hello");
        assertNotNull(response);
        assertFalse(capturedMessages.isEmpty());

        List<ChatMessage> sentMessages = capturedMessages.get(0);
        assertTrue(sentMessages.get(0) instanceof SystemMessage);
        assertEquals("System Prompt", sentMessages.get(0).text());
        assertTrue(sentMessages.get(sentMessages.size() - 1) instanceof UserMessage);
        assertEquals("Hello", sentMessages.get(sentMessages.size() - 1).text());
    }

    @Test
    public void testToolCallHallucinatedTool() {
        final int[] turn = {0};
        ChatLanguageModel dynamicMockModel = new ChatLanguageModel() {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                if (turn[0] == 0) {
                    turn[0]++;
                    ToolExecutionRequest hallucinatedRequest = ToolExecutionRequest.builder()
                            .id("call-hallucinated")
                            .name("non_existent_tool")
                            .arguments("{\"param\": 123}")
                            .build();
                    return Response.from(AiMessage.from(hallucinatedRequest));
                } else {
                    ChatMessage last = messages.get(messages.size() - 1);
                    if (last instanceof dev.langchain4j.data.message.ToolExecutionResultMessage toolResultMsg) {
                        return Response.from(AiMessage.from("Error received: " + toolResultMsg.text()));
                    }
                    return Response.from(AiMessage.from("Done"));
                }
            }

            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
                return generate(messages);
            }
        };

        FlexAgentChatModel model2 = FlexAgentChatModel.builder()
                .delegateModel(dynamicMockModel)
                .addToolObject(new Calculator())
                .build();

        Response<AiMessage> response = model2.generate(List.of(new UserMessage("Trigger hallucination")));
        assertNotNull(response);
        assertTrue(response.content().text().contains("Tool not found in registry: non_existent_tool"));
    }
}
