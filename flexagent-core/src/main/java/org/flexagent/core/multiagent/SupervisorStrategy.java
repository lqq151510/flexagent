package org.flexagent.core.multiagent;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.model.ToolCall;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.runtime.AgentRuntime;
import org.flexagent.core.strategy.AgentStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A multi-agent orchestration strategy that decomposes a complex task and assigns subtasks to worker agents.
 */
public class SupervisorStrategy implements AgentStrategy {

    private static final Logger log = LoggerFactory.getLogger(SupervisorStrategy.class);

    private final AgentStrategy baseStrategy;
    private final List<AgentNode> workerNodes;

    public SupervisorStrategy(AgentStrategy baseStrategy, List<AgentNode> workerNodes) {
        this.baseStrategy = baseStrategy;
        this.workerNodes = workerNodes;
    }

    @Override
    public AgentMessage execute(String prompt, AgentRuntime runtime, Function<ToolCall, ToolResult> toolExecutor) throws IOException {
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("You are a Multi-Agent Supervisor. Your job is to break down the user request into a subtask that can be handled by one of your worker agents.\n");
        systemPrompt.append("Available worker agents:\n");
        for (AgentNode node : workerNodes) {
            systemPrompt.append("- ").append(node.getName()).append(": ").append(node.getDescription()).append("\n");
        }
        systemPrompt.append("\nOutput your step-by-step plan. Then assign the FIRST subtask by outputting exactly:\n");
        systemPrompt.append("ASSIGN_TO: <agent_name>\nTASK: <task_description>\n");

        String initialPrompt = systemPrompt.toString() + "\n\nUser Request: " + prompt;
        AgentMessage supervisorDecision = baseStrategy.execute(initialPrompt, runtime, toolExecutor);
        
        String decisionText = supervisorDecision.text() != null ? supervisorDecision.text() : "";
        log.info("Supervisor plan:\n{}", decisionText);

        String targetAgentName = null;
        String taskDesc = null;

        for (String line : decisionText.split("\n")) {
            if (line.startsWith("ASSIGN_TO:")) {
                targetAgentName = line.substring("ASSIGN_TO:".length()).trim();
            } else if (line.startsWith("TASK:")) {
                taskDesc = line.substring("TASK:".length()).trim();
            }
        }

        if (targetAgentName != null && taskDesc != null) {
            AgentMessage subResult = null;
            for (AgentNode node : workerNodes) {
                if (node.getName().equalsIgnoreCase(targetAgentName)) {
                    log.info("Supervisor delegating task to worker {}: {}", targetAgentName, taskDesc);
                    subResult = node.execute(taskDesc, Map.of());
                    break;
                }
            }
            
            if (subResult != null) {
                String synthesisPrompt = "The sub-agent '" + targetAgentName + "' has completed the task and returned the following result:\n\n" 
                        + subResult.text() 
                        + "\n\nPlease synthesize the final answer to the user's original request based on this result. You may reformat or summarize it.";
                
                return baseStrategy.execute(synthesisPrompt, runtime, toolExecutor);
            } else {
                return AgentMessage.assistant("Supervisor failed to find worker agent: " + targetAgentName);
            }
        }

        return AgentMessage.assistant("Supervisor could not determine a valid subtask assignment. Output: " + decisionText);
    }
}
