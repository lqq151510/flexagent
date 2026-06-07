package org.flexagent.core.strategy;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.model.ToolCall;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.runtime.AgentRuntime;
import org.flexagent.core.orchestration.AgentProfile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class HierarchicalStrategy implements AgentStrategy {

    private final List<AgentProfile> subAgents = new ArrayList<>();
    private final AgentStrategy baseStrategy;

    public HierarchicalStrategy(AgentStrategy baseStrategy) {
        this.baseStrategy = baseStrategy;
    }

    public void addSubAgent(AgentProfile subAgent) {
        subAgents.add(subAgent);
    }

    @Override
    public AgentMessage execute(String prompt, AgentRuntime runtime, Function<ToolCall, ToolResult> toolExecutor) throws IOException {
        StringBuilder subAgentResults = new StringBuilder();

        for (AgentProfile profile : subAgents) {
            String subtaskPrompt = String.format("System Role: %s\nSystem Prompt: %s\nTask: %s", 
                profile.role(), profile.systemPrompt(), prompt);
            
            subAgentResults.append("--- Result from ").append(profile.name()).append(" ---\n");
            
            AgentMessage subResult = baseStrategy.execute(subtaskPrompt, runtime, toolExecutor);
            subAgentResults.append(subResult.text()).append("\n\n");
        }

        String summaryPrompt = "Please summarize the following subagent results for the original prompt: '" + prompt + "'\n\n" + subAgentResults.toString();
        
        return baseStrategy.execute(summaryPrompt, runtime, toolExecutor);
    }
}
