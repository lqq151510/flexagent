package org.flexagent.springai;

import org.flexagent.core.model.ToolDefinition;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.runtime.AgentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.function.FunctionCallback;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SpringAiRuntimeTest {

    private ChatModel chatModel;
    private SpringAiRuntime runtime;

    @BeforeEach
    public void setUp() {
        chatModel = Mockito.mock(ChatModel.class);
        runtime = new SpringAiRuntime(chatModel);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFunctionCallbackMappingAndExecution() throws Exception {
        // 1. Initialize configuration with a custom tool
        AgentConfig config = new AgentConfig();
        String expectedSchema = "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}";
        ToolDefinition toolDef = new ToolDefinition("BeijingWeather", "Get weather of Beijing", expectedSchema);
        config.addTool(toolDef);

        runtime.initialize(config);

        // 2. Reflectively inspect registered FunctionCallback objects (created in SpringAiRuntime but private)
        // Usually, in runAgentLoop, it builds FunctionCallback from config.getTools().
        // For unit testing, let's invoke the private callback creation or instantiate it directly if possible.
        // In SpringAiRuntime, "FlexAgentFunctionCallback" is a private inner class.
        // We can instantiate it via reflection.
        Class<?> callbackClass = Class.forName("org.flexagent.springai.SpringAiRuntime$FlexAgentFunctionCallback");
        java.lang.reflect.Constructor<?> constructor = callbackClass.getDeclaredConstructor(SpringAiRuntime.class, ToolDefinition.class);
        constructor.setAccessible(true);
        FunctionCallback callback = (FunctionCallback) constructor.newInstance(runtime, toolDef);

        // 3. Verify tool schema mapping
        assertEquals("BeijingWeather", callback.getName());
        assertEquals("Get weather of Beijing", callback.getDescription());
        assertEquals(expectedSchema, callback.getInputTypeSchema());

        // 4. Verify parameter parsing and blocking execution logic in virtual threads
        Thread.ofVirtual().start(() -> {
            try {
                // Call block will emit step, and block until sendToolResult is called
                String output = callback.call("{\"city\":\"Beijing\"}");
                assertEquals("北京天气晴朗", output);
            } catch (Exception e) {
                fail(e.getMessage());
            }
        });

        // Poll the TOOL_CALL step emitted by callback.call
        org.flexagent.core.model.Step step = runtime.pollStep(5, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(step);
        assertEquals(org.flexagent.core.model.StepType.TOOL_CALL, step.type());
        assertFalse(step.toolCalls().isEmpty());

        org.flexagent.core.model.ToolCall tc = step.toolCalls().get(0);
        assertEquals("BeijingWeather", tc.name());
        assertEquals("Beijing", tc.arguments().get("city"));

        // Simulate ToolResult response from FlexAgentChatModel
        ToolResult result = new ToolResult(tc.id(), tc.name(), "北京天气晴朗", null);
        runtime.sendToolResult(result);
    }

    public interface StreamingChatModelMock extends ChatModel, org.springframework.ai.chat.model.StreamingChatModel {}

    @Test
    public void testStreamingResponse() throws Exception {
        StreamingChatModelMock streamingChatModel = Mockito.mock(StreamingChatModelMock.class);
        
        org.springframework.ai.chat.model.ChatResponse chunk1 = new org.springframework.ai.chat.model.ChatResponse(
            List.of(new org.springframework.ai.chat.model.Generation("Hello "))
        );
        org.springframework.ai.chat.model.ChatResponse chunk2 = new org.springframework.ai.chat.model.ChatResponse(
            List.of(new org.springframework.ai.chat.model.Generation("World!"))
        );
        
        Mockito.when(streamingChatModel.stream(Mockito.any(org.springframework.ai.chat.prompt.Prompt.class)))
               .thenReturn(reactor.core.publisher.Flux.just(chunk1, chunk2));

        SpringAiRuntime streamingRuntime = new SpringAiRuntime(streamingChatModel);
        AgentConfig config = new AgentConfig();
        streamingRuntime.initialize(config);
        
        streamingRuntime.send("Say hi");
        
        org.flexagent.core.model.Step step1 = streamingRuntime.pollStep(5, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(step1);
        assertEquals(org.flexagent.core.model.StepType.STREAM_TOKEN, step1.type());
        assertEquals("Hello ", step1.content());
        
        org.flexagent.core.model.Step step2 = streamingRuntime.pollStep(5, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(step2);
        assertEquals(org.flexagent.core.model.StepType.STREAM_TOKEN, step2.type());
        assertEquals("World!", step2.content());
        
        org.flexagent.core.model.Step step3 = streamingRuntime.pollStep(5, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(step3);
        assertEquals(org.flexagent.core.model.StepType.TEXT_RESPONSE, step3.type());
        assertEquals("Hello World!", step3.content());
    }
}
