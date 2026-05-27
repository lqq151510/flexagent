# FlexAgent 路线图 (Roadmap)

我们采取以“MVP 最小可行”为核心、逐步向生态渗透的路线，确保每一阶段的交付都具备高可用性与扎实的测试覆盖率。

---

## 📌 v0.1: 基础骨架与核心可用 (Current Stage)
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
  * [ ] **极简 Builder API**：提供更便捷的实例化工具（例如 `FlexAgentChatModel.builder().tools(...).build()`）。
  * [ ] **Ollama & Qwen 示例细化**：在 Examples 中正式交付本地 Ollama 和阿里云千问的完整运行示例。
  * [ ] **ToolCall 异常自愈**：完善 `ToolCallPolicy` 的容错策略与错误上报机制。
  * [ ] **更清晰的异常链**：为 SPI 发现失败、WebSocket 连接丢失等场景提供带有一步修复指引的自定义异常。

---

## 📌 v0.3: Spring Boot 生态整合
* **目标**：实现与 Java 传统后端生态的无缝连接，提供声明式的自动装配能力。
* **交付内容**：
  * [ ] **`flexagent-spring-boot-starter`**：开发独立的 Spring Boot 起步依赖。
  * [ ] **声明式 YAML 配置**：支持通过 `application.yml` 来定义运行期后端类型、大模型密钥等。
  * [ ] **Spring Bean 工具自动注册**：自动扫描 Spring 容器中所有带有 `@FlexTool` 注解的 Bean 并转化为 Agent 工具。

---

## 📌 v0.4: 异构运行时与高级编排
* **目标**：扩展 Runtime 提供者，支持 MCP 协议与多 Agent 协同。
* **交付内容**：
  * [ ] **Spring AI Runtime Provider**：支持将 Spring AI 运行时作为后端的 SPI 提供者。
  * [ ] **MCP (Model Context Protocol) 运行时支持**：原生对接 MCP 服务端，加载外部通用工具。
  * [ ] **滑动窗口上下文管理增强**：更精细的 Token 计算与自动压缩淘汰策略。
