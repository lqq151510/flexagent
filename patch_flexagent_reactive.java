    public Flux<Step> streamSteps(String sessionId, String prompt) {
        return Flux.create((FluxSink<Step> sink) -> {
            AgentSessionContext.set(sessionId);
            try {
                boolean hasMemory = (delegate.memory != null && sessionId != null && !"stateless".equals(sessionId));

                if (hasMemory) {
                    List<AgentMessage> agentHistory = delegate.memory.getMessages(sessionId);
                    List<ChatMessage> chatHistory = new ArrayList<>();
                    if (agentHistory != null && !agentHistory.isEmpty()) {
                        FlexAgentObservationUtils.recordMemoryHit(sessionId, true);
                        for (AgentMessage am : agentHistory) {
                            chatHistory.add(delegate.toChatMessage(am));
                        }
                    } else {
                        FlexAgentObservationUtils.recordMemoryHit(sessionId, false);
                    }

                    if (chatHistory.isEmpty()) {
                        chatHistory = new ArrayList<>();
                    }

                    chatHistory = delegate.withInitialSystemMessages(chatHistory);

                    if (delegate.activeRuntime instanceof LangChain4jRuntime lc4jRuntime) {
                        lc4jRuntime.setHistoryMessages(chatHistory);
                        lc4jRuntime.setSessionId(sessionId);
                    }
                } else {
                    if (delegate.activeRuntime instanceof LangChain4jRuntime lc4jRuntime) {
                        lc4jRuntime.setHistoryMessages(delegate.withInitialSystemMessages(new ArrayList<>()));
                        lc4jRuntime.setSessionId(sessionId);
                    }
                }

                delegate.activeRuntime.send(prompt);

                AtomicBoolean keepPolling = new AtomicBoolean(true);
                StringBuilder contentBuilder = new StringBuilder();

                sink.onCancel(() -> keepPolling.set(false));
                sink.onDispose(() -> keepPolling.set(false));

                Runnable pollingTask = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            while (keepPolling.get()) {
                                Step step = delegate.activeRuntime.pollStep(100, TimeUnit.MILLISECONDS);
                                if (step == null) continue;

                                sink.next(step);

                                if (step.status() == org.flexagent.core.model.StepStatus.ERROR) {
                                    sink.complete();
                                    return;
                                }

                                if (step.type() == org.flexagent.core.model.StepType.TOOL_CALL && !step.toolCalls().isEmpty()) {
                                    for (ToolCall toolCall : step.toolCalls()) {
                                        ToolResult toolResult = null;
                                        for (CustomToolExecutor executor : delegate.customToolExecutors) {
                                            if (executor.supports(toolCall.name())) {
                                                toolResult = executor.execute(toolCall);
                                                break;
                                            }
                                        }
                                        if (toolResult == null) {
                                            toolResult = delegate.toolAdapter.execute(toolCall);
                                        }
                                        delegate.activeRuntime.sendToolResult(toolResult);
                                        
                                        Step toolDoneStep = new Step(toolCall.id(), 0, org.flexagent.core.model.StepType.TOOL_CALL, org.flexagent.core.model.StepSource.SYSTEM, null, org.flexagent.core.model.StepStatus.DONE, "TOOL_DONE", null, null, null, null, null, null, null, null);
                                        sink.next(toolDoneStep);
                                    }
                                }

                                if (step.contentDelta() != null && !step.contentDelta().isEmpty()) {
                                    contentBuilder.append(step.contentDelta());
                                }

                                if (Boolean.TRUE.equals(step.isCompleteResponse()) && step.type() == org.flexagent.core.model.StepType.TEXT_RESPONSE) {
                                    if (hasMemory) {
                                        if (delegate.activeRuntime instanceof LangChain4jRuntime lc4jRuntime) {
                                            List<ChatMessage> updatedMessages = lc4jRuntime.getChatMessages();
                                            List<AgentMessage> updatedAgentMessages = new ArrayList<>();
                                            for (ChatMessage cm : updatedMessages) {
                                                updatedAgentMessages.add(delegate.toAgentMessage(cm));
                                            }
                                            delegate.memory.clear(sessionId);
                                            delegate.memory.addMessages(sessionId, updatedAgentMessages);
                                        } else {
                                            delegate.memory.addMessage(sessionId, AgentMessage.user(prompt));
                                            delegate.memory.addMessage(sessionId, AgentMessage.assistant(contentBuilder.toString()));
                                        }
                                    }
                                    sink.complete();
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            sink.error(new FlexAgentException("FlexAgent reactive agent execution failed", e));
                        } finally {
                            AgentSessionContext.clear();
                        }
                    }
                };

                Schedulers.boundedElastic().schedule(pollingTask);

            } catch (Exception e) {
                sink.error(e);
            }
        });
    }
