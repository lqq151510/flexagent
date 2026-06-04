package org.flexagent.langchain4j.compaction;

import org.flexagent.core.memory.compaction.CompactionStrategy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.flexagent.core.memory.compaction.MessageInspector;

public class Langchain4jMessageInspector implements MessageInspector<ChatMessage> {

    @Override
    public int estimateTokenCount(ChatMessage message) {
        if (message == null || message.text() == null) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(message.text().length() / 4.0));
    }

    @Override
    public boolean isSystemMessage(ChatMessage message) {
        return message instanceof SystemMessage;
    }

    @Override
    public boolean isToolRequestMessage(ChatMessage message) {
        if (message instanceof AiMessage aiMsg) {
            return aiMsg.hasToolExecutionRequests();
        }
        return false;
    }

    @Override
    public boolean isToolResultMessage(ChatMessage message) {
        return message instanceof ToolExecutionResultMessage;
    }

    @Override
    public boolean isMatchingToolPair(ChatMessage request, ChatMessage result) {
        if (request instanceof AiMessage aiMsg && result instanceof ToolExecutionResultMessage toolRes) {
            if (aiMsg.hasToolExecutionRequests()) {
                return aiMsg.toolExecutionRequests().stream()
                        .anyMatch(req -> req.id().equals(toolRes.id()));
            }
        }
        return false;
    }
}
