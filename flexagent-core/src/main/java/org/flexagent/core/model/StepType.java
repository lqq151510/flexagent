package org.flexagent.core.model;

public enum StepType {
    TEXT_RESPONSE,
    STREAM_TOKEN,
    TOOL_CALL,
    SYSTEM_MESSAGE,
    COMPACTION,
    FINISH,
    ERROR,
    UNKNOWN
}
