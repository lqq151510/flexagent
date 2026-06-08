# 更新日志 (Changelog)

所有 FlexAgent 的重大更新与版本迭代均记录于此。

## [1.2.0] - 2026-06-08

### 🚀 核心控制反转与无缝集成
* **FlexAgentClient 门面下沉**：将原本耦合在 LangChain4j 中的 Agent Loop、Tool Call 和 Memory 管理逻辑全面抽离并下沉至 `flexagent-core` 的 `FlexAgentClient` 中，实现核心调度的完全自治。
* **零侵入生态兼容**：
  * **Spring Boot Starter**：原生暴露 `FlexAgentClient` Bean 供新业务使用，同时保留原有 `FlexAgentChatModel` 桥接，旧业务零代码修改即可享受新引擎。
  * **Spring AI**：`SpringAiRuntime` 无缝适配新架构，将内部 FunctionCallback 请求完美转换为标准 `ToolCall` 协议。
  * **MCP**：`McpToolExecutor` 在新架构下稳定支持跨进程工具调用。

### ⚡ 极速并发优化
* **解除 Agent Loop 队列阻塞死锁**：修复了 `ReActStrategy` 轮询策略中引发的主线程强制 100ms 盲等机制，引入 `isCompleteResponse` 前置嗅探。
* **吞吐量跃升**：在 JMH 并发基准压测中，吞吐量从原本的 11 TPS 暴涨 17,000 倍达到 189,000+ TPS，框架层封装开销降至微秒级（~5μs）。

### 🐍 Python SDK 生态对齐
* **全面平齐 Java 最新协议**：`flexagent-sdk-python` 完全跟进 Java 端的 `AgentClient` 核心设计，包括统一的 ToolCall 执行和 AgentMessage 内存编解码方案，全量自动化测试 100% 通过。


## [1.1.0] - 2026-06-07

### 🚀 多智能体编排与测评生态
* **Multi-Agent 编排**：新增 `AgentProfile`、`GroupChat` 和 `MessageBus`，支持声明式多智能体群聊。
* **分级任务树**：新增 `HierarchicalStrategy` 主子代理委派策略，解决大模型上下文过载问题。
* **Python SDK 全面对齐**：`flexagent-sdk-python` 达成全核心特性对齐，包括 LLM Streaming 抽象、`@tool` 沙箱执行机制和滑动窗口记忆管理。
* **评测体系 (Benchmarking)**：新增 `flexagent-benchmark` 模块，提供 `DatasetLoader` 和 `AgentEvaluator` 自动统计大模型工具调用成功率。

## [1.0.0] - 2026-06-06

### 🚀 稳定版与 Web Console RAG 引擎集成
* **Web 控制台重构**：提供基于 SSE 流式更新的多轮对话 UI 与动态加载动画。
* **MCP/RAG 动态装配面板**：前端支持可视化修改 MCP 命令与向量库链接，后端动态重载相关组件。
* **RAG 向量数据库支持**：`flexagent-rag` 模块新增 `InMemoryVectorStore` 和 `MilvusVectorStore` 的热插拔支持。
* **生产级增强**：Spring AI 流式输出对齐、MCP 异步交互重构及使用监控。

## [0.7.0] - 2026-06-06

### 🚀 响应式与可观测性
* **响应式 API 支持**：引入 Project Reactor，新增 `FlexAgentReactiveChatModel` 桥接，支持 `Flux<AgentMessage>` 非阻塞流式交互。
* **Spring WebFlux 适配**：在 `flexagent-spring-boot-starter` 自动检测并装配 `FlexAgentReactiveChatModel` Bean。
* **Micrometer 接入**：在核心层封装 `FlexAgentObservationUtils`，实现对工具调用耗时 (`flexagent.tool.invoke.timer`)、会话记忆命中 (`flexagent.memory.hit`) 与 LLM Token 消耗 (`flexagent.llm.token.usage`) 的自动化监控打点。
* **v0.4 债务清空**：补齐了 Spring AI 运行时打通（解决参数格式反序列化痛点）及原生 MCP Client（支持进程启停与流式数据交互）。

## [0.6.0-SNAPSHOT] - 2026-06-03

### ✅ CI 与 PR 合并质量
* GitHub Actions 从 `mvn clean test` 升级为 `mvn -B -ntp clean verify`，让本地验证与 CI gate 保持一致。
* CI 新增 Surefire 测试报告与 JaCoCo 覆盖率报告 artifacts，便于 PR 失败时快速定位。
* 修复 tag 发布触发条件：`v*` tag 现在会触发 publish job，并在 verify 通过后执行发布。
* 发布包流程对 GitHub Packages 已存在版本的 `409 Conflict` 做幂等跳过，避免重跑 tag / main 发布时误报失败。
* PR 模板补充验证命令、风险/rollout、文档/changelog 与 reviewer checklist，减少合并前遗漏。

### 🧪 测试覆盖
* 父 POM 接入 JaCoCo，`verify` 阶段自动生成模块级覆盖率报告。
* `flexagent-mcp` 新增 `McpToolScannerTest`，验证 MCP mock tool schema 的结构与 required 参数。

### 📖 维护文档
* `CONTRIBUTING.md`、`MAINTENANCE.md`、`PULL_REQUEST_GUIDELINES.md` 同步为 `mvn -B -ntp clean verify` 验证标准。
* `ROADMAP.md` 将 v0.6 范围更新为 Memory 产品化、CI 与合并质量。

### 📖 Spring Boot Memory 产品化
* 新增 `docs/spring-boot-memory.md`，系统说明 `flexagent.memory.*` 配置项、`in-memory` / `redis` 两种模式、TTL 行为与生产建议。
* `docs/memory_quickstart.md` 增补 Spring Boot Starter 集成方式，并统一使用当前 Builder API。
* 中英文 `README` 增加 Session Memory / Redis 文档入口，强化 v0.5 Memory 能力的 discoverability。

### 🧪 示例与测试补强
* Spring Boot 示例 `AgentController` 改为显式接收 `sessionId`，便于直观演示会话隔离。
* Spring Boot 示例新增默认 `application.yml`，开箱即启用 `in-memory` + `30m TTL` 的 Memory 配置。
* `SpringBootAutoConfigurationTest` 补充 Starter 层的 session isolation 与 TTL 过期回归测试，验证自动配置后的 `FlexAgentChatModel` 会正确继承 Memory 行为。

## [0.5.0] - 2026-05-28

### 🧠 Session Memory 与上下文压缩
* 新增 `AgentMemory` / `AgentMessage` / `AgentSessionContext` 等会话记忆基础能力，为多轮对话提供跨请求历史保存与 session 隔离。
* 新增 `InMemoryAgentMemory` 默认实现，支持无外部依赖的本地内存记忆。
* 新增 `CompactionStrategy` 及其策略实现，支持 `SlidingWindowCompactionStrategy`、`SummaryCompactionStrategy` 和 `ToolAwareCompactionStrategy`。
* `FlexAgentChatModel.Builder` 新增 `memory(...)` 与 `compactionMaxMessages(...)` 等配置入口，保持不配置时的无状态兼容行为。

### 📖 文档与验证
* 新增会话记忆快速开始文档与上下文压缩示例文档。
* 新增记忆与压缩相关单元测试，确保 `mvn clean test` 可稳定通过。

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
