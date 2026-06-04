# FlexAgent Session Memory 快速开始指南

FlexAgent 在 v0.5.0 版本中引入了 **Session Memory (会话记忆)** 基础能力。这使得智能体不仅可以一次性地处理单次请求，还可以跨多次请求记住对话上下文（包括普通的聊天文本和智能体触发的工具调用记录），并支持会话间的安全隔离。

---

## 1. 核心概念

1. **`AgentMemory`**：会话记忆的持久化存储接口。
2. **`InMemoryAgentMemory`**：`AgentMemory` 的默认内存实现（线程安全，基于哈希表存储）。
3. **`AgentMessage`**：框架内通用的消息抽象模型（包含 `system`, `user`, `assistant`, `tool` 四种角色）。
4. **`AgentSessionContext`**：用于在当前线程中隐式传递 `sessionId` 的 `ThreadLocal` 状态容器。

---

## 2. 快速上手

### 2.1 引入 Memory 并构建智能体

你可以直接通过 `FlexAgentChatModel.builder()` 链式 API 来配置 `AgentMemory` 实例。

```java
import org.flexagent.core.memory.AgentMemory;
import org.flexagent.core.memory.InMemoryAgentMemory;
import org.flexagent.langchain4j.FlexAgentChatModel;

// 1. 初始化会话记忆组件
AgentMemory memory = new InMemoryAgentMemory();

// 2. 构建 FlexAgent 实例
FlexAgentChatModel agent = FlexAgentChatModel.builder()
        .model(yourLanguageModel)
        .memory(memory) // 注入 Memory 实例
        .build();
```

---

## 3. 进行多轮会话交互

为了进行多会话隔离，FlexAgent 提供了两种会话 ID 传递方式：**显式重载参数**与**线程上下文传递**。

### 方式 A：使用显式重载 API（推荐）

`FlexAgentChatModel` 提供了直接携带 `sessionId` 的重载 `generate` 方法：

```java
// 会话 A 的多轮交互
agent.generate("session-A", "你好，我是泽宝。"); 
agent.generate("session-A", "我刚刚说我叫什么名字？"); // 输出: "您刚刚说您叫泽宝。"

// 会话 B 的交互，与会话 A 隔离
agent.generate("session-B", "你好。");
agent.generate("session-B", "我刚刚说我叫什么名字？"); // 输出: "抱歉，您刚刚没有提到您的名字。"
```

### 方式 B：使用线程上下文 `AgentSessionContext`（常用于框架集成）

当你在原生的 LangChain4j 生态中调用通用的 `ChatLanguageModel` 接口，无法显式修改方法签名传递参数时，可以使用 `AgentSessionContext` 绑定会话 ID：

```java
import org.flexagent.core.memory.AgentSessionContext;

// 1. 在处理请求前绑定 sessionId
AgentSessionContext.set("session-123");

try {
    // 2. 正常调用原生的 generate 方法，底层将自动使用绑定的会话 ID 加载和更新历史
    Response<AiMessage> response = agent.generate(List.of(UserMessage.from("我的生日是08月15日")));
} finally {
    // 3. 处理完毕后务必清理，防止线程复用污染
    AgentSessionContext.clear();
}
```

---

## 4. 工具调用的多轮记忆支持

在 FlexAgent 中，**工具调用及执行结果**被作为第一等公民存储在会话历史中。如果智能体在第一轮中触发了自定义工具：
1. FlexAgent 会自动捕获 `ToolCall` 与其执行结果 `ToolResult`。
2. 在对话正常结束时，这些工具执行细节将被完整映射为通用 `AgentMessage` 保存在 memory 中。
3. 下一次调用时，这些工具调用历史将重新注入大模型，避免大模型因丢失过去的工具交互记录而对上下文感到困惑。

---

## 5. Spring Boot Starter 集成

如果你使用 `flexagent-spring-boot-starter`，可以直接通过配置文件启用 Memory，无需手动 new `InMemoryAgentMemory`：

```yaml
flexagent:
  runtime: langchain4j
  memory:
    type: in-memory
    ttl: 30m
```

切换到 Redis 也只需要改动配置：

```yaml
flexagent:
  runtime: langchain4j
  memory:
    type: redis
    ttl: 2h
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}
      database: 0
      timeout: 2000
```

更完整的 Spring Boot 接入方式、Redis 说明、以及 `sessionId` 透传示例见 [docs/spring-boot-memory.md](spring-boot-memory.md)。

---

## 6. 无状态行为向下兼容

当你的 FlexAgent 实例 **未配置 memory** 时，它将完全维持原有的无状态行为：
- 调用 `generate(List<ChatMessage> messages)` 时，直接取传入的完整 `messages` 列表作为本次对话的上下文。
- 每次 generate 结束不保留任何历史状态，完全交由上层调用方进行历史消息管理。
