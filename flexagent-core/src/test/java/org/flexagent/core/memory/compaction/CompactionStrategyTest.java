package org.flexagent.core.memory.compaction;

import org.flexagent.core.memory.AgentMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CompactionStrategyTest {

    @Test
    void slidingWindowKeepsLatestMessagesAndSystemPrompt() {
        SlidingWindowCompactionStrategy strategy = new SlidingWindowCompactionStrategy(3);

        List<AgentMessage> messages = List.of(
                AgentMessage.system("You are helpful"),
                AgentMessage.user("hello"),
                AgentMessage.assistant("hi"),
                AgentMessage.user("second"),
                AgentMessage.assistant("response"),
                AgentMessage.user("latest")
        );

        List<AgentMessage> compacted = strategy.compact(messages);

        assertEquals(3, compacted.size());
        assertEquals("system", compacted.get(0).role());
        assertEquals("You are helpful", compacted.get(0).text());
        assertEquals("response", compacted.get(1).text());
        assertEquals("latest", compacted.get(2).text());
    }

    @Test
    void summaryCompactionIntroducesSyntheticSummary() {
        SummaryCompactionStrategy strategy = new SummaryCompactionStrategy(4);

        List<AgentMessage> messages = List.of(
                AgentMessage.system("You are helpful"),
                AgentMessage.user("Tell me about release pipelines"),
                AgentMessage.assistant("Use release profile"),
                AgentMessage.user("What about javadocs?"),
                AgentMessage.assistant("Attach sources and javadocs"),
                AgentMessage.user("How to publish?")
        );

        List<AgentMessage> compacted = strategy.compact(messages);

        assertEquals(4, compacted.size());
        assertEquals("system", compacted.get(0).role());
        assertEquals("system", compacted.get(1).role());
        assertTrue(compacted.get(1).text().contains("Conversation summary"));
        assertEquals("Attach sources and javadocs", compacted.get(2).text());
        assertEquals("How to publish?", compacted.get(3).text());
    }

    @Test
    void toolAwareCompactionRetainsToolResults() {
        ToolAwareCompactionStrategy strategy = new ToolAwareCompactionStrategy(4);

        List<AgentMessage> messages = List.of(
                AgentMessage.system("You are helpful"),
                AgentMessage.user("calculate"),
                AgentMessage.assistant("Calling tool"),
                AgentMessage.tool("call-1", "add", "{\"result\":30}"),
                AgentMessage.user("follow up"),
                AgentMessage.assistant("final answer")
        );

        List<AgentMessage> compacted = strategy.compact(messages);

        assertTrue(compacted.stream().anyMatch(msg -> "tool".equals(msg.role())));
        assertTrue(compacted.stream().anyMatch(msg -> "final answer".equals(msg.text())));
    }

    @Test
    void legacyCompactOnlyStrategyCompactsByDefault() {
        class LegacyStrategy implements CompactionStrategy<AgentMessage> {
            @Override
            public List<AgentMessage> compact(List<AgentMessage> messages) {
                return messages;
            }
        }

        LegacyStrategy strategy = new LegacyStrategy();

        assertTrue(strategy.shouldCompact(List.of(AgentMessage.user("hello"))));
    }

    @Test
    void noOpNeverCompacts() {
        NoopCompactionStrategy strategy = new NoopCompactionStrategy();
        assertFalse(strategy.shouldCompact(List.of(AgentMessage.user("hello"))));
    }

    @Test
    void slidingWindowCanTriggerByTokenThreshold() {
        SlidingWindowCompactionStrategy strategy = new SlidingWindowCompactionStrategy(3, 100, 10);

        List<AgentMessage> messages = List.of(
                AgentMessage.user("This is a deliberately long message for token estimation."),
                AgentMessage.assistant("Another long assistant response to exceed token threshold."),
                AgentMessage.user("Third long message to trigger compaction by token count."),
                AgentMessage.assistant("Fourth long message that should be compacted.")
        );

        assertTrue(strategy.shouldCompact(messages));
        assertEquals("token-threshold", strategy.compactionReason(messages));
        List<AgentMessage> compacted = strategy.compact(messages);
        assertEquals(3, compacted.size());
    }

    @Test
    void toolAwareSupportsReservedToolMessageParameter() {
        ToolAwareCompactionStrategy strategy = new ToolAwareCompactionStrategy(5, 5, null, 2);

        List<AgentMessage> messages = List.of(
                AgentMessage.system("System"),
                AgentMessage.user("u1"),
                AgentMessage.assistant("a1"),
                AgentMessage.tool("c1", "tool", "r1"),
                AgentMessage.tool("c2", "tool", "r2"),
                AgentMessage.tool("c3", "tool", "r3"),
                AgentMessage.user("u2")
        );

        List<AgentMessage> compacted = strategy.compact(messages);
        long toolResultCount = compacted.stream().filter(m -> "tool".equals(m.role())).count();
        assertEquals(2, toolResultCount);
    }
}
