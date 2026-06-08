package org.flexagent.core.orchestration;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.multiagent.AgentNode;

import java.util.List;

/**
 * A selector that simply iterates through available agents sequentially.
 */
public class RoundRobinSpeakerSelector implements NextSpeakerSelector {
    
    private int currentIndex = 0;

    @Override
    public AgentNode selectNext(List<AgentNode> availableNodes, List<GroupChatMessage> chatHistory) {
        if (availableNodes == null || availableNodes.isEmpty()) {
            throw new IllegalStateException("No agent nodes available");
        }
        AgentNode next = availableNodes.get(currentIndex);
        currentIndex = (currentIndex + 1) % availableNodes.size();
        return next;
    }
}
