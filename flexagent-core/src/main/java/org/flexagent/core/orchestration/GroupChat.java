package org.flexagent.core.orchestration;

import org.flexagent.core.multiagent.AgentNode;
import org.flexagent.core.memory.AgentMessage;
import java.util.ArrayList;
import java.util.List;

public class GroupChat {
    
    private final List<AgentProfile> agents = new ArrayList<>();
    private final List<AgentNode> agentNodes = new ArrayList<>();
    private final MessageBus messageBus;
    private final List<GroupChatMessage> history = new ArrayList<>();
    
    private NextSpeakerSelector selector = new RoundRobinSpeakerSelector();
    private boolean isFinished = false;
    private int oldAgentIndex = 0; // for backward compatibility of AgentProfile

    public GroupChat(MessageBus messageBus) {
        this.messageBus = messageBus;
    }

    public void addAgent(AgentProfile agent) {
        agents.add(agent);
    }

    public void addAgentNode(AgentNode agentNode) {
        agentNodes.add(agentNode);
    }

    public void setSelector(NextSpeakerSelector selector) {
        this.selector = selector;
    }
    
    public List<AgentProfile> getAgents() {
        return agents;
    }

    public List<AgentNode> getAgentNodes() {
        return agentNodes;
    }

    public AgentProfile nextAgent() {
        if (agents.isEmpty()) {
            throw new IllegalStateException("No agents in the group chat");
        }
        // Fallback backward compatibility for AgentProfile.
        AgentProfile next = agents.get(oldAgentIndex);
        oldAgentIndex = (oldAgentIndex + 1) % agents.size();
        return next;
    }

    public AgentNode nextAgentNode() {
        if (isFinished) {
            return null; // Signals end of chat
        }
        if (agentNodes.isEmpty()) {
            throw new IllegalStateException("No agent nodes in the group chat");
        }
        
        return selector.selectNext(agentNodes, history);
    }
    
    public void broadcast(AgentMessage message, String sender) {
        GroupChatMessage msg = new GroupChatMessage(sender, message);
        history.add(msg);
        messageBus.publish(msg);
    }

    public void broadcast(String message, String source) {
        GroupChatMessage msg = new GroupChatMessage(source, AgentMessage.assistant(message));
        history.add(msg);
        messageBus.publish(msg);
    }

    public boolean isFinished() {
        return isFinished;
    }

    public void setFinished(boolean finished) {
        this.isFinished = finished;
    }
}
