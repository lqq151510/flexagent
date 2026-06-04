# FlexAgent 路线图 (Roadmap)

我们采取以“MVP 最小可行”为核心、逐步向生态渗透的路线，确保每一阶段的交付都具备高可用性与扎实的测试覆盖率。

---

## 📌 v0.1: 基础骨架与核心可用
* **目标**：实现核心抽象解耦、打通招牌 DeepSeek 示例并配备全套持续集成 (CI)。
* **交付内容**：
  * [x] **Runtime SPI**：提供核心 `AgentRuntime`、`AgentRuntimeProvider` 等可插拔 SPI 实现。
  * [x] **LangChain4j 运行时**：实现对 Java 社区主流 SDK 的桥接。
  * [x] **Tool 调用解耦**：允许业务层定义通用的 `@Tool`，底层进行适配路由。
  * [x] **推理流解析**：实现流式 `<think>` 标签剥离状态机。
  * [x] **招牌示例**：提供 `DeepSeekAgentDemo`，完美演示思考流和工具调用控制台日志。
  * [x] **基础测试**：完备的单元测试（>= 15 个用例）。
  * [x] **CI 流程**：配置 GitHub Actions，在每次推送时进行 Maven 构建校验。

---

## 📌 v0.2: 开发者体验提升与完善
* **目标**：简化配置方式，降低上手门槛，支持更丰富的异常处理和更多 OpenAI-compatible 端点。
* **交付内容**：
  * [x] **极简 Builder API**：提供更便捷的实例化工具（例如 `FlexAgentChatModel.builder().langChain4j(model).tools(...).build()`）。
  * [x] **Ollama & Qwen 示例细化**：在 Examples 中正式交付本地 Ollama 和阿里云千问的完整运行示例。
  * [x] **ToolCall 异常自愈**：完善 `ToolCallPolicy` 的容错策略与错误上报机制。
  * [x] **更清晰的异常链**：为 Builder 误用、运行时初始化和 ToolCall 参数解析提供带有一步修复指引的自定义异常。

---

## 📌 v0.3: Spring Boot 生态整合
* **目标**：实现与 Java 传统后端生态的无缝连接，提供声明式的自动装配能力。
* **交付内容**：
  * [x] **`flexagent-spring-boot-starter`**：开发独立的 Spring Boot 起步依赖。
  * [x] **声明式 YAML 配置**：支持通过 `application.yml` 来定义运行期后端类型、大模型密钥等。
  * [x] **Spring Bean 工具自动注册**：自动扫描 Spring 容器中所有带有 `@FlexTool` 注解的 Bean 并转化为 Agent 工具。

---

## 📌 v0.4: 异构运行时与高级编排
* **目标**：扩展 Runtime 提供者，并为长对话、多工具链路补齐更强的上下文治理能力。
* **交付内容**：
  * [ ] **Spring AI Runtime Provider**：支持将 Spring AI 运行时作为后端的 SPI 提供者。
  * [ ] **MCP (Model Context Protocol) 运行时支持**：原生对接 MCP 服务端，加载外部通用工具。
  * [x] **滑动窗口上下文管理增强**：已支持 `SlidingWindow`、`Summary` 与 `ToolAware` 三类压缩策略。

---

## 📌 v0.5: Session Memory Foundation
* **目标**：为多轮智能体交互建立可复用的会话记忆底座，并保持无状态模式向下兼容。
* **交付内容**：
  * [x] **AgentMemory 抽象**：统一 `AgentMemory`、`AgentMessage` 与 `AgentSessionContext` 核心模型。
  * [x] **In-Memory 默认实现**：支持单机进程内会话隔离与 TTL 过期。
  * [x] **Redis Memory 实现**：支持跨进程历史恢复与过期续期。
  * [x] **Builder 接入点**：通过 `FlexAgentChatModel.Builder.memory(...)` 与 compaction API 组合使用。
  * [x] **基础回归测试**：覆盖多轮对话、工具调用记忆、会话隔离和 TTL。

---

## 📌 v0.6: Memory 产品化、CI 与合并质量 (Current Stage)
* **目标**：把 v0.5 的 Memory 基础能力沉淀为可直接交付给 Spring Boot 项目的接入体验，同时强化 CI、测试覆盖与 PR 合并质量。
* **交付内容**：
  * [x] **Spring Boot Memory/Redis 配置文档**：提供完整的 `application.yml` 配置说明、参数表和接入建议。
  * [x] **Redis 使用示例**：补齐 Starter 场景下的 Redis 配置示例与会话验证方法。
  * [x] **Session-Aware Spring Demo**：示例应用支持显式传入 `sessionId`，便于演示多轮隔离。
  * [x] **Starter 层回归测试**：覆盖自动配置后的 session isolation 与 TTL 过期行为。
  * [x] **CI Verify Gate**：GitHub Actions 升级为 `mvn -B -ntp clean verify`，并上传 Surefire / JaCoCo artifacts。
  * [x] **PR 合并质量模板**：PR 模板补充验证命令、风险说明、文档/changelog 状态与 reviewer checklist。
  * [x] **MCP 模块测试补齐**：为 MCP tool schema scanner 增加最小回归测试，避免模块长期无测试。
  * [ ] **生产级增强项**：补充更细粒度的 Memory 观测指标与可配置 Redis key namespace。
