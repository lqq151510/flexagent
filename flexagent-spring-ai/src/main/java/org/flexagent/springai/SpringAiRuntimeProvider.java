package org.flexagent.springai;

import org.flexagent.core.runtime.AgentRuntime;
import org.flexagent.core.runtime.AgentRuntimeConfig;
import org.flexagent.core.runtime.AgentRuntimeProvider;
import org.springframework.ai.chat.model.ChatModel;

public class SpringAiRuntimeProvider implements AgentRuntimeProvider {

    public static final String SPRING_AI_TYPE = "spring-ai";

    @Override
    public boolean supports(String type) {
        return SPRING_AI_TYPE.equalsIgnoreCase(type);
    }

    @Override
    public AgentRuntime create(AgentRuntimeConfig config) {
        ChatModel chatModel = config.model(ChatModel.class);
        if (chatModel == null) {
            throw new IllegalArgumentException("SpringAiRuntime requires a ChatModel delegate.");
        }
        return new SpringAiRuntime(chatModel);
    }
}
