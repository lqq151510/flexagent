package org.flexagent.langchain4j;

import org.flexagent.core.memory.AgentMemory;
import org.flexagent.core.memory.InMemoryAgentMemory;
import org.flexagent.core.model.ToolCallPolicy;
import org.flexagent.langchain4j.compaction.ToolAwareCompactionStrategy;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FlexAgentMemoryTest {

    public static class TestTools {
        @Tool("Adds two integers")
        public int add(@P("a") int a, @P("b") int b) {
            return a + b;
        }
    }

    @Test
    public void testWithoutMemoryBehavior() {
        final List<List<ChatMessage>> capturedMessages = new ArrayList<>();
        ChatLanguageModel mockModel = new ChatLanguageModel() {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                capturedMessages.add(new ArrayList<>(messages));
                return Response.from(AiMessage.from("Stateless response"));
            }

            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
                return generate(messages);
            }
        };

        FlexAgentChatModel model = FlexAgentChatModel.builder()
                .delegateModel(mockModel)
                .build();

        model.generate(List.of(new UserMessage("Hello 1")));
        model.generate(List.of(new UserMessage("Hello 2")));

        assertEquals(2, capturedMessages.size());
        // Without memory, each call should only receive the current UserMessage (history is empty)
        assertEquals(1, capturedMessages.get(0).size());
        assertEquals("Hello 1", ((UserMessage) capturedMessages.get(0).get(0)).text());
        assertEquals(1, capturedMessages.get(1).size());
        assertEquals("Hello 2", ((UserMessage) capturedMessages.get(1).get(0)).text());
    }

    @Test
    public void testWithMemoryMultiTurn() {
        final List<List<ChatMessage>> capturedMessages = new ArrayList<>();
        ChatLanguageModel mockModel = new ChatLanguageModel() {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                capturedMessages.add(new ArrayList<>(messages));
                return Response.from(AiMessage.from("Reply to: " + messages.get(messages.size() - 1).text()));
            }

            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
                return generate(messages);
            }
        };

        AgentMemory memory = new InMemoryAgentMemory();
        FlexAgentChatModel model = FlexAgentChatModel.builder()
                .delegateModel(mockModel)
                .memory(memory)
                .build();

        Response<AiMessage> res1 = model.generate("session-1", "Hello");
        assertEquals("Reply to: Hello", res1.content().text());

        Response<AiMessage> res2 = model.generate("session-1", "How are you?");
        assertEquals("Reply to: How are you?", res2.content().text());

        assertEquals(2, capturedMessages.size());

        // First turn: just ["Hello"]
        List<ChatMessage> turn1 = capturedMessages.get(0);
        assertEquals(1, turn1.size());
        assertEquals("Hello", ((UserMessage) turn1.get(0)).text());

        // Second turn: ["Hello", "Reply to: Hello", "How are you?"]
        List<ChatMessage> turn2 = capturedMessages.get(1);
        assertEquals(3, turn2.size());
        assertEquals("Hello", ((UserMessage) turn2.get(0)).text());
        assertEquals("Reply to: Hello", ((AiMessage) turn2.get(1)).text());
        assertEquals("How are you?", ((UserMessage) turn2.get(2)).text());
    }

    @Test
    public void testSessionIsolation() {
        final List<List<ChatMessage>> capturedMessages = new ArrayList<>();
        ChatLanguageModel mockModel = new ChatLanguageModel() {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                capturedMessages.add(new ArrayList<>(messages));
                return Response.from(AiMessage.from("OK"));
            }

            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
                return generate(messages);
            }
        };

        AgentMemory memory = new InMemoryAgentMemory();
        FlexAgentChatModel model = FlexAgentChatModel.builder()
                .delegateModel(mockModel)
                .memory(memory)
                .build();

        model.generate("session-A", "Apple");
        model.generate("session-B", "Banana");
        model.generate("session-A", "Next");

        assertEquals(3, capturedMessages.size());

        // Turn 1 (session-A): ["Apple"]
        assertEquals(1, capturedMessages.get(0).size());
        assertEquals("Apple", ((UserMessage) capturedMessages.get(0).get(0)).text());

        // Turn 2 (session-B): ["Banana"] (should NOT contain Apple)
        assertEquals(1, capturedMessages.get(1).size());
        assertEquals("Banana", ((UserMessage) capturedMessages.get(1).get(0)).text());

        // Turn 3 (session-A): ["Apple", "OK", "Next"] (should NOT contain Banana)
        List<ChatMessage> turn3 = capturedMessages.get(2);
        assertEquals(3, turn3.size());
        assertEquals("Apple", ((UserMessage) turn3.get(0)).text());
        assertEquals("OK", ((AiMessage) turn3.get(1)).text());
        assertEquals("Next", ((UserMessage) turn3.get(2)).text());
    }

    @Test
    public void testMemoryWithToolCalls() {
        final List<List<ChatMessage>> capturedMessages = new ArrayList<>();
        final int[] turnCount = {0};
        
        ChatLanguageModel mockModel = new ChatLanguageModel() {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                capturedMessages.add(new ArrayList<>(messages));
                
                int tc = turnCount[0];
                if (tc == 0) {
                    turnCount[0]++;
                    // First turn user message: request tool execution
                    ToolExecutionRequest req = ToolExecutionRequest.builder()
                            .id("call-1")
                            .name("add")
                            .arguments("{\"a\":1,\"b\":1}")
                            .build();
                    return Response.from(AiMessage.from(req));
                } else if (tc == 1) {
                    turnCount[0]++;
                    // After tool execution, return final answer
                    return Response.from(AiMessage.from("The sum is 2"));
                } else {
                    // Second turn: regular text response
                    return Response.from(AiMessage.from("Got it"));
                }
            }

            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
                return generate(messages);
            }
        };

        AgentMemory memory = new InMemoryAgentMemory();
        FlexAgentChatModel model = FlexAgentChatModel.builder()
                .delegateModel(mockModel)
                .memory(memory)
                .toolCallPolicy(ToolCallPolicy.STRICT)
                .addToolObject(new TestTools())
                .build();

        // 1. First turn: user prompt triggers tool call, executes tool, returns final answer
        Response<AiMessage> res1 = model.generate("session-tool", "Calculate 1+1");
        assertEquals("The sum is 2", res1.content().text());

        // 2. Second turn: regular follow-up message
        Response<AiMessage> res2 = model.generate("session-tool", "Next prompt");
        assertEquals("Got it", res2.content().text());

        // Validate captured messages for the second turn
        // The last captured message should be in capturedMessages index 2
        // Sequence of messages sent in turn 2 should be:
        // 0: UserMessage "Calculate 1+1"
        // 1: AiMessage (with ToolExecutionRequest)
        // 2: ToolExecutionResultMessage "2"
        // 3: AiMessage "The sum is 2"
        // 4: UserMessage "Next prompt"
        assertTrue(capturedMessages.size() >= 3);
        List<ChatMessage> secondTurnInput = capturedMessages.get(2);
        
        assertEquals(5, secondTurnInput.size());
        assertTrue(secondTurnInput.get(0) instanceof UserMessage);
        assertEquals("Calculate 1+1", ((UserMessage) secondTurnInput.get(0)).text());
        
        assertTrue(secondTurnInput.get(1) instanceof AiMessage);
        assertTrue(((AiMessage) secondTurnInput.get(1)).hasToolExecutionRequests());
        
        assertTrue(secondTurnInput.get(2) instanceof ToolExecutionResultMessage);
        assertEquals("2", ((ToolExecutionResultMessage) secondTurnInput.get(2)).text());
        
        assertTrue(secondTurnInput.get(3) instanceof AiMessage);
        assertEquals("The sum is 2", ((AiMessage) secondTurnInput.get(3)).text());
        
        assertTrue(secondTurnInput.get(4) instanceof UserMessage);
        assertEquals("Next prompt", ((UserMessage) secondTurnInput.get(4)).text());
    }

    @Test
    public void testCompactionWithToolAwareKeepsToolHistoryAcrossTurns() {
        final List<List<ChatMessage>> capturedMessages = new ArrayList<>();
        final int[] turnCount = {0};

        ChatLanguageModel mockModel = new ChatLanguageModel() {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                capturedMessages.add(new ArrayList<>(messages));
                int tc = turnCount[0];
                if (tc == 0) {
                    turnCount[0]++;
                    ToolExecutionRequest req = ToolExecutionRequest.builder()
                            .id("call-keep")
                            .name("add")
                            .arguments("{\"a\":2,\"b\":3}")
                            .build();
                    return Response.from(AiMessage.from(req));
                } else if (tc == 1) {
                    turnCount[0]++;
                    return Response.from(AiMessage.from("Tool result is 5"));
                } else {
                    return Response.from(AiMessage.from("follow-up-ok"));
                }
            }

            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
                return generate(messages);
            }
        };

        AgentMemory memory = new InMemoryAgentMemory();
        FlexAgentChatModel model = FlexAgentChatModel.builder()
                .delegateModel(mockModel)
                .memory(memory)
                .toolCallPolicy(ToolCallPolicy.STRICT)
                .addToolObject(new TestTools())
                .compactionStrategy(new ToolAwareCompactionStrategy(4, 4, null, 1))
                .build();

        Response<AiMessage> first = model.generate("session-compaction-tool", "Please add");
        assertEquals("Tool result is 5", first.content().text());
        Response<AiMessage> second = model.generate("session-compaction-tool", "continue");
        assertEquals("follow-up-ok", second.content().text());

        assertTrue(capturedMessages.size() >= 3);
        List<ChatMessage> secondTurnInput = capturedMessages.get(2);
        assertTrue(secondTurnInput.stream().anyMatch(m -> m instanceof ToolExecutionResultMessage));
        assertTrue(secondTurnInput.stream().anyMatch(m -> "continue".equals(m.text())));
    }
}
