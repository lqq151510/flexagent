# FlexAgent SPI 架构升级与彻底解耦实施计划

为了将 FlexAgent 打造为一个真正具备扩展性、高内聚低耦合的 Java Agent Runtime 框架，我们将对 SPI 机制进行深度升级。

## User Review Required
> [!IMPORTANT]
> 核心升级内容包括在 `flexagent-core` 中引入通用的 `AgentRuntimeConfig` 配置封装类和 `RuntimeTypes` 常量定义，确保 `flexagent-core` 没有任何对外部三方框架（如 LangChain4j）的编译期物理依赖。

## Proposed Changes

### 1. Core 核心层物理依赖完全解耦
* **[NEW]** 创建 [AgentRuntimeConfig.java](file:///Users/liuyongze/Desktop/sdk/flexagent-core/src/main/java/org/flexagent/core/runtime/AgentRuntimeConfig.java)：
  封装运行期参数，解耦对特定大模型 SDK 类（如 `ChatLanguageModel`）的强绑定。提供泛型转型方法安全获取 delegate model。
* **[NEW]** 创建 [RuntimeTypes.java](file:///Users/liuyongze/Desktop/sdk/flexagent-core/src/main/java/org/flexagent/core/runtime/RuntimeTypes.java)：
  集中管理运行时支持的后端类型常量（如 `langchain4j`, `localharness`），防止拼写错误。
* **[MODIFY]** 重构接口 [AgentRuntimeProvider.java](file:///Users/liuyongze/Desktop/sdk/flexagent-core/src/main/java/org/flexagent/core/runtime/AgentRuntimeProvider.java)：
  ```java
  package org.flexagent.core.runtime;
  public interface AgentRuntimeProvider {
      boolean supports(String type);
      AgentRuntime create(AgentRuntimeConfig config);
  }
  ```

### 2. SPI 适配器模块重构
* **[MODIFY]** `flexagent-langchain4j`:
  * 在 [LangChain4jRuntimeProvider.java](file:///Users/liuyongze/Desktop/sdk/flexagent-langchain4j/src/main/java/org/flexagent/langchain4j/LangChain4jRuntimeProvider.java) 中，通过 `config.model(ChatLanguageModel.class)` 获取对应的 LangChain4j 大模型实例，并完成装配。
* **[MODIFY]** `flexagent-localharness`:
  * 在 [LocalHarnessRuntimeProvider.java](file:///Users/liuyongze/Desktop/sdk/flexagent-localharness/src/main/java/org/flexagent/localharness/LocalHarnessRuntimeProvider.java) 中，基于通用的 `AgentRuntimeConfig` 来创建 LocalHarness 运行时。

### 3. SPI 动态发现与冲突/空结果处理
* **[MODIFY]** 重构门面类 [FlexAgentChatModel.java](file:///Users/liuyongze/Desktop/sdk/flexagent-langchain4j/src/main/java/org/flexagent/langchain4j/FlexAgentChatModel.java)：
  使用 Stream API 过滤所有注册的 SPI 提供者。
  * 若未找到对应的 Provider，抛出异常。
  * 若找到多个匹配的 Provider，抛出异常，防止冲突。
  * 仅在匹配结果唯一时，才调用其 `create` 方法。

### 4. 示例 Demo 增强与 README 补充声明
* **[MODIFY]** [DeepSeekAgentDemo.java](file:///Users/liuyongze/Desktop/sdk/flexagent-examples/src/main/java/org/flexagent/examples/DeepSeekAgentDemo.java)：
  * 在启动时，使用日志或标准输出打印出当前生效的 Runtime 及其 SPI Provider 类名，以直观演示 SPI 的动态生效。
* **[MODIFY]** 中文 [README.md](file:///Users/liuyongze/Desktop/sdk/README.md) 与 英文 [README_EN.md](file:///Users/liuyongze/Desktop/sdk/README_EN.md) 补充声明：
  * 明确在 README 中声明 `flexagent-localharness` 不分发、不内置任何商业或专有二进制文件。

---

## Verification Plan

### Static Checks
- 验证 `flexagent-core` 源码和 POM 中没有任何对 LangChain4j 的强引用依赖。
- 确认 SPI 描述文件 `/META-INF/services/org.flexagent.core.runtime.AgentRuntimeProvider` 在两个适配器模块中编写且格式正确。
- 验证在打包后，target 目录中能正确生成和复制对应的 SPI 注册描述文件。
- 使用 `mvn dependency:tree` 校验依赖树中无残留旧坐标 `com.google.antigravity`。

### Automated Tests
在根目录下执行：
```bash
mvn clean test
```
期待：所有的 6 个单元与集成测试均能通过 SPI 动态发现机制编译并执行通过。

### Manual Verification
运行 Demo：
```bash
mvn -pl flexagent-examples exec:java -Dexec.mainClass="org.flexagent.examples.DeepSeekAgentDemo"
```
期待控制台能够打印加载的 Runtime 与 Provider：
```text
FlexAgent runtime loaded: langchain4j
Provider: org.flexagent.langchain4j.LangChain4jRuntimeProvider
```
