# FlexAgent
> Lightweight Java Agent Runtime Adapter for LangChain4j and OpenAI-compatible models.

[![CI Build](https://github.com/lqq151510/flexagent/actions/workflows/maven.yml/badge.svg)](https://github.com/lqq151510/flexagent/actions/workflows/maven.yml)
[![Java](https://img.shields.io/badge/Java-21%2B-blue)](https://img.shields.io/badge/Java-21%2B-blue)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-v1.5.0-blue)](https://img.shields.io/badge/version-v1.5.0-blue)

FlexAgent 是一个轻量级、企业级的 Java Agent Runtime 适配层。它用于解耦业务工具与底层模型运行时，优先支持 LangChain4j、OpenAI-compatible 模型与推理流解析。
在最新的 `v1.5.0` 中，FlexAgent 进化为具备完整 **分布式流转、微服务注册与发现、大规模流式调度抗压** 能力的企业级产品。

> ⚠️ **免责声明 (Disclaimer)**
> 本项目是社区驱动的第三方适配层，**并非 Google 官方项目，与 Google、Gemini、Antigravity 没有任何官方或法律关联**。

---

## 🚀 10 分钟快速上手

你可以非常方便地在本地克隆并运行本项目，无需安装任何外部引擎。

### 1. 克隆与构建
确保你的环境已安装 JDK 21 和 Maven。
```bash
# 克隆项目
git clone https://github.com/lqq151510/flexagent.git
cd flexagent

# 编译、运行所有集成测试
mvn -B -ntp clean verify
```

### 2. 运行招牌 DeepSeek 示例
设置你的 DeepSeek API Key 并启动 Demo：
```bash
export DEEPSEEK_API_KEY="your-actual-api-key"

mvn -pl flexagent-examples exec:java -Dexec.mainClass="org.flexagent.examples.DeepSeekAgentDemo"
```

---

## 🌟 核心特性 (Key Features)

### 1. 基础架构解耦 (Decoupled Architecture)
* **多提供商支持**: 原生支持 LangChain4j, Spring AI, 和本地 MCP/LocalHarness。
* **极简接入**: 一套代码随意切换底层大模型。
* **MCP (Model Context Protocol) 动态热重载**: 接入任意符合 MCP 标准的服务端，动态插拔工具列表而无需重启项目。

### 2. 企业级多智能体协同 (Multi-Agent Orchestration)
* **DAG 工作流执行图**: 提供核心抽象 `WorkflowOrchestrator` 与 `AgentTaskNode`，允许按照有向无环图 (DAG) 编排复杂的 Agent 任务。
* **并发执行边界隔离**: 引入 `ParallelNode` 结合专用线程池执行并行 Agent 推理，并彻底解决上下文共享时的并发修改冲突（`CopyOnWriteArrayList`）。

### 3. 分布式容错与微服务 (Cloud & Microservices) 💥 `NEW in v1.5.0`
* **Agent-as-a-Service (Nacos)**: 独创 `flexagent-spring-cloud-starter` 模块。Spring Boot 启动时，`NacosAgentMetadataRegistrar` 自动将当前系统内的 Agent 能力注入 Nacos Metadata 中。
* **Remote Proxy 调度**: `RemoteAgentNode` 代理类借助带负载均衡的 `WebClient`，自动利用 Nacos 发现服务并通过 RPC 执行远端的 Agent。
* **Redis 分布式断点续传**: 提供 `CheckpointManager`。任意工作流中间节点宕机，可瞬间由其它微服务节点无缝接管并读取最新会话，进度 **0 丢失**。
* **异构模型熔断 (Resilience4j)**: 云端模型 API 服务（如 DeepSeek）不可用时，毫秒级开路并回退（Fallback）至本地小模型（如 Qwen），防止系统雪崩。
* **千亿并发流式抗压 (WebFlux/SSE)**: 基于 Reactor，支持高频大规模长连接响应。

### 4. 增强记忆体系 (Advanced Memory) 💥 `NEW in v1.4.0`
* **长期实体提取 (Long-Term Memory)**: `InMemoryLongTermMemory` 可对冗长对话进行大模型实体总结提取，并设有容量阀值（`MAX_ENTITIES=1000`）防止 OOM 内存泄漏。
* **长短记忆滑动窗口**: 自动管理上下文，保证大模型永远只吃最优质且未超长的 Prompt 上下文。

---

## 🛠️ 项目结构 (Modules)

* **`flexagent-core`**：核心抽象。包含 `AgentRuntime`、`WorkflowOrchestrator` 调度引擎、记忆管理等。
* **`flexagent-langchain4j` / `flexagent-spring-ai` / `flexagent-mcp`**：三套底层大模型的独立适配器。
* **`flexagent-spring-boot-starter`**：自动配置环境与注入 Beans。
* **`flexagent-spring-cloud-starter`**：新增！用于实现 Nacos 元数据注册及 `RemoteAgentNode` 代理 RPC 调度。
* **`flexagent-enterprise-tests`**：包含 Resilience4j、Testcontainers(Redis/Milvus)、Spring WebFlux 并发的真实压测集。
* **`flexagent-examples`**：最佳实践与演示 Demo。

---

## 🔧 代码示例

### 微服务架构下调用远端 Agent
```java
// 只需要声明 RemoteAgentNode 即可将任务甩给微服务集群中的其他机器！
AgentNode remoteDataAnalyst = new RemoteAgentNode("data-service", "DataAnalystAgent", loadBalancedWebClientBuilder);

WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(redisCheckpointManager);
orchestrator.addNode(new AgentTaskNode("step1", remoteDataAnalyst, null, "prompt", "result"));

// 容错与调度将自动交由 Nacos 和 Resilience4j 处理
orchestrator.run("Workflow-1", "step1", new HashMap<>());
```

### 单体架构的极简 Agent 调用
```java
try (FlexAgentChatModel agent = FlexAgentChatModel.builder()
        .langChain4j(delegateModel)
        .tools(new MyTools())
        .enableThinkingExtraction(true)
        .build()) {
    String answer = agent.generate("帮我调用工具完成任务");
}
```

---

## 📖 相关文档

* **设计思想与框架对比**：[FlexAgent vs LangChain4j vs Spring AI](docs/comparison-with-langchain4j-and-spring-ai.md)
* **Session Memory 快速开始**：[docs/memory_quickstart.md](docs/memory_quickstart.md)
* **Spring Boot Memory / Redis 配置**：[docs/spring-boot-memory.md](docs/spring-boot-memory.md)
* **上下文压缩与长对话控制**：[Context Compaction](docs/examples/context-compaction.md)
* **企业级能力验证与测试报告**：[Enterprise Testing Walkthrough](walkthrough.md) (含熔断、RPC、SSE压测说明)
* **版本发布日志**：[CHANGELOG.md](CHANGELOG.md)

---

## 🧩 开源维护与成熟度

* **持续集成**：每次 push / pull request 都会运行 Maven `verify`，执行 `flexagent-enterprise-tests` 分布式限流集成测试，并上传测试与覆盖率报告。
* **版本记录**：通过 `CHANGELOG.md` 维护每次发布的功能变更与演进说明。
* **模块化交付**：核心、适配器、示例、Spring Cloud Starter 独立发版模块维护，便于分层演进与高度复用。
