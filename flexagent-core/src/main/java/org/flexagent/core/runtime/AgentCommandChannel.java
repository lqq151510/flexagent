package org.flexagent.core.runtime;

import org.flexagent.core.model.ToolResult;
import java.io.IOException;

public interface AgentCommandChannel {
    void send(String prompt) throws IOException;
    void sendToolResult(ToolResult result) throws IOException;
}
