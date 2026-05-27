package org.flexagent.core.model;

public record ToolDefinition(
    String name,
    String description,
    String parametersJsonSchema
) {}
