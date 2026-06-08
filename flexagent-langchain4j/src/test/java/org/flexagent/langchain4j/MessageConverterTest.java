package org.flexagent.langchain4j;

import org.flexagent.core.model.ToolCall;
import org.flexagent.core.model.ToolCallPolicy;
import org.flexagent.core.memory.AgentMessage;
import dev.langchain4j.data.message.*;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MessageConverterTest {

    @Test
    public void testToChatMessageNull() {
        assertNull(MessageConverter.toChatMessage(null));
    }

    @Test
    public void testToChatMessageSystem() {
        AgentMessage msg = AgentMessage.system("System prompt");
        ChatMessage chatMsg = MessageConverter.toChatMessage(msg);
        assertTrue(chatMsg instanceof SystemMessage);
        assertEquals("System prompt", chatMsg.text());
    }

    @Test
    public void testToChatMessageUser() {
        AgentMessage msg = AgentMessage.user("User message");
        ChatMessage chatMsg = MessageConverter.toChatMessage(msg);
        assertTrue(chatMsg instanceof UserMessage);
        assertEquals("User message", chatMsg.text());
    }

    @Test
    public void testToChatMessageAssistant() {
        AgentMessage msg = AgentMessage.assistant("Assistant message");
        ChatMessage chatMsg = MessageConverter.toChatMessage(msg);
        assertTrue(chatMsg instanceof AiMessage);
        assertEquals("Assistant message", chatMsg.text());
        assertFalse(((AiMessage) chatMsg).hasToolExecutionRequests());
    }

    @Test
    public void testToChatMessageAssistantWithTools() {
        List<ToolCall> toolCalls = List.of(
                new ToolCall("call-1", "tool_a", Map.of("arg", 1), "{\"arg\":1}", null)
        );
        AgentMessage msg = AgentMessage.assistant("Ai message", toolCalls);
        ChatMessage chatMsg = MessageConverter.toChatMessage(msg);
        
        assertTrue(chatMsg instanceof AiMessage);
        AiMessage aiMsg = (AiMessage) chatMsg;
        assertEquals("Ai message", aiMsg.text());
        assertTrue(aiMsg.hasToolExecutionRequests());
        assertEquals(1, aiMsg.toolExecutionRequests().size());
        assertEquals("call-1", aiMsg.toolExecutionRequests().get(0).id());
        assertEquals("tool_a", aiMsg.toolExecutionRequests().get(0).name());
        assertEquals("{\"arg\":1}", aiMsg.toolExecutionRequests().get(0).arguments());
    }

    @Test
    public void testToChatMessageTool() {
        AgentMessage msg = AgentMessage.tool("call-1", "tool_a", "success");
        ChatMessage chatMsg = MessageConverter.toChatMessage(msg);
        assertTrue(chatMsg instanceof ToolExecutionResultMessage);
        ToolExecutionResultMessage toolMsg = (ToolExecutionResultMessage) chatMsg;
        assertEquals("call-1", toolMsg.id());
        assertEquals("tool_a", toolMsg.toolName());
        assertEquals("success", toolMsg.text());
    }

    @Test
    public void testToAgentMessageNull() {
        assertNull(MessageConverter.toAgentMessage(null, ToolCallPolicy.STRICT));
    }

    @Test
    public void testToAgentMessageSystem() {
        ChatMessage chatMsg = SystemMessage.from("System prompt");
        AgentMessage msg = MessageConverter.toAgentMessage(chatMsg, ToolCallPolicy.STRICT);
        assertEquals("system", msg.role());
        assertEquals("System prompt", msg.text());
    }

    @Test
    public void testToAgentMessageUser() {
        ChatMessage chatMsg = UserMessage.from("User msg");
        AgentMessage msg = MessageConverter.toAgentMessage(chatMsg, ToolCallPolicy.STRICT);
        assertEquals("user", msg.role());
        assertEquals("User msg", msg.text());
    }

    @Test
    public void testToAgentMessageAi() {
        ChatMessage chatMsg = AiMessage.from("Ai msg");
        AgentMessage msg = MessageConverter.toAgentMessage(chatMsg, ToolCallPolicy.STRICT);
        assertEquals("assistant", msg.role());
        assertEquals("Ai msg", msg.text());
        assertNull(msg.toolCalls());
    }

    @Test
    public void testToAgentMessageAiWithTools() {
        List<dev.langchain4j.agent.tool.ToolExecutionRequest> requests = List.of(
                dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("tool_a")
                        .arguments("{\"arg\": 1}")
                        .build()
        );
        ChatMessage chatMsg = new AiMessage("Ai msg", requests);
        AgentMessage msg = MessageConverter.toAgentMessage(chatMsg, ToolCallPolicy.STRICT);
        
        assertEquals("assistant", msg.role());
        assertEquals("Ai msg", msg.text());
        assertNotNull(msg.toolCalls());
        assertEquals(1, msg.toolCalls().size());
        assertEquals("call-1", msg.toolCalls().get(0).id());
        assertEquals("tool_a", msg.toolCalls().get(0).name());
        assertEquals(1, msg.toolCalls().get(0).arguments().get("arg"));
    }

    @Test
    public void testToAgentMessageTool() {
        ChatMessage chatMsg = ToolExecutionResultMessage.from("call-1", "tool_a", "success");
        AgentMessage msg = MessageConverter.toAgentMessage(chatMsg, ToolCallPolicy.STRICT);
        assertEquals("tool", msg.role());
        assertEquals("call-1", msg.toolId());
        assertEquals("tool_a", msg.toolName());
        assertEquals("success", msg.text());
    }

    @Test
    public void testToAgentMessageToolCallPolicyStrictException() {
        List<dev.langchain4j.agent.tool.ToolExecutionRequest> requests = List.of(
                dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("tool_a")
                        .arguments("invalid-json")
                        .build()
        );
        ChatMessage chatMsg = new AiMessage("Ai msg", requests);
        assertThrows(RuntimeException.class, () -> {
            MessageConverter.toAgentMessage(chatMsg, ToolCallPolicy.STRICT);
        });
    }

    @Test
    public void testToAgentMessageToolCallPolicyTextFallback() {
        List<dev.langchain4j.agent.tool.ToolExecutionRequest> requests = List.of(
                dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("tool_a")
                        .arguments("invalid-json")
                        .build()
        );
        ChatMessage chatMsg = new AiMessage("Ai msg", requests);
        AgentMessage msg = MessageConverter.toAgentMessage(chatMsg, ToolCallPolicy.TEXT_FALLBACK);
        assertEquals("assistant", msg.role());
        assertTrue(msg.text().contains("Tool Call Fallback: call-1 failed to parse json"));
    }

    @Test
    public void testToAgentMessageToolCallPolicyLenient() {
        List<dev.langchain4j.agent.tool.ToolExecutionRequest> requests = List.of(
                dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("tool_a")
                        .arguments("invalid-json")
                        .build()
        );
        ChatMessage chatMsg = new AiMessage("Ai msg", requests);
        AgentMessage msg = MessageConverter.toAgentMessage(chatMsg, ToolCallPolicy.LENIENT);
        assertEquals("assistant", msg.role());
        assertEquals("Ai msg", msg.text());
        assertNotNull(msg.toolCalls());
        assertEquals(1, msg.toolCalls().size());
        assertEquals(Collections.emptyMap(), msg.toolCalls().get(0).arguments());
    }
}
