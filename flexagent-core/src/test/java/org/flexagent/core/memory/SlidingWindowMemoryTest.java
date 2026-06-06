package org.flexagent.core.memory;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SlidingWindowMemoryTest {

    @Test
    public void testSlidingWindow() {
        AgentMemory delegate = new InMemoryAgentMemory();
        SlidingWindowMemory memory = new SlidingWindowMemory(delegate, 3);

        String sessionId = "session1";
        memory.addMessage(sessionId, AgentMessage.system("System prompt"));
        memory.addMessage(sessionId, AgentMessage.user("Message 1"));
        memory.addMessage(sessionId, AgentMessage.assistant("Reply 1"));
        
        List<AgentMessage> messages = memory.getMessages(sessionId);
        assertEquals(3, messages.size());
        assertEquals("system", messages.get(0).role());
        assertEquals("Message 1", messages.get(1).text());
        assertEquals("Reply 1", messages.get(2).text());

        memory.addMessage(sessionId, AgentMessage.user("Message 2"));
        // Now total would be 4, but window is 3. So System + last 2.
        messages = memory.getMessages(sessionId);
        assertEquals(3, messages.size());
        assertEquals("system", messages.get(0).role());
        assertEquals("Reply 1", messages.get(1).text());
        assertEquals("Message 2", messages.get(2).text());
    }
}
