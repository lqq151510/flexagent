package org.flexagent.core.model;

public record ToolResult(
    String id,
    String name,
    Object result,
    String error
) {}
