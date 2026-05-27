# 更新日志 (Changelog)

所有 FlexAgent 的重大更新与版本迭代均记录于此。

---

## [0.1.0-SNAPSHOT] - 2026-05-27

### 🚀 新增特性
* **核心 Runtime SPI**：引入可插拔的 `AgentRuntime` 与 `AgentRuntimeProvider` SPI，支持通过 Java ServiceLoader 动态发现及加载后端。
* **解耦版工具抽象**：设计并交付了统一的 `ToolDefinition` 与 `ToolAdapter`，将业务 `@Tool` 定义与具体的底座大模型框架物理隔离开来。
* **推理 `<think>` 标签流式回溯提取器**：内置 `XmlThinkTagExtractor` 状态机，能实时在字符碎片流中剥离推理思考过程（`ThinkingDelta`）与回复文本（`TextDelta`）。
* **ToolCall 容灾机制 (ToolCallPolicy)**：提供 `STRICT`、`LENIENT` 及 `TEXT_FALLBACK` 等策略，解决由于大模型幻觉产生的 JSON 参数损坏导致崩溃的问题。
* **LangChain4j 运行时支持**：交付 `flexagent-langchain4j` 模块，完美支持主流的 OpenAI-compatible 模型。
* **实验性 localharness 适配器**：提供 `flexagent-localharness` 模块，支持双向 WebSocket 与外部 Go 进程 localharness 进行沙箱调试交互。

### 🛠️ 单元测试
* 对 `XmlThinkTagExtractor` 补齐了高碎片化数据包、未闭合标签及多段流式输出的极端情况单元测试。
* 交付了针对 `AgentRuntimeConfig`、`RuntimeTypes`、`ToolCallPolicy`、`ToolAdapter`、`ServiceLoaderRuntimeProvider` 等基础组件的全套单元测试，提升整体代码可信度。

### 📖 示例与文档
* 交付了 `flexagent-examples` 模块，包含招牌的直连 DeepSeek-R1 控制台 Tool Loop 示例 `DeepSeekAgentDemo`。
* 编写了中英文 README.md，明确了项目定位并给出了 10 分钟快速上手编译运行指令。
* 新增 `docs/comparison-with-langchain4j-and-spring-ai.md` 架构对比文档。
