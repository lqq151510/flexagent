package org.flexagent.core.multiagent;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.model.ToolCall;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.runtime.AgentRuntime;
import org.flexagent.core.strategy.AgentStrategy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultiAgentTest {

    private static class MockAgentNode implements AgentNode {
        private final String name;
        private final String desc;
        private final String mockResponse;

        public MockAgentNode(String name, String desc, String mockResponse) {
            this.name = name;
            this.desc = desc;
            this.mockResponse = mockResponse;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return desc; }

        @Override
        public AgentMessage execute(String task, Map<String, Object> context) {
            return AgentMessage.assistant(mockResponse);
        }
    }

    private static class MockBaseStrategy implements AgentStrategy {
        private final String mockOutput;

        public MockBaseStrategy(String mockOutput) {
            this.mockOutput = mockOutput;
        }

        @Override
        public AgentMessage execute(String prompt, AgentRuntime runtime, Function<ToolCall, ToolResult> toolExecutor) throws IOException {
            return AgentMessage.assistant(mockOutput);
        }
    }

    @Test
    public void testRouterStrategy() throws IOException {
        AgentNode mathAgent = new MockAgentNode("MathAgent", "Solves math problems", "Math answer");
        AgentNode weatherAgent = new MockAgentNode("WeatherAgent", "Gets weather", "Weather answer");

        String llmOutput = "I should route this.\nROUTE_TO: MathAgent\nTASK: What is 2+2?";
        RouterStrategy router = new RouterStrategy(new MockBaseStrategy(llmOutput), List.of(mathAgent, weatherAgent));

        AgentMessage result = router.execute("What is 2+2?", null, null);
        assertEquals("Math answer", result.text());
    }

    @Test
    public void testSupervisorStrategy() throws IOException {
        AgentNode coder = new MockAgentNode("CoderAgent", "Writes code", "Code snippet");

        String llmPlanOutput = "Let's ask the coder.\nASSIGN_TO: CoderAgent\nTASK: Write a loop";
        SupervisorStrategy supervisor = new SupervisorStrategy(new MockBaseStrategy(llmPlanOutput), List.of(coder));

        AgentMessage result = supervisor.execute("I need a loop", null, null);
        // Because the base strategy is mock, the synthesis step will also return the mock string.
        // Wait, the mock strategy always returns the same string.
        assertEquals(llmPlanOutput, result.text());
    }

    @Test
    public void testGroupChatAndMessageBus() {
        org.flexagent.core.orchestration.MessageBus messageBus = new org.flexagent.core.orchestration.InMemoryMessageBus();
        java.util.List<org.flexagent.core.orchestration.GroupChatMessage> received = new java.util.ArrayList<>();
        messageBus.subscribe(received::add);

        org.flexagent.core.orchestration.GroupChat groupChat = new org.flexagent.core.orchestration.GroupChat(messageBus);
        groupChat.setRoutingStrategy(org.flexagent.core.orchestration.GroupChat.RoutingStrategy.ROUND_ROBIN);

        AgentNode nodeA = new MockAgentNode("AgentA", "Desc A", "Reply A");
        AgentNode nodeB = new MockAgentNode("AgentB", "Desc B", "Reply B");

        groupChat.addAgentNode(nodeA);
        groupChat.addAgentNode(nodeB);

        // Verify round robin turn routing
        assertEquals("AgentA", groupChat.nextAgentNode().getName());
        assertEquals("AgentB", groupChat.nextAgentNode().getName());
        assertEquals("AgentA", groupChat.nextAgentNode().getName());

        // Verify message broadcast
        groupChat.broadcast("Hello World", "AgentA");
        assertEquals(1, received.size());
        assertEquals("AgentA", received.get(0).sender());
        assertEquals("Hello World", received.get(0).text());
    }
}
