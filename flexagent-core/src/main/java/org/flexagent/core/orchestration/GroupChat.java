package org.flexagent.core.orchestration;

import java.util.ArrayList;
import java.util.List;

public class GroupChat {
    
    private final List<AgentProfile> agents = new ArrayList<>();
    private final MessageBus messageBus;
    private int currentIndex = 0;
    
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

    public void setRoutingStrategy(RoutingStrategy strategy) {
        this.routingStrategy = strategy;
    }
    
    public List<AgentProfile> getAgents() {
        return agents;
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
            // Placeholder for Supervisor routing
            return agents.get(0);
        }
    }
    
    public void broadcast(String message, String source) {
        messageBus.publish(new Event("group_message", source, message));
    }
}
