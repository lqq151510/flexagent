package org.flexagent.core.runtime;

import org.flexagent.core.memory.AgentMessage;
import java.util.List;

public interface AgentQueryChannel {
    List<AgentMessage> getHistoryMessages();
    void setHistoryMessages(List<AgentMessage> messages);
    void setSessionId(String sessionId);
}
