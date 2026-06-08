package org.flexagent.core.runtime;

import org.flexagent.core.model.Step;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.model.RuntimeCapability;
import org.flexagent.core.model.ThinkingMode;
import org.flexagent.core.model.ToolCallPolicy;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface AgentRuntime extends AutoCloseable {
    void initialize(AgentConfig config) throws IOException;
    void send(String prompt) throws IOException;
    Step pollStep(long timeout, TimeUnit unit) throws InterruptedException;
    void sendToolResult(ToolResult result) throws IOException;
    void waitForIdle();

    Set<RuntimeCapability> capabilities();
    ThinkingMode thinkingMode();
    ToolCallPolicy toolCallPolicy();

    default void setHistoryMessages(java.util.List<org.flexagent.core.memory.AgentMessage> messages) {}
    default java.util.List<org.flexagent.core.memory.AgentMessage> getHistoryMessages() { return java.util.Collections.emptyList(); }
    default void setSessionId(String sessionId) {}
}
