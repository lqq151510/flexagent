package org.flexagent.core.memory;

import org.flexagent.core.memory.compaction.MemoryCompactor;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SummarizationMemoryTest {

    @Test
    public void testSummarization() {
        AgentMemory delegate = new InMemoryAgentMemory();
        
        MemoryCompactor mockCompactor = history -> {
            return List.of(AgentMessage.system("Summarized"));
        };
        
        SummarizationMemory memory = new SummarizationMemory(delegate, 3, mockCompactor);
        
        String sessionId = "session1";
        memory.addMessage(sessionId, AgentMessage.user("Message 1"));
        memory.addMessage(sessionId, AgentMessage.assistant("Reply 1"));
        memory.addMessage(sessionId, AgentMessage.user("Message 2"));
        // Now size is 3, threshold is 3. It should trigger compaction on the NEXT add.
        
        List<AgentMessage> messages = memory.getMessages(sessionId);
        assertEquals(3, messages.size());
        
        memory.addMessage(sessionId, AgentMessage.assistant("Reply 2"));
        // Triggers compaction
        messages = memory.getMessages(sessionId);
        assertEquals(1, messages.size());
        assertEquals("system", messages.get(0).role());
        assertEquals("Summarized", messages.get(0).text());
    }
}
