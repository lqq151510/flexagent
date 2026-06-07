package org.flexagent.core.orchestration;

import java.util.List;

public record AgentProfile(String name, String role, String systemPrompt, List<Object> tools) {}
