package org.flexagent.core.model;

public record UsageMetadata(
    Integer promptTokenCount,
    Integer cachedContentTokenCount,
    Integer candidatesTokenCount,
    Integer thoughtsTokenCount,
    Integer totalTokenCount
) {}
