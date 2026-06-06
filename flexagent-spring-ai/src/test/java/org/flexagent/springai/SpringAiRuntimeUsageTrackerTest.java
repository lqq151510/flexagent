package org.flexagent.springai;

import org.flexagent.core.runtime.AgentConfig;
import org.flexagent.core.runtime.UsageTracker;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class SpringAiRuntimeUsageTrackerTest {

    @Test
    public void testUsageTrackerRecordsTokens() throws Exception {
        ChatModel mockChatModel = Mockito.mock(ChatModel.class);
        
        ChatResponse mockResponse = Mockito.mock(ChatResponse.class);
        ChatResponseMetadata metadata = Mockito.mock(ChatResponseMetadata.class);
        org.springframework.ai.chat.metadata.Usage mockUsage = Mockito.mock(org.springframework.ai.chat.metadata.Usage.class);
        
        when(mockUsage.getPromptTokens()).thenReturn(120L);
        when(mockUsage.getGenerationTokens()).thenReturn(60L);
        when(metadata.getUsage()).thenReturn(mockUsage);
        when(mockResponse.getMetadata()).thenReturn(metadata);
        
        Generation mockGeneration = Mockito.mock(Generation.class);
        AssistantMessage mockAssistantMessage = new AssistantMessage("Mock response");
        when(mockGeneration.getOutput()).thenReturn(mockAssistantMessage);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        
        when(mockChatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockResponse);

        class TestUsageTracker implements UsageTracker {
            int totalInput = 0;
            int totalOutput = 0;

            @Override
            public void recordUsage(String sessionId, String modelName, int inputTokens, int outputTokens) {
                totalInput += inputTokens;
                totalOutput += outputTokens;
            }
        }
        
        TestUsageTracker testTracker = new TestUsageTracker();

        AgentConfig config = new AgentConfig();
        config.setUsageTracker(testTracker);
        config.setModelName("spring-ai-model");

        SpringAiRuntime runtime = new SpringAiRuntime(mockChatModel);
        runtime.initialize(config);

        runtime.send("Hello Spring AI");
        runtime.waitForIdle();

        assertEquals(120, testTracker.totalInput, "Input tokens should match");
        assertEquals(60, testTracker.totalOutput, "Output tokens should match");
    }
}
