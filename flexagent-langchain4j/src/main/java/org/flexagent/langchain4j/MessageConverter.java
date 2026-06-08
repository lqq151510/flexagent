package org.flexagent.langchain4j;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flexagent.core.model.ToolCall;
import org.flexagent.core.model.ToolCallPolicy;
import org.flexagent.core.memory.AgentMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.flexagent.core.util.FlexObjectMapper;

public class MessageConverter {
    private static final Logger log = LoggerFactory.getLogger(MessageConverter.class);
    private static final ObjectMapper objectMapper = FlexObjectMapper.getInstance();

    public static ChatMessage toChatMessage(AgentMessage msg) {
        if (msg == null) return null;
        switch (msg.role()) {
            case "system":
                return SystemMessage.from(msg.text());
            case "user":
                return UserMessage.from(msg.text());
            case "assistant":
            case "ai":
                if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                    List<dev.langchain4j.agent.tool.ToolExecutionRequest> requests = new ArrayList<>();
                    for (ToolCall tc : msg.toolCalls()) {
                        requests.add(dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                                .id(tc.id())
                                .name(tc.name())
                                .arguments(tc.argumentsJson())
                                .build());
                    }
                    if (msg.text() != null && !msg.text().isEmpty()) {
                        return new AiMessage(msg.text(), requests);
                    } else {
                        return AiMessage.from(requests);
                    }
                }
                return AiMessage.from(msg.text());
            case "tool":
                return ToolExecutionResultMessage.from(msg.toolId(), msg.toolName(), msg.text());
            default:
                throw new IllegalArgumentException("Unknown AgentMessage role: " + msg.role());
        }
    }

    public static AgentMessage toAgentMessage(ChatMessage msg, ToolCallPolicy policy) {
        if (msg == null) return null;
        if (msg instanceof SystemMessage) {
            return AgentMessage.system(msg.text());
        } else if (msg instanceof UserMessage) {
            return AgentMessage.user(msg.text());
        } else if (msg instanceof AiMessage aiMsg) {
            if (aiMsg.hasToolExecutionRequests()) {
                List<ToolCall> toolCalls = new ArrayList<>();
                for (dev.langchain4j.agent.tool.ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
                    try {
                        toolCalls.add(new ToolCall(
                                req.id(),
                                req.name(),
                                parseJsonToMapStrict(req.arguments()),
                                req.arguments(),
                                null
                        ));
                    } catch (Exception e) {
                        if (policy == ToolCallPolicy.TEXT_FALLBACK) {
                            return AgentMessage.assistant("Tool Call Fallback: " + req.id() + " failed to parse json. " + e.getMessage());
                        } else if (policy == ToolCallPolicy.STRICT) {
                            throw new RuntimeException("Failed to parse tool call arguments under STRICT policy: " + req.arguments(), e);
                        } else {
                            log.warn("LENIENT: Failed to parse tool call JSON", e);
                            toolCalls.add(new ToolCall(req.id(), req.name(), Collections.emptyMap(), req.arguments(), null));
                        }
                    }
                }
                return AgentMessage.assistant(aiMsg.text(), toolCalls);
            }
            return AgentMessage.assistant(aiMsg.text());
        } else if (msg instanceof ToolExecutionResultMessage toolMsg) {
            return AgentMessage.tool(toolMsg.id(), toolMsg.toolName(), toolMsg.text());
        } else {
            throw new IllegalArgumentException("Unknown ChatMessage type: " + msg.getClass().getName());
        }
    }

    private static Map<String, Object> parseJsonToMapStrict(String json) throws Exception {
        if (json == null || json.isEmpty()) return Collections.emptyMap();
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }
}
