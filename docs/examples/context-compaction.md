# FlexAgent Example: Context Compaction

本示例展示如何在多轮对话增长后，对上下文进行压缩，从而控制消息长度并保留关键对话信息。

## 1. 依赖配置

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.flexagent</groupId>
            <artifactId>flexagent-bom</artifactId>
            <version>0.5.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.flexagent</groupId>
        <artifactId>flexagent-langchain4j</artifactId>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
        <version>0.31.0</version>
    </dependency>
</dependencies>
```

## 2. 压缩策略

### Sliding Window
保留最近 N 条消息，适合短上下文高频交互。

```java
import org.flexagent.langchain4j.FlexAgentChatModel;
import org.flexagent.langchain4j.compaction.SlidingWindowCompactionStrategy;

FlexAgentChatModel model = FlexAgentChatModel.builder()
        .model(delegateModel)
        .compactionStrategy(new SlidingWindowCompactionStrategy(8))
        .build();
```

也可以通过 Builder 直接配置阈值触发（message/token）：

```java
FlexAgentChatModel model = FlexAgentChatModel.builder()
        .model(delegateModel)
        .compactionMaxMessages(8)         // 压缩后保留窗口
        .compactionMessageThreshold(20)   // 超过 20 条消息触发
        .compactionTokenThreshold(2000)   // 或估算 token 超过 2000 触发
        .build();
```

### Summary
将较早的消息折叠成摘要系统消息，适合长对话保留任务状态。

```java
import org.flexagent.langchain4j.compaction.SummaryCompactionStrategy;

FlexAgentChatModel model = FlexAgentChatModel.builder()
        .model(delegateModel)
        .compactionStrategy(new SummaryCompactionStrategy(
                10,     // maxMessages
                20,     // messageThreshold
                2000,   // tokenThreshold
                180,    // summaryMaxChars
                2       // minTailMessages
        ))
        .build();
```

### Tool Aware
在压缩时优先保留工具调用结果，避免工具上下文被误删。

```java
import org.flexagent.langchain4j.compaction.ToolAwareCompactionStrategy;

FlexAgentChatModel model = FlexAgentChatModel.builder()
        .model(delegateModel)
        .compactionStrategy(new ToolAwareCompactionStrategy(
                12,     // maxMessages
                24,     // messageThreshold
                2500,   // tokenThreshold
                3       // maxToolMessagesToKeep
        ))
        .build();
```

## 3. 行为说明

* 当消息数未超过阈值时，不会触发压缩。
* 超过阈值后，Runtime 会在调用模型前自动压缩上下文。
* 默认不配置 `compactionStrategy` 时，行为保持和原来一致。
* Runtime 会输出压缩观测日志：`sessionId`、触发原因、压缩前后 message/token 数。
