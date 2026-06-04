# FlexAgent Spring Boot Memory 与 Redis 配置指南

本文面向 `flexagent-spring-boot-starter` 使用者，目标是把 Session Memory 变成一个可以直接落地到 Spring Boot 服务里的能力，而不只是底层 API。

---

## 1. 能力概览

Starter 启用后，FlexAgent 会自动完成两件事：

1. 根据 `flexagent.memory.*` 配置创建 `AgentMemory` Bean。
2. 将该 Memory 自动注入 `FlexAgentChatModel`，使 `generate(sessionId, ...)` 具备多轮会话记忆、会话隔离与 TTL 过期能力。

当前支持两种 Memory 实现：

| 类型 | 适用场景 | 特点 |
| --- | --- | --- |
| `in-memory` | 单机开发、Demo、本地联调 | 零外部依赖，进程重启后历史丢失 |
| `redis` | 多实例部署、需要跨节点共享会话 | 支持会话恢复、统一 TTL、适合生产环境 |

---

## 2. 最小配置

### 2.1 In-Memory

```yaml
flexagent:
  runtime: langchain4j
  memory:
    type: in-memory
    ttl: 30m
```

说明：

* `type` 默认就是 `in-memory`。
* `ttl` 为空时表示不过期。
* In-Memory 适合本地开发与单实例服务，不适合作为跨实例共享存储。

### 2.2 Redis

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

说明：

* Redis 键前缀当前固定为 `flexagent:memory:session:`。
* 每次读取或写入该 session 时，TTL 都会被刷新，适合“活跃会话续期”的场景。
* 当 TTL 到期后，会话历史将自然失效；再次访问同一 `sessionId` 时，会以新会话重新开始。

---

## 3. 配置项说明

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `flexagent.memory.type` | `in-memory` | Memory 实现类型，支持 `in-memory` / `redis` |
| `flexagent.memory.ttl` | `null` | 会话过期时间，支持 Spring `Duration` 格式，如 `30m`、`2h`、`45s` |
| `flexagent.memory.redis.host` | `localhost` | Redis 主机 |
| `flexagent.memory.redis.port` | `6379` | Redis 端口 |
| `flexagent.memory.redis.password` | `null` | Redis 密码 |
| `flexagent.memory.redis.database` | `0` | Redis DB 索引 |
| `flexagent.memory.redis.timeout` | `2000` | 连接超时，单位毫秒 |

---

## 4. 在 Controller 中传递 `sessionId`

推荐在 Web 层显式传入 `sessionId`，而不是把会话隔离留给上层自己拼装历史消息。

```java
@RestController
public class AgentController {

    private final FlexAgentChatModel agent;

    public AgentController(FlexAgentChatModel agent) {
        this.agent = agent;
    }

    @GetMapping("/chat")
    public String chat(
            @RequestParam String sessionId,
            @RequestParam String message
    ) {
        return agent.generate(sessionId, message);
    }
}
```

这样做的效果是：

* 同一个 `sessionId` 会自动命中同一段历史。
* 不同 `sessionId` 之间天然隔离。
* 你不需要自己维护 `List<ChatMessage>` 历史列表。

---

## 5. 什么时候使用 `AgentSessionContext`

如果你的上层代码拿到的是通用 `ChatLanguageModel` 接口，而不是 `FlexAgentChatModel`，就可以在请求入口临时绑定 `AgentSessionContext`：

```java
AgentSessionContext.set(sessionId);
try {
    Response<AiMessage> response = chatLanguageModel.generate(List.of(UserMessage.from(message)));
} finally {
    AgentSessionContext.clear();
}
```

这个方式适合：

* 你在框架层只暴露 `ChatLanguageModel`。
* 你不方便改已有方法签名。

不适合：

* 忘记在 finally 中清理上下文。
* 在线程复用环境里长期持有 ThreadLocal。

---

## 6. Redis 生产建议

* 让 `ttl` 明确可观测，不要在生产环境依赖“永不过期”的默认行为。
* 把 `sessionId` 设计成稳定但无业务敏感信息泄露的标识，例如用户 ID + 会话 UUID，而不是直接拼接明文隐私字段。
* 如果服务是多副本部署，优先使用 Redis Memory，这样 agent 历史不会被单节点内存打散。
* 对长会话配合 Context Compaction 一起使用，避免历史无限增长。

---

## 7. 快速验证

使用 examples 模块里的 Spring Demo 时，可以直接这样测试会话隔离：

```bash
curl "http://localhost:8080/chat?sessionId=user-a&message=你好，我叫泽宝"
curl "http://localhost:8080/chat?sessionId=user-a&message=我刚刚叫什么"
curl "http://localhost:8080/chat?sessionId=user-b&message=我刚刚叫什么"
```

预期：

* 第二次请求会命中 `user-a` 的历史。
* `user-b` 不会看到 `user-a` 的内容。
