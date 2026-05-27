package org.flexagent.core.model;

public enum RuntimeCapability {
    STREAMING,          // 支持流式文本/推理吐出
    TOOL_CALLING,       // 支持调用自定义工具
    THINKING_DELTA,     // 支持输出思维链
    COMPACTION,         // 支持上下文压缩
    LOCAL_PROCESS       // 依赖本地Go进程驱动
}
