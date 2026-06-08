package org.flexagent.core;

import org.flexagent.core.exception.FlexAgentException;
import org.flexagent.core.memory.AgentMemory;
import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.memory.AgentSessionContext;
import org.flexagent.core.model.ToolCall;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.runtime.AgentRuntime;
import org.flexagent.core.runtime.FlexAgentObservationUtils;
import org.flexagent.core.strategy.AgentStrategy;
import org.flexagent.core.strategy.ReActStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The core facade for FlexAgent, decoupled from any specific LLM framework (e.g., LangChain4j or Spring AI).
 */
public class FlexAgentClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(FlexAgentClient.class);

    private final AgentRuntime activeRuntime;
    private final AgentStrategy strategy;
    private final AgentMemory memory;
    private final Function<ToolCall, ToolResult> toolExecutor;
    private final List<AgentMessage> initialSystemMessages;

    private FlexAgentClient(Builder builder) {
        this.activeRuntime = builder.activeRuntime;
        this.strategy = builder.strategy != null ? builder.strategy : new ReActStrategy();
        this.memory = builder.memory;
        this.toolExecutor = builder.toolExecutor;
        this.initialSystemMessages = builder.initialSystemMessages != null ? builder.initialSystemMessages : new ArrayList<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public AgentMessage generate(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Messages cannot be empty");
        }
        
        String prompt = "";
        AgentMessage lastMessage = messages.get(messages.size() - 1);
        boolean extractPrompt = "user".equals(lastMessage.role());
        if (extractPrompt) {
            prompt = lastMessage.text();
        }
        
        List<AgentMessage> history = new ArrayList<>();
        boolean hasSystem = false;
        for (AgentMessage m : messages) {
            if ("system".equals(m.role())) {
                hasSystem = true;
                break;
            }
        }
        if (!hasSystem) {
            history.addAll(initialSystemMessages);
        }
        
        if (extractPrompt) {
            history.addAll(messages.subList(0, messages.size() - 1));
        } else {
            history.addAll(messages);
        }
        
        activeRuntime.setHistoryMessages(history);
        String sessionId = AgentSessionContext.get();
        if (sessionId != null) {
            activeRuntime.setSessionId(sessionId);
        }

        try {
            return this.strategy.execute(prompt, this.activeRuntime, toolExecutor);
        } catch (FlexAgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error executing FlexAgent Agent Runtime", e);
            throw new FlexAgentException("FlexAgent agent execution failed: " + e.getMessage(), e);
        }
    }

    public AgentMessage generate(String prompt) {
        String sessionId = AgentSessionContext.get();
        boolean hasMemory = (this.memory != null && sessionId != null);

        log.info("Generating response for prompt length: {}", prompt.length());

        if (hasMemory) {
            List<AgentMessage> agentHistory = this.memory.getMessages(sessionId);
            if (agentHistory != null && !agentHistory.isEmpty()) {
                FlexAgentObservationUtils.recordMemoryHit(sessionId, true);
                activeRuntime.setHistoryMessages(agentHistory);
            } else {
                FlexAgentObservationUtils.recordMemoryHit(sessionId, false);
                activeRuntime.setHistoryMessages(initialSystemMessages);
            }
            activeRuntime.setSessionId(sessionId);
        } else {
            activeRuntime.setHistoryMessages(initialSystemMessages);
            activeRuntime.setSessionId(sessionId);
        }

        try {
            AgentMessage resultMessage = this.strategy.execute(prompt, this.activeRuntime, toolExecutor);

            if (hasMemory) {
                // If the runtime maintains history internally (like LangChain4jRuntime), sync it back
                List<AgentMessage> updatedMessages = activeRuntime.getHistoryMessages();
                if (updatedMessages != null && !updatedMessages.isEmpty()) {
                    this.memory.clear(sessionId);
                    this.memory.addMessages(sessionId, updatedMessages);
                } else {
                    this.memory.addMessage(sessionId, AgentMessage.user(prompt));
                    this.memory.addMessage(sessionId, AgentMessage.assistant(resultMessage.text()));
                }
            }
            return resultMessage;
        } catch (FlexAgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error executing FlexAgent Agent Runtime", e);
            throw new FlexAgentException("FlexAgent agent execution failed: " + e.getMessage(), e);
        }
    }

    public AgentMessage generate(String sessionId, String userMessage) {
        AgentSessionContext.set(sessionId);
        try {
            return generate(userMessage);
        } finally {
            AgentSessionContext.clear();
        }
    }

    public Flux<String> stream(List<AgentMessage> messages) {
        return Flux.create(emitter -> {
            if (messages == null || messages.isEmpty()) {
                emitter.error(new IllegalArgumentException("Messages cannot be empty"));
                return;
            }
            
            String prompt = "";
            AgentMessage lastMessage = messages.get(messages.size() - 1);
            boolean extractPrompt = "user".equals(lastMessage.role());
            if (extractPrompt) {
                prompt = lastMessage.text();
            }
            
            List<AgentMessage> history = new ArrayList<>();
            boolean hasSystem = false;
            for (AgentMessage m : messages) {
                if ("system".equals(m.role())) {
                    hasSystem = true;
                    break;
                }
            }
            if (!hasSystem) {
                history.addAll(initialSystemMessages);
            }
            
            if (extractPrompt) {
                history.addAll(messages.subList(0, messages.size() - 1));
            } else {
                history.addAll(messages);
            }
            
            activeRuntime.setHistoryMessages(history);
            String sessionId = AgentSessionContext.get();
            if (sessionId != null) {
                activeRuntime.setSessionId(sessionId);
            }

            try {
                this.strategy.executeStream(prompt, this.activeRuntime, toolExecutor, token -> {
                    emitter.next(token);
                });
                emitter.complete();
            } catch (Exception e) {
                log.error("Error executing FlexAgent stream", e);
                emitter.error(new FlexAgentException("FlexAgent stream execution failed: " + e.getMessage(), e));
            }
        });
    }

    public Flux<String> stream(String prompt) {
        return Flux.create(emitter -> {
            String sessionId = AgentSessionContext.get();
            boolean hasMemory = (this.memory != null && sessionId != null);

            log.info("Generating stream for prompt length: {}", prompt.length());

            if (hasMemory) {
                List<AgentMessage> agentHistory = this.memory.getMessages(sessionId);
                if (agentHistory != null && !agentHistory.isEmpty()) {
                    FlexAgentObservationUtils.recordMemoryHit(sessionId, true);
                    activeRuntime.setHistoryMessages(agentHistory);
                } else {
                    FlexAgentObservationUtils.recordMemoryHit(sessionId, false);
                    activeRuntime.setHistoryMessages(initialSystemMessages);
                }
                activeRuntime.setSessionId(sessionId);
            } else {
                activeRuntime.setHistoryMessages(initialSystemMessages);
                activeRuntime.setSessionId(sessionId);
            }

            try {
                AgentMessage resultMessage = this.strategy.executeStream(prompt, this.activeRuntime, toolExecutor, token -> {
                    emitter.next(token);
                });

                if (hasMemory) {
                    List<AgentMessage> updatedMessages = activeRuntime.getHistoryMessages();
                    if (updatedMessages != null && !updatedMessages.isEmpty()) {
                        this.memory.clear(sessionId);
                        this.memory.addMessages(sessionId, updatedMessages);
                    } else {
                        this.memory.addMessage(sessionId, AgentMessage.user(prompt));
                        this.memory.addMessage(sessionId, AgentMessage.assistant(resultMessage.text()));
                    }
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("Error executing FlexAgent stream", e);
                emitter.error(new FlexAgentException("FlexAgent stream execution failed: " + e.getMessage(), e));
            }
        });
    }

    public Flux<String> stream(String sessionId, String userMessage) {
        AgentSessionContext.set(sessionId);
        try {
            return stream(userMessage);
        } finally {
            AgentSessionContext.clear();
        }
    }

    @Override
    public void close() throws Exception {
        if (this.activeRuntime != null) {
            this.activeRuntime.close();
        }
    }

    public AgentRuntime getActiveRuntime() {
        return activeRuntime;
    }

    public static class Builder {
        private AgentRuntime activeRuntime;
        private AgentStrategy strategy;
        private AgentMemory memory;
        private Function<ToolCall, ToolResult> toolExecutor;
        private List<AgentMessage> initialSystemMessages;

        public Builder activeRuntime(AgentRuntime runtime) {
            this.activeRuntime = runtime;
            return this;
        }

        public Builder strategy(AgentStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder memory(AgentMemory memory) {
            this.memory = memory;
            return this;
        }

        public Builder toolExecutor(Function<ToolCall, ToolResult> toolExecutor) {
            this.toolExecutor = toolExecutor;
            return this;
        }

        public Builder initialSystemMessages(List<AgentMessage> initialSystemMessages) {
            this.initialSystemMessages = initialSystemMessages;
            return this;
        }

        public FlexAgentClient build() {
            if (activeRuntime == null) {
                throw new IllegalStateException("activeRuntime must be provided");
            }
            if (toolExecutor == null) {
                throw new IllegalStateException("toolExecutor must be provided");
            }
            return new FlexAgentClient(this);
        }
    }
}
