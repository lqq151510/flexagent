package org.flexagent.springai;

import org.flexagent.core.memory.compaction.MessageInspector;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

public class SpringAiMessageInspector implements MessageInspector<Message> {

    @Override
    public int estimateTokenCount(Message message) {
        if (message == null || message.getContent() == null) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(message.getContent().toString().length() / 4.0));
    }

    @Override
    public boolean isSystemMessage(Message message) {
        return message instanceof SystemMessage;
    }

    @Override
    public boolean isToolRequestMessage(Message message) {
        if (message instanceof AssistantMessage am) {
            return am.hasToolCalls();
        }
        return false;
    }

    @Override
    public boolean isToolResultMessage(Message message) {
        return message instanceof ToolResponseMessage;
    }

    @Override
    public boolean isMatchingToolPair(Message request, Message result) {
        if (request instanceof AssistantMessage am && result instanceof ToolResponseMessage tm) {
            if (am.hasToolCalls()) {
                return am.getToolCalls().stream()
                        .anyMatch(tc -> tm.getResponses().stream()
                                .anyMatch(tr -> tr.id().equals(tc.id())));
            }
        }
        return false;
    }
}
