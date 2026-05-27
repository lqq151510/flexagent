package org.flexagent.langchain4j;

import org.flexagent.core.runtime.AgentRuntime;
import org.flexagent.core.runtime.AgentRuntimeConfig;
import org.flexagent.core.runtime.AgentRuntimeProvider;
import org.flexagent.core.runtime.RuntimeTypes;
import dev.langchain4j.model.chat.ChatLanguageModel;

public class LangChain4jRuntimeProvider implements AgentRuntimeProvider {
    @Override
    public boolean supports(String type) {
        return RuntimeTypes.LANGCHAIN4J.equalsIgnoreCase(type);
    }

    @Override
    public AgentRuntime create(AgentRuntimeConfig config) {
        ChatLanguageModel model = config.model(ChatLanguageModel.class);
        if (model == null) {
            throw new IllegalArgumentException("LangChain4jRuntime requires a ChatLanguageModel delegate model.");
        }
        return new LangChain4jRuntime(model);
    }
}
