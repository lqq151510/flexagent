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
 * A multi-agent orchestration strategy that dynamically routes a user prompt to a specialized sub-agent.
 */
public class RouterStrategy implements AgentStrategy {

    private static final Logger log = LoggerFactory.getLogger(RouterStrategy.class);

    private final AgentStrategy baseStrategy;
    private final List<AgentNode> availableNodes;

    public RouterStrategy(AgentStrategy baseStrategy, List<AgentNode> availableNodes) {
        this.baseStrategy = baseStrategy;
        this.availableNodes = availableNodes;
    }

    @Override
    public AgentMessage execute(String prompt, AgentRuntime runtime, Function<ToolCall, ToolResult> toolExecutor) throws IOException {
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("You are a Multi-Agent Router. Analyze the user request and determine which specialized agent is best suited to handle it.\n");
        systemPrompt.append("Available agents:\n");
        for (AgentNode node : availableNodes) {
            systemPrompt.append("- ").append(node.getName()).append(": ").append(node.getDescription()).append("\n");
        }
        systemPrompt.append("\nTo route, output exactly:\nROUTE_TO: <agent_name>\nTASK: <task_description>\n\nDo not output anything else.");

        String routePrompt = systemPrompt.toString() + "\n\nUser Request: " + prompt;

        // Execute routing logic using base strategy
        AgentMessage routingDecision = baseStrategy.execute(routePrompt, runtime, toolExecutor);
        
        String decisionText = routingDecision.text() != null ? routingDecision.text() : "";
        log.info("Routing decision:\n{}", decisionText);

        String targetAgentName = null;
        String taskDesc = prompt; // Default to full prompt

        for (String line : decisionText.split("\n")) {
            if (line.startsWith("ROUTE_TO:")) {
                targetAgentName = line.substring("ROUTE_TO:".length()).trim();
            } else if (line.startsWith("TASK:")) {
                taskDesc = line.substring("TASK:".length()).trim();
            }
        }

        if (targetAgentName != null) {
            for (AgentNode node : availableNodes) {
                if (node.getName().equalsIgnoreCase(targetAgentName)) {
                    log.info("Routing request to agent: {}", targetAgentName);
                    return node.execute(taskDesc, Map.of());
                }
            }
            log.warn("Target agent '{}' not found, falling back.", targetAgentName);
        }

        return AgentMessage.assistant("Failed to route request. Decision output was: " + decisionText);
    }
}
