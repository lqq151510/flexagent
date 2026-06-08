package org.flexagent.examples;

import org.flexagent.core.orchestration.AgentProfile;
import org.flexagent.core.orchestration.GroupChat;
import org.flexagent.core.orchestration.InMemoryMessageBus;
import org.flexagent.core.orchestration.MessageBus;

import java.util.Collections;

public class MultiAgentGroupChatDemo {

    public static void main(String[] args) {
        System.out.println("=== Multi-Agent Group Chat Demo ===");

        MessageBus messageBus = new InMemoryMessageBus();
        
        // Subscribe to group messages
        messageBus.subscribe(message -> {
            System.out.println("[" + message.sender() + "]: " + message.text());
        });

        GroupChat groupChat = new GroupChat(messageBus);
        groupChat.setRoutingStrategy(GroupChat.RoutingStrategy.ROUND_ROBIN);

        // Add some dummy agent profiles
        groupChat.addAgent(new AgentProfile("Alice", "Data Analyst", "You analyze data.", Collections.emptyList()));
        groupChat.addAgent(new AgentProfile("Bob", "Developer", "You write code.", Collections.emptyList()));
        groupChat.addAgent(new AgentProfile("Charlie", "QA", "You test code.", Collections.emptyList()));

        System.out.println("Agents in group chat:");
        for (AgentProfile agent : groupChat.getAgents()) {
            System.out.println(" - " + agent.name() + " (" + agent.role() + ")");
        }

        System.out.println("\nSimulating Round Robin Routing:");
        for (int i = 0; i < 5; i++) {
            AgentProfile nextAgent = groupChat.nextAgent();
            System.out.println("Turn " + (i + 1) + ": " + nextAgent.name());
            groupChat.broadcast("Hello, I am " + nextAgent.name() + " and it is my turn.", nextAgent.name());
        }
    }
}
