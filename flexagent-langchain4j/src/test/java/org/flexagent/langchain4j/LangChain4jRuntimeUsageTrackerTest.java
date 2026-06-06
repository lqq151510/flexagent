package org.flexagent.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.flexagent.core.runtime.AgentConfig;
import org.flexagent.core.runtime.UsageTracker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LangChain4jRuntimeUsageTrackerTest {

    @Test
    public void testUsageTrackerRecordsTokens() throws Exception {
        // Mock model that returns a known token usage
        ChatLanguageModel mockModel = new ChatLanguageModel() {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                return new Response<>(AiMessage.from("Mock response"), new TokenUsage(100, 50), null);
            }
        };

        // Custom usage tracker to capture the values
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
        config.setModelName("test-model");

        LangChain4jRuntime runtime = new LangChain4jRuntime(mockModel);
        runtime.initialize(config);

        // Send a prompt to trigger the generate call
        runtime.send("Hello world");
        runtime.waitForIdle(); // Wait for the execution to finish

        // Verify that the UsageTracker recorded the tokens from the mock model response
        assertEquals(100, testTracker.totalInput, "Input tokens should match the mock response");
        assertEquals(50, testTracker.totalOutput, "Output tokens should match the mock response");
    }
}
