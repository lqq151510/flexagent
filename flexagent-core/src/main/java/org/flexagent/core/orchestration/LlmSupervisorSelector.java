package org.flexagent.core.orchestration;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.multiagent.AgentNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;

/**
 * A selector that uses an LLM function to analyze chat history and dynamically pick the next speaker.
 */
public class LlmSupervisorSelector implements NextSpeakerSelector {

    private static final Logger log = LoggerFactory.getLogger(LlmSupervisorSelector.class);
    
    private final Function<String, String> completionFunction;
    private final int maxHistoryToKeep;

    public LlmSupervisorSelector(Function<String, String> completionFunction) {
        this(completionFunction, 10);
    }

    public LlmSupervisorSelector(Function<String, String> completionFunction, int maxHistoryToKeep) {
        this.completionFunction = completionFunction;
        this.maxHistoryToKeep = maxHistoryToKeep;
    }

    @Override
    public AgentNode selectNext(List<AgentNode> availableNodes, List<GroupChatMessage> chatHistory) {
        if (availableNodes == null || availableNodes.isEmpty()) {
            throw new IllegalStateException("No agent nodes available");
        }
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a GroupChat Supervisor. Read the conversation history and select the most appropriate next speaker from the available agents. ");
        prompt.append("If a judge or summarizer should conclude the discussion, select them.\n");
        prompt.append("Available agents:\n");
        for (AgentNode node : availableNodes) {
            prompt.append("- ").append(node.getName()).append(": ").append(node.getDescription()).append("\n");
        }
        prompt.append("\nOutput exactly: ROUTE_TO: <agent_name>\nDo not output any other text.\n\nRecent Chat History:\n");
        
        int startIdx = Math.max(0, chatHistory.size() - maxHistoryToKeep);
        for (int i = startIdx; i < chatHistory.size(); i++) {
            GroupChatMessage msg = chatHistory.get(i);
            prompt.append(msg.sender()).append(": ").append(msg.text()).append("\n");
        }
        
        String decisionText = completionFunction.apply(prompt.toString());
        log.info("LlmSupervisorSelector decision:\n{}", decisionText);

        if (decisionText != null) {
            for (String line : decisionText.split("\n")) {
                if (line.trim().startsWith("ROUTE_TO:")) {
                    String target = line.substring("ROUTE_TO:".length()).trim();
                    for (AgentNode node : availableNodes) {
                        if (node.getName().equalsIgnoreCase(target)) {
                            return node;
                        }
                    }
                    log.warn("Supervisor selected unknown agent '{}', falling back to first.", target);
                    break;
                }
            }
        }

        // Fallback
        return availableNodes.get(0);
    }
}
