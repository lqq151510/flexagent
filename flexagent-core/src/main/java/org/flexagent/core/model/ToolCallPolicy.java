package org.flexagent.core.model;

public enum ToolCallPolicy {
    STRICT,             // 格式错误直接报错，拒绝执行
    LENIENT,            // 尝试对 JSON 格式进行宽松修复
    TEXT_FALLBACK       // 格式解析失败则回退，把调用请求直接作为普通文本呈现给用户
}
