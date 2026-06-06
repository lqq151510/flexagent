package org.flexagent.core.strategy;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.model.ToolCall;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.runtime.AgentRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReflectionStrategyTest {

    @Mock
    private AgentRuntime runtime;

    @Mock
    private AgentStrategy executorStrategy;

    @Mock
    private Function<ToolCall, ToolResult> toolExecutor;

    @Test
    void testReflectionExecution() throws IOException {
        ReflectionStrategy strategy = new ReflectionStrategy(executorStrategy);

        // 1st generation -> executor returns initial message
        AgentMessage initialMessage = AgentMessage.assistant("This is the initial answer.");
        // 2nd generation -> executor returns refined message
        AgentMessage refinedMessage = AgentMessage.assistant("This is the refined answer after reflection.");

        String prompt = "What is the capital of France?";
        
        when(executorStrategy.execute(eq(prompt), eq(runtime), eq(toolExecutor)))
                .thenReturn(initialMessage);
        
        when(executorStrategy.execute(argThat(s -> s.contains("Please review your previous answer")), eq(runtime), eq(toolExecutor)))
                .thenReturn(refinedMessage);

        AgentMessage finalAnswer = strategy.execute(prompt, runtime, toolExecutor);

        assertNotNull(finalAnswer);
        assertEquals("This is the refined answer after reflection.", finalAnswer.text());
        
        verify(executorStrategy, times(1)).execute(eq(prompt), eq(runtime), eq(toolExecutor));
        verify(executorStrategy, times(1)).execute(argThat(s -> s.contains("Please review your previous answer")), eq(runtime), eq(toolExecutor));
    }
}
