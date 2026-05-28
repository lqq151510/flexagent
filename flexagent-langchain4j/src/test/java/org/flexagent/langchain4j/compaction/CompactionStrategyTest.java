package org.flexagent.langchain4j.compaction;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CompactionStrategyTest {

    @Test
    void slidingWindowKeepsLatestMessagesAndSystemPrompt() {
        SlidingWindowCompactionStrategy strategy = new SlidingWindowCompactionStrategy(3);

        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are helpful"),
                UserMessage.from("hello"),
                AiMessage.from("hi"),
                UserMessage.from("second"),
                AiMessage.from("response"),
                UserMessage.from("latest")
        );

        List<ChatMessage> compacted = strategy.compact(messages);

        assertEquals(3, compacted.size());
        assertTrue(compacted.get(0) instanceof SystemMessage);
        assertEquals("You are helpful", compacted.get(0).text());
        assertEquals("response", compacted.get(1).text());
        assertEquals("latest", compacted.get(2).text());
    }

    @Test
    void summaryCompactionIntroducesSyntheticSummary() {
        SummaryCompactionStrategy strategy = new SummaryCompactionStrategy(4);

        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are helpful"),
                UserMessage.from("Tell me about release pipelines"),
                AiMessage.from("Use release profile"),
                UserMessage.from("What about javadocs?"),
                AiMessage.from("Attach sources and javadocs"),
                UserMessage.from("How to publish?")
        );

        List<ChatMessage> compacted = strategy.compact(messages);

        assertEquals(4, compacted.size());
        assertTrue(compacted.get(0) instanceof SystemMessage);
        assertTrue(compacted.get(1) instanceof SystemMessage);
        assertTrue(compacted.get(1).text().contains("Conversation summary"));
        assertEquals("Attach sources and javadocs", compacted.get(2).text());
        assertEquals("How to publish?", compacted.get(3).text());
    }

    @Test
    void toolAwareCompactionRetainsToolResults() {
        ToolAwareCompactionStrategy strategy = new ToolAwareCompactionStrategy(4);

        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are helpful"),
                UserMessage.from("calculate"),
                AiMessage.from("Calling tool"),
                ToolExecutionResultMessage.from("call-1", "add", "{\"result\":30}"),
                UserMessage.from("follow up"),
                AiMessage.from("final answer")
        );

        List<ChatMessage> compacted = strategy.compact(messages);

        assertTrue(compacted.stream().anyMatch(msg -> msg instanceof ToolExecutionResultMessage));
        assertTrue(compacted.stream().anyMatch(msg -> "final answer".equals(msg.text())));
    }

    @Test
    void noOpNeverCompacts() {
        NoopCompactionStrategy strategy = new NoopCompactionStrategy();
        assertFalse(strategy.shouldCompact(List.of(UserMessage.from("hello"))));
    }
}
