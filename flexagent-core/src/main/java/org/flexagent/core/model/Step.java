package org.flexagent.core.model;

import java.util.List;

public record Step(
    String id,
    int stepIndex,
    StepType type,
    StepSource source,
    StepTarget target,
    StepStatus status,
    String content,
    String contentDelta,
    String thinking,
    String thinkingDelta,
    List<ToolCall> toolCalls,
    String error,
    Boolean isCompleteResponse,
    Object structuredOutput,
    UsageMetadata usageMetadata
) {}
