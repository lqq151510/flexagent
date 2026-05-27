# 更新日志 (Changelog)

所有 FlexAgent 的重大更新与版本迭代均记录于此。

## [0.3.0] - 2026-05-27

### 🚀 新增特性与重构
* **完全独立的原生注解**：新增原生工具注解 `@FlexTool` 和参数注解 `@FlexParam`，完全解耦 LangChain4j 等第三方工具注解，实现了核心层的完全自治。
* **双注解兼容性适配**：重构 `ToolAdapter` 以同时扫描并识别 `@FlexTool` 与 LangChain4j 的 `@Tool`（降级兼容），确保平滑无痛迁移。
* **Jackson 原生 JSON Schema 生成**：新增 `ToolSchemaGenerator` 与 `FlexToolScanner`，绕过 LangChain4j 工具参数提取，自主生成完美的标准参数描述。
* **官方 Spring Boot Starter 支持**：交付了 `flexagent-spring-boot-starter` 起步依赖模块：
  * 支持以 `flexagent` 为前缀的配置项（映射至 `FlexAgentProperties` 绑定）。
  * 核心自动配置类 `FlexAgentAutoConfiguration` 负责挂载 `FlexAgentChatModel` 并从容器 `ApplicationContext` 自动扫描带有 `@FlexTool` 方法的 Beans 注入为 Tools。
  * 采用 Spring Boot 3.x 标准自动配置 imports 规范。

### 🛠️ 测试与示例对齐
* **自动化测试覆盖**：
  * `FlexToolScannerTest` 和 `ToolSchemaGeneratorTest` 覆盖原生工具扫描与 JSON Schema 正确性验证。
  * `LangChain4jToolCompatibilityTest` 验证双注解的无缝过渡和兼容。
  * `SpringBootAutoConfigurationTest` 覆盖轻量 Spring 上下文的属性解析及 Bean 工具自动装配行为。
* **Spring Boot 示例程序**：新增 `SpringDemoApplication` 与 `AgentController` (REST 终点 `/chat`)，完整展示如何在 Web 开发中秒级接入 FlexAgent。

---

## [0.2.0] - 2026-05-27

### 🚀 新增特性与重构
* **生命周期常驻重构**：`FlexAgentChatModel` 继承 `AutoCloseable`，将 SPI 加载及 `AgentRuntime` 的初始化工作移至构造函数，避免在每次调用 `generate()` 时重复初始化和销毁 Runtime 带来的性能与上下文丢失问题。
* **极简 Builder API 链式注入**：简化了 Builder 链式调用 API，支持 `.runtime(RuntimeTypes.LANGCHAIN4J)`、`.model(delegateModel)`、`.tools(tool1, tool2)`、`.enableThinkingExtraction(true)` 等极简配置形式。
* **推理模型智能自动探测**：若 modelName 或底座 model 实例名称中包含 `"reasoner"` 或 `"r1"`，则自动开启 `XML_THINK_TAG` 状态机以抽取剥离推理流的 `<think>` 标签。
* **标准化自定义异常体系**：引入 `FlexAgentException` 及其具体子类：
  * `ProviderNotFoundException`：在 classpath 中找不到对应 SPI 运行时或发现重复冲突时抛出，提供明确的 classpath 排障动作指引。
  * `RuntimeInitializationException`：当运行时连接或后端底层初始化（如 WebSocket 连接或 Harness 进程拉起）失败时抛出。
  * `ToolInvocationException`：工具反射调用发生异常时抛出。

### 🛠️ 测试与示例对齐
* **全面对齐示例**：更新了 `DeepSeekAgentDemo`、`QwenAgentDemo`、`OllamaReasoningDemo` 以适配 v0.2.0 常驻生命周期的 AutoCloseable 用法和极简 Builder。
* **单元/集成测试补齐**：编写了 `FlexAgentExceptionTest` 验证异常层级与消息传输；编写了 `FlexAgentChatModelBuilderTest` 验证 Builder API 参数解析和推理模型自动探测逻辑。

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
