package org.flexagent.core.runtime;

import org.flexagent.core.model.AgentEvent;
import org.flexagent.core.model.TextDelta;
import org.flexagent.core.model.ThinkingDelta;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class XmlThinkTagExtractorTest {

    @Test
    public void testPlainResponseWithoutTags() {
        XmlThinkTagExtractor extractor = new XmlThinkTagExtractor();
        List<AgentEvent> events = extractor.extract("Hello, this is a plain response.");
        
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof TextDelta);
        assertEquals("Hello, this is a plain response.", ((TextDelta) events.get(0)).text());
    }

    @Test
    public void testResponseWithCompleteTags() {
        XmlThinkTagExtractor extractor = new XmlThinkTagExtractor();
        List<AgentEvent> events = extractor.extract("<think>Solving step by step.</think>The final answer is 42.");
        
        assertEquals(2, events.size());
        
        assertTrue(events.get(0) instanceof ThinkingDelta);
        assertEquals("Solving step by step.", ((ThinkingDelta) events.get(0)).text());
        
        assertTrue(events.get(1) instanceof TextDelta);
        assertEquals("The final answer is 42.", ((TextDelta) events.get(1)).text());
    }

    @Test
    public void testStreamingFragmentedTags() {
        XmlThinkTagExtractor extractor = new XmlThinkTagExtractor();
        
        // Step 1: Send partial start tag "<thi"
        List<AgentEvent> e1 = extractor.extract("<thi");
        assertTrue(e1.isEmpty()); // Should wait for full tag
        
        // Step 2: Send remaining tag + content "nk>Reasoning..."
        List<AgentEvent> e2 = extractor.extract("nk>Reasoning...");
        assertEquals(1, e2.size());
        assertTrue(e2.get(0) instanceof ThinkingDelta);
        assertEquals("Reasoning...", ((ThinkingDelta) e2.get(0)).text());
        
        // Step 3: Send partial end tag "hello</thi"
        List<AgentEvent> e3 = extractor.extract("hello</thi");
        assertEquals(1, e3.size());
        assertTrue(e3.get(0) instanceof ThinkingDelta);
        assertEquals("hello", ((ThinkingDelta) e3.get(0)).text());
        
        // Step 4: Send remaining tag + final text "nk>Result text"
        List<AgentEvent> e4 = extractor.extract("nk>Result text");
        assertEquals(1, e4.size());
        assertTrue(e4.get(0) instanceof TextDelta);
        assertEquals("Result text", ((TextDelta) e4.get(0)).text());
    }

    @Test
    public void testUnclosedThinkingTag() {
        XmlThinkTagExtractor extractor = new XmlThinkTagExtractor();
        List<AgentEvent> e1 = extractor.extract("<think>Analyzing the request...");
        assertEquals(1, e1.size());
        assertTrue(e1.get(0) instanceof ThinkingDelta);
        assertEquals("Analyzing the request...", ((ThinkingDelta) e1.get(0)).text());

        List<AgentEvent> e2 = extractor.extract(" adding more thoughts without closing tag.");
        assertEquals(1, e2.size());
        assertTrue(e2.get(0) instanceof ThinkingDelta);
        assertEquals(" adding more thoughts without closing tag.", ((ThinkingDelta) e2.get(0)).text());
    }

    @Test
    public void testMultipleSequentialThinkingTags() {
        XmlThinkTagExtractor extractor = new XmlThinkTagExtractor();
        List<AgentEvent> events = extractor.extract("<think>First thought</think>Text one<think>Second thought</think>Text two");
        
        assertEquals(4, events.size());
        
        assertTrue(events.get(0) instanceof ThinkingDelta);
        assertEquals("First thought", ((ThinkingDelta) events.get(0)).text());
        
        assertTrue(events.get(1) instanceof TextDelta);
        assertEquals("Text one", ((TextDelta) events.get(1)).text());

        assertTrue(events.get(2) instanceof ThinkingDelta);
        assertEquals("Second thought", ((ThinkingDelta) events.get(2)).text());

        assertTrue(events.get(3) instanceof TextDelta);
        assertEquals("Text two", ((TextDelta) events.get(3)).text());
    }
}
