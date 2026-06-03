package org.flexagent.examples.springboot;

import org.flexagent.langchain4j.FlexAgentChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgentController {

    private final FlexAgentChatModel agent;

    // Direct injection of the auto-configured FlexAgentChatModel bean
    public AgentController(FlexAgentChatModel agent) {
        this.agent = agent;
    }

    @GetMapping("/chat")
    public String chat(
            @RequestParam(value = "sessionId", defaultValue = "demo-session") String sessionId,
            @RequestParam(value = "message", defaultValue = "Hello") String message
    ) {
        return agent.generate(sessionId, message).content().text();
    }
}
