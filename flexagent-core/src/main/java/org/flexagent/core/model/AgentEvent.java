package org.flexagent.core.model;

public sealed interface AgentEvent permits ThinkingDelta, TextDelta {}
