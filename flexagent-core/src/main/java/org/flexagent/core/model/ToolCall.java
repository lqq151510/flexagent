package org.flexagent.core.model;

import java.util.Map;

public record ToolCall(
    String id,
    String name,
    Map<String, Object> arguments,
    String argumentsJson,
    String canonicalPath
) {}
