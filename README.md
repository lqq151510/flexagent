# FlexAgent
> Lightweight Java Agent Runtime Adapter for LangChain4j and OpenAI-compatible models.

[![CI Build](https://github.com/your-username/flexagent/actions/workflows/ci.yml/badge.svg)](https://github.com/your-username/flexagent/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-17%2B-blue)](https://img.shields.io/badge/Java-17%2B-blue)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-v0.1.0--SNAPSHOT-orange)](https://img.shields.io/badge/version-v0.1.0--SNAPSHOT-orange)

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
git clone <your-repository-url>
cd sdk

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

---

## 🛠️ 项目结构 (Modules)

* **`flexagent-core`**：核心抽象。包含 `AgentRuntime` SPI 定义、通用 `ToolDefinition`、推理流解析提取器与基础数据模型。**该模块物理上不依赖任何三方 LLM SDK**。
* **`flexagent-langchain4j`**：针对 LangChain4j 生态的适配实现，负责多轮 Agent Loop 的驱动与执行。
* **`flexagent-localharness`**：实验性模块。通过双向 WebSocket 连接外部 localharness 进程。
* **`flexagent-examples`**：示例 Demo。

---

## 📖 相关文档

* **设计思想与框架对比**：[FlexAgent vs LangChain4j vs Spring AI](docs/comparison-with-langchain4j-and-spring-ai.md)
* **未来演进路线图**：[ROADMAP.md](ROADMAP.md)
* **版本发布日志**：[CHANGELOG.md](CHANGELOG.md)
