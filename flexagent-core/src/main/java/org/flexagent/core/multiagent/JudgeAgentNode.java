package org.flexagent.core.multiagent;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.orchestration.GroupChat;

import java.util.Map;

/**
 * A specialized AgentNode that signals the end of a GroupChat discussion.
 */
public class JudgeAgentNode implements AgentNode {

    private final String name;
    private final String description;
    private final GroupChat groupChat;
    private final AgentNode underlyingAgent;

    public JudgeAgentNode(String name, String description, GroupChat groupChat, AgentNode underlyingAgent) {
        this.name = name;
        this.description = description;
        this.groupChat = groupChat;
        this.underlyingAgent = underlyingAgent;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public AgentMessage execute(String prompt, Map<String, Object> variables) {
        // Mark the chat as finished
        if (groupChat != null) {
            groupChat.setFinished(true);
        }
        
        if (underlyingAgent != null) {
            return underlyingAgent.execute(prompt, variables);
        }
        
        return AgentMessage.assistant("Discussion concluded.");
    }
}
