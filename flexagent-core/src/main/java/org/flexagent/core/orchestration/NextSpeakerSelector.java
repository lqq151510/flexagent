package org.flexagent.core.orchestration;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.multiagent.AgentNode;

import java.util.List;

/**
 * Interface to determine the next speaker in a group chat environment.
 */
public interface NextSpeakerSelector {
    
    /**
     * Determines the next speaker.
     * 
     * @param availableNodes the list of available agent nodes
     * @param chatHistory the recent chat history
     * @return the selected AgentNode, or null to indicate termination.
     */
    AgentNode selectNext(List<AgentNode> availableNodes, List<GroupChatMessage> chatHistory);
}
