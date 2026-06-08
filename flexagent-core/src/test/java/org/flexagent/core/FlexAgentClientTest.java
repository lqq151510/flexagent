package org.flexagent.core;

import org.flexagent.core.memory.AgentMemory;
import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.memory.AgentSessionContext;
import org.flexagent.core.model.ToolCall;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.runtime.AgentRuntime;
import org.flexagent.core.strategy.AgentStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FlexAgentClientTest {

    @Mock
    private AgentRuntime runtime;

    @Mock
    private AgentStrategy strategy;

    @Mock
    private AgentMemory memory;

    @Mock
    private Function<ToolCall, ToolResult> toolExecutor;

    private FlexAgentClient client;

    @BeforeEach
    public void setUp() {
        client = FlexAgentClient.builder()
                .activeRuntime(runtime)
                .strategy(strategy)
                .toolExecutor(toolExecutor)
                .initialSystemMessages(List.of(AgentMessage.system("Init System Message")))
                .memory(memory)
                .build();
    }

    @Test
    public void testBuilderValidation() {
        assertThrows(IllegalStateException.class, () -> {
            FlexAgentClient.builder().build();
        });
        assertThrows(IllegalStateException.class, () -> {
            FlexAgentClient.builder().activeRuntime(runtime).build();
        });
        assertThrows(IllegalStateException.class, () -> {
            FlexAgentClient.builder().toolExecutor(toolExecutor).build();
        });
    }

    @Test
    public void testGenerateWithMessagesList() throws Exception {
        List<AgentMessage> messages = new ArrayList<>();
        messages.add(AgentMessage.user("Hello"));
        
        AgentMessage expectedResponse = AgentMessage.assistant("Hi there");
        when(strategy.execute(eq("Hello"), eq(runtime), eq(toolExecutor))).thenReturn(expectedResponse);

        AgentMessage response = client.generate(messages);

        assertEquals(expectedResponse, response);
        verify(runtime).setHistoryMessages(argThat(list -> list.size() == 1 && "system".equals(list.get(0).role())));
    }

    @Test
    public void testGenerateWithMessagesListIncludingSystem() throws Exception {
        List<AgentMessage> messages = new ArrayList<>();
        messages.add(AgentMessage.system("Custom system"));
        messages.add(AgentMessage.user("Hello"));
        
        AgentMessage expectedResponse = AgentMessage.assistant("Hi there");
        when(strategy.execute(eq("Hello"), eq(runtime), eq(toolExecutor))).thenReturn(expectedResponse);

        AgentMessage response = client.generate(messages);

        assertEquals(expectedResponse, response);
        verify(runtime).setHistoryMessages(argThat(list -> list.size() == 1 && "system".equals(list.get(0).role()) && "Custom system".equals(list.get(0).text())));
    }

    @Test
    public void testGenerateWithPromptNoMemory() throws Exception {
        FlexAgentClient noMemoryClient = FlexAgentClient.builder()
                .activeRuntime(runtime)
                .strategy(strategy)
                .toolExecutor(toolExecutor)
                .initialSystemMessages(List.of(AgentMessage.system("Init System Message")))
                .build();

        AgentMessage expectedResponse = AgentMessage.assistant("Response");
        when(strategy.execute(eq("Hello"), eq(runtime), eq(toolExecutor))).thenReturn(expectedResponse);

        AgentMessage response = noMemoryClient.generate("Hello");

        assertEquals(expectedResponse, response);
        verify(runtime).setHistoryMessages(argThat(list -> list.size() == 1 && "Init System Message".equals(list.get(0).text())));
    }

    @Test
    public void testGenerateWithPromptWithMemoryHit() throws Exception {
        String sessionId = "test-session";
        AgentSessionContext.set(sessionId);
        try {
            List<AgentMessage> history = List.of(AgentMessage.user("Prev hello"), AgentMessage.assistant("Prev hi"));
            when(memory.getMessages(sessionId)).thenReturn(history);

            AgentMessage expectedResponse = AgentMessage.assistant("Response");
            when(strategy.execute(eq("Hello"), eq(runtime), eq(toolExecutor))).thenReturn(expectedResponse);
            
            List<AgentMessage> updatedHistory = List.of(AgentMessage.user("Prev hello"), AgentMessage.assistant("Prev hi"), AgentMessage.user("Hello"), expectedResponse);
            when(runtime.getHistoryMessages()).thenReturn(updatedHistory);

            AgentMessage response = client.generate("Hello");

            assertEquals(expectedResponse, response);
            verify(runtime).setHistoryMessages(history);
            verify(memory).clear(sessionId);
            verify(memory).addMessages(sessionId, updatedHistory);
        } finally {
            AgentSessionContext.clear();
        }
    }

    @Test
    public void testGenerateWithPromptWithMemoryMiss() throws Exception {
        String sessionId = "test-session";
        AgentSessionContext.set(sessionId);
        try {
            when(memory.getMessages(sessionId)).thenReturn(Collections.emptyList());

            AgentMessage expectedResponse = AgentMessage.assistant("Response");
            when(strategy.execute(eq("Hello"), eq(runtime), eq(toolExecutor))).thenReturn(expectedResponse);

            AgentMessage response = client.generate("Hello");

            assertEquals(expectedResponse, response);
            verify(runtime).setHistoryMessages(argThat(list -> list.size() == 1 && "Init System Message".equals(list.get(0).text())));
            verify(memory).addMessage(sessionId, AgentMessage.user("Hello"));
            verify(memory).addMessage(sessionId, expectedResponse);
        } finally {
            AgentSessionContext.clear();
        }
    }

    @Test
    public void testGenerateWithSessionIdAndUserMessage() throws Exception {
        AgentMessage expectedResponse = AgentMessage.assistant("Response");
        when(strategy.execute(eq("Hello"), eq(runtime), eq(toolExecutor))).thenReturn(expectedResponse);

        AgentMessage response = client.generate("test-session", "Hello");

        assertEquals(expectedResponse, response);
        verify(runtime).setSessionId("test-session");
    }

    @Test
    public void testStreamWithMessagesList() throws Exception {
        List<AgentMessage> messages = new ArrayList<>();
        messages.add(AgentMessage.user("Hello"));

        doAnswer(invocation -> {
            java.util.function.Consumer<String> callback = invocation.getArgument(3);
            callback.accept("Hi");
            callback.accept(" there");
            return null;
        }).when(strategy).executeStream(eq("Hello"), eq(runtime), eq(toolExecutor), any());

        Flux<String> stream = client.stream(messages);
        List<String> tokens = stream.collectList().block();

        assertNotNull(tokens);
        assertEquals(List.of("Hi", " there"), tokens);
    }

    @Test
    public void testStreamWithPromptWithMemoryHit() throws Exception {
        String sessionId = "test-session";
        AgentSessionContext.set(sessionId);
        try {
            List<AgentMessage> history = List.of(AgentMessage.user("Prev hello"));
            when(memory.getMessages(sessionId)).thenReturn(history);

            AgentMessage expectedResponse = AgentMessage.assistant("Hi there");
            doAnswer(invocation -> {
                java.util.function.Consumer<String> callback = invocation.getArgument(3);
                callback.accept("Hi");
                callback.accept(" there");
                return expectedResponse;
            }).when(strategy).executeStream(eq("Hello"), eq(runtime), eq(toolExecutor), any());

            List<AgentMessage> updatedHistory = List.of(AgentMessage.user("Prev hello"), AgentMessage.user("Hello"), expectedResponse);
            when(runtime.getHistoryMessages()).thenReturn(updatedHistory);

            Flux<String> stream = client.stream("Hello");
            List<String> tokens = stream.collectList().block();

            assertNotNull(tokens);
            assertEquals(List.of("Hi", " there"), tokens);
            verify(memory).clear(sessionId);
            verify(memory).addMessages(sessionId, updatedHistory);
        } finally {
            AgentSessionContext.clear();
        }
    }

    @Test
    public void testStreamWithSessionIdAndUserMessage() throws Exception {
        AgentMessage expectedResponse = AgentMessage.assistant("Hi there");
        doAnswer(invocation -> {
            java.util.function.Consumer<String> callback = invocation.getArgument(3);
            callback.accept("Hi");
            return expectedResponse;
        }).when(strategy).executeStream(eq("Hello"), eq(runtime), eq(toolExecutor), any());

        Flux<String> stream = client.stream("test-session", "Hello");
        List<String> tokens = stream.collectList().block();

        assertNotNull(tokens);
        assertEquals(List.of("Hi"), tokens);
    }
}
