package org.flexagent.core.runtime;

import org.flexagent.core.model.RuntimeCapability;
import org.flexagent.core.model.ThinkingMode;
import org.flexagent.core.model.ToolCallPolicy;
import org.flexagent.core.model.Step;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface AgentRuntime extends AutoCloseable, AgentQueryChannel, AgentCommandChannel {
    void initialize(AgentConfig config) throws IOException;
    Set<RuntimeCapability> capabilities();
    ThinkingMode thinkingMode();
    ToolCallPolicy toolCallPolicy();
    
    default void waitForIdle() {}
    
    default Step pollStep(long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException("Blocking pollStep is not supported by default");
    }

    @Override
    default void setHistoryMessages(java.util.List<org.flexagent.core.memory.AgentMessage> messages) {}
    @Override
    default java.util.List<org.flexagent.core.memory.AgentMessage> getHistoryMessages() { return java.util.Collections.emptyList(); }
    @Override
    default void setSessionId(String sessionId) {}
}
