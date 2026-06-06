package org.flexagent.langchain4j.memory;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.memory.compaction.MemoryCompactor;

import java.util.ArrayList;
import java.util.List;

/**
 * A memory compactor that uses LangChain4j ChatLanguageModel to summarize the history.
 */
public class LangChain4jMemoryCompactor implements MemoryCompactor {
    private final ChatLanguageModel chatLanguageModel;
    private final String summaryPromptTemplate;

    public LangChain4jMemoryCompactor(ChatLanguageModel chatLanguageModel) {
        this(chatLanguageModel, "Please summarize the following conversation history into a concise context. Keep the important facts and decisions:");
    }

    public LangChain4jMemoryCompactor(ChatLanguageModel chatLanguageModel, String summaryPromptTemplate) {
        this.chatLanguageModel = chatLanguageModel;
        this.summaryPromptTemplate = summaryPromptTemplate;
    }

    @Override
    public List<AgentMessage> compact(List<AgentMessage> history) {
        if (history == null || history.size() <= 1) {
            return history;
        }

        StringBuilder conversationBuilder = new StringBuilder();
        List<AgentMessage> systemMessages = new ArrayList<>();
        
        for (AgentMessage msg : history) {
            if ("system".equals(msg.role())) {
                systemMessages.add(msg);
            } else {
                conversationBuilder.append(msg.role().toUpperCase()).append(": ");
                if (msg.text() != null) {
                    conversationBuilder.append(msg.text());
                }
                if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                    conversationBuilder.append(" [Used Tools: ").append(msg.toolCalls().size()).append("]");
                }
                conversationBuilder.append("\n");
            }
        }

        String prompt = summaryPromptTemplate + "\n\n" + conversationBuilder.toString();
        
        String summary = chatLanguageModel.generate(prompt);

        List<AgentMessage> compacted = new ArrayList<>();
        compacted.addAll(systemMessages);
        compacted.add(AgentMessage.system("Summary of previous conversation:\n" + summary));

        return compacted;
    }
}
