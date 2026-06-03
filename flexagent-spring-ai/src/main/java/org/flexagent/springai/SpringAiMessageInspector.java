package org.flexagent.springai;

import org.flexagent.core.memory.compaction.MessageInspector;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.FunctionMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class SpringAiMessageInspector implements MessageInspector<Message> {
    private static final List<String> TOOL_REQUEST_KEYS = List.of(
            "toolCalls", "tool_calls", "functionCalls", "function_calls", "functionCall", "function_call"
    );
    private static final List<String> TOOL_ID_KEYS = List.of(
            "id", "toolCallId", "tool_call_id", "callId", "functionCallId", "function_call_id"
    );

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
        return message instanceof AssistantMessage && hasAnyMetadataValue(message, TOOL_REQUEST_KEYS);
    }

    @Override
    public boolean isToolResultMessage(Message message) {
        return message instanceof FunctionMessage;
    }

    @Override
    public boolean isMatchingToolPair(Message request, Message result) {
        if (!(request instanceof AssistantMessage) || !(result instanceof FunctionMessage)) {
            return false;
        }

        List<String> requestIds = extractToolIds(request);
        List<String> resultIds = extractToolIds(result);
        if (requestIds.isEmpty() || resultIds.isEmpty()) {
            return true;
        }
        return requestIds.stream().anyMatch(resultIds::contains);
    }

    private boolean hasAnyMetadataValue(Message message, List<String> keys) {
        Map<String, Object> metadata = metadata(message);
        if (metadata.isEmpty()) {
            return false;
        }
        for (String key : keys) {
            if (isPresent(metadata.get(key))) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractToolIds(Message message) {
        List<String> ids = new ArrayList<>();
        collectToolIds(metadata(message), ids);
        return ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
    }

    private void collectToolIds(Object value, List<String> ids) {
        if (value instanceof Map<?, ?> map) {
            for (String key : TOOL_ID_KEYS) {
                Object id = map.get(key);
                if (id instanceof String text) {
                    ids.add(text);
                }
            }
            for (Object nested : map.values()) {
                collectToolIds(nested, ids);
            }
        } else if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                collectToolIds(item, ids);
            }
        }
    }

    private boolean isPresent(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return !text.isBlank();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private Map<String, Object> metadata(Message message) {
        if (message instanceof AbstractMessage abstractMessage && abstractMessage.getMetadata() != null) {
            return abstractMessage.getMetadata();
        }
        return Map.of();
    }
}
