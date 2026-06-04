package org.flexagent.springai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.FunctionMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpringAiMessageInspectorTest {

    private final SpringAiMessageInspector inspector = new SpringAiMessageInspector();

    @Test
    void functionMessageIsToolResult() {
        assertTrue(inspector.isToolResultMessage(new FunctionMessage("{\"ok\":true}")));
        assertFalse(inspector.isToolResultMessage(new UserMessage("hello")));
    }

    @Test
    void assistantFunctionMetadataMatchesFunctionResult() {
        AssistantMessage request = new AssistantMessage(
                "",
                Map.of("function_call", Map.of("id", "call-1", "name", "lookup"))
        );
        FunctionMessage result = new FunctionMessage(
                "{\"value\":42}",
                Map.of("tool_call_id", "call-1")
        );

        assertTrue(inspector.isToolRequestMessage(request));
        assertTrue(inspector.isMatchingToolPair(request, result));
    }

    @Test
    void assistantWithoutToolMetadataIsNotToolRequest() {
        AssistantMessage message = new AssistantMessage("plain text");

        assertFalse(inspector.isToolRequestMessage(message));
    }
}
