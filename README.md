# FlexAgent
> Lightweight Java Agent Runtime Adapter for LangChain4j and OpenAI-compatible models.

[![CI Build](https://github.com/lqq151510/flexagent/actions/workflows/maven.yml/badge.svg)](https://github.com/lqq151510/flexagent/actions/workflows/maven.yml)
[![Java](https://img.shields.io/badge/Java-21%2B-blue)](https://img.shields.io/badge/Java-21%2B-blue)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-v0.5.0-blue)](https://img.shields.io/badge/version-v0.5.0-blue)

FlexAgent 是一个轻量级 Java Agent Runtime 适配层，用于解耦业务工具与底层模型运行时，优先支持 LangChain4j、OpenAI-compatible 模型与推理流解析。

> ⚠️ **免责声明 (Disclaimer)**
> 本项目是社区驱动的第三方适配层，**并非 Google 官方项目，与 Google、Gemini、Antigravity 没有任何官方或法律关联**。
> `flexagent-localharness` 是可选的本地运行时适配模块，**本项目不内置、不修改、不分发任何专有或受版权保护的 local harness 二进制文件**。

---

## 🚀 10 分钟快速上手

你可以非常方便地在本地克隆并运行本项目，无需安装任何外部引擎。

### 1. 克隆与构建
确保你的环境已安装 JDK 21 和 Maven。
```bash
# 克隆项目
git clone https://github.com/lqq151510/flexagent.git
cd flexagent

# 编译并运行所有单元测试
mvn clean test
```

### 2. 运行招牌 DeepSeek 示例
设置你的 DeepSeek API Key 并启动 Demo：
```bash
export DEEPSEEK_API_KEY="your-actual-api-key"

mvn -pl flexagent-examples exec:java -Dexec.mainClass="org.flexagent.examples.DeepSeekAgentDemo"
```
你将会在控制台上看到：
1. FlexAgent 通过 Java SPI 机制动态加载 `langchain4j` 后端。
2. 大模型（如 `deepseek-reasoner`）的流式思考过程以 `[Thinking] xxx` 标签实时输出。
3. FlexAgent 截获大模型的工具调用指令，在本地执行 Java 反射方法。
4. 返回工具执行结果，并由大模型给出最终的回答。

---

## 🌟 核心特性 (Key Features)

* **可插拔 Runtime SPI**：底层提供核心 `AgentRuntime` 接口。你可以使用 `LangChain4j` 原生驱动，也可以无缝切换至实验性的 `localharness` 外部引擎调试，而无需修改任何业务代码。
* **业务工具彻底解耦**：将业务层的 `@Tool` 注解方法抽象转化为通用的 `ToolDefinition`，底层适配器根据实际运行时进行参数映射，未来引入 Spring AI 时工具类无需重写。
* **推理 `<think>` 标签流式回溯解析**：针对 DeepSeek-R1 等推理模型，在流式接收过程中通过内置状态机精准分段剥离 `ThinkingDelta` 与 `TextDelta`，无惧网络分片导致的标签切碎或未闭合。
* **ToolCall 容灾策略**：内置 `STRICT`、`LENIENT` 和 `TEXT_FALLBACK` 策略，轻松应对模型生成的 JSON 幻觉和破碎参数输出。
* **Session Memory 与 TTL**：支持 `InMemoryAgentMemory` 与 `RedisAgentMemory`，可按 `sessionId` 进行会话隔离、跨请求记忆与过期清理。

### 极简 Builder 示例

```java
try (FlexAgentChatModel agent = FlexAgentChatModel.builder()
        .langChain4j(delegateModel)
        .tools(new MyTools())
        .lenientToolCalls()
        .enableThinkingExtraction(true)
        .build()) {
    String answer = agent.generate("帮我调用工具完成任务");
}
```

---

## 🛠️ 项目结构 (Modules)

* **`flexagent-core`**：核心抽象。包含 `AgentRuntime` SPI 定义、通用 `ToolDefinition`、推理流解析提取器与基础数据模型。**该模块物理上不依赖任何三方 LLM SDK**。
* **`flexagent-langchain4j`**：针对 LangChain4j 生态的适配实现，负责多轮 Agent Loop 的驱动与执行。
* **`flexagent-localharness`**：实验性模块。通过双向 WebSocket 连接外部 localharness 进程。
* **`flexagent-examples`**：示例 Demo。

---

## 📖 相关文档

* **设计思想与框架对比**：[FlexAgent vs LangChain4j vs Spring AI](docs/comparison-with-langchain4j-and-spring-ai.md)
* **Session Memory 快速开始**：[docs/memory_quickstart.md](docs/memory_quickstart.md)
* **Spring Boot Memory / Redis 配置**：[docs/spring-boot-memory.md](docs/spring-boot-memory.md)
* **上下文压缩与长对话控制**：[Context Compaction](docs/examples/context-compaction.md)
* **未来演进路线图**：[ROADMAP.md](ROADMAP.md)
* **版本发布日志**：[CHANGELOG.md](CHANGELOG.md)
* **维护说明**：[MAINTENANCE.md](MAINTENANCE.md)
* **Pull Request 指南**：[PULL_REQUEST_GUIDELINES.md](PULL_REQUEST_GUIDELINES.md)

---

## 🧩 开源维护与成熟度

* **持续集成**：每次 push / pull request 都会运行 Maven 构建与测试。
* **版本记录**：通过 `CHANGELOG.md` 维护每次发布的功能变更与演进说明。
* **贡献规范**：仓库包含 `CONTRIBUTING.md`、`SECURITY.md` 与 PR 模板，便于外部协作者参与。
* **模块化交付**：核心、适配器、示例、Spring Boot Starter 分模块维护，便于分层演进与复用。
