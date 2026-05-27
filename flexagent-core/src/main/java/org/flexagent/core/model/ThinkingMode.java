package org.flexagent.core.model;

public enum ThinkingMode {
    NONE,               // 普通大模型，无思考输出
    XML_THINK_TAG,      // R1/Qwen等，在文本中带 <think> 标签输出
    REASONING_CONTENT,  // 平台直接返回 reasoning_content 字段
    PROVIDER_NATIVE     // Gemini等由服务商提供的原生思维链模式
}
