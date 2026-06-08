package org.flexagent.core.orchestration;

import org.flexagent.core.multiagent.AgentNode;
import org.flexagent.core.memory.AgentMessage;
import java.util.ArrayList;
import java.util.List;

public class GroupChat {
    
    private final List<AgentProfile> agents = new ArrayList<>();
    private final List<AgentNode> agentNodes = new ArrayList<>();
    private final MessageBus messageBus;
    private int currentIndex = 0;
    private int nodeIndex = 0;
    
    public enum RoutingStrategy {
        ROUND_ROBIN,
        SUPERVISOR
    }
    
    private RoutingStrategy routingStrategy = RoutingStrategy.ROUND_ROBIN;

    public GroupChat(MessageBus messageBus) {
        this.messageBus = messageBus;
    }

    public void addAgent(AgentProfile agent) {
        agents.add(agent);
    }

    public void addAgentNode(AgentNode agentNode) {
        agentNodes.add(agentNode);
    }

    public void setRoutingStrategy(RoutingStrategy strategy) {
        this.routingStrategy = strategy;
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
        
        if (routingStrategy == RoutingStrategy.ROUND_ROBIN) {
            AgentProfile next = agents.get(currentIndex);
            currentIndex = (currentIndex + 1) % agents.size();
            return next;
        } else {
            return agents.get(0);
        }
    }

    public AgentNode nextAgentNode() {
        if (agentNodes.isEmpty()) {
            throw new IllegalStateException("No agent nodes in the group chat");
        }
        
        if (routingStrategy == RoutingStrategy.ROUND_ROBIN) {
            AgentNode next = agentNodes.get(nodeIndex);
            nodeIndex = (nodeIndex + 1) % agentNodes.size();
            return next;
        } else {
            return agentNodes.get(0);
        }
    }
    
    public void broadcast(AgentMessage message, String sender) {
        messageBus.publish(new GroupChatMessage(sender, message));
    }

    public void broadcast(String message, String source) {
        messageBus.publish(new GroupChatMessage(source, AgentMessage.assistant(message)));
    }
}
