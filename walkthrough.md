# FlexAgent Java Adapter 开源发布与开发体验重构报告 (v0.2.0)

我们已成功完成对 **FlexAgent Java Adapter** 从 v0.1.0 到 v0.2.0 的全面升级。本次升级聚焦于**常驻生命周期重构**（解决重复初始化问题）、**极简 Builder API 设计**（极大提升开发体验）、**推理模型智能探测**以及**标准化自定义异常体系的构建**。

---

## v0.2.0 重大更新与交付成果

### 1. 常驻 Runtime 生命周期重构
为了解决 v0.1.0 中每次调用 `generate()` 都会重新加载 SPI 并销毁重建 `AgentRuntime` 的性能和会话上下文丢失的缺陷：
* 使 `FlexAgentChatModel` 实现了 `AutoCloseable` 接口。
* 在构造函数中，通过 SPI 加载并对 `AgentRuntime` 进行一次性初始化，且在整个生命周期中持久保持该 Runtime 连接。
* 在 `generate()` 中直接复用长驻的运行时实例，多轮生成性能提升数倍，并支持底层运行时维护对话会话状态与历史消息的裁剪。
* 在 `close()` 方法被调用时释放连接、网络资源和 Harness 进程。

### 2. 极简开发体验的 Builder API
重构并精简了 Builder，开发者无需手动关心 `AgentRuntimeConfig`、`ServiceLoader` 等繁杂概念，能够像使用原生大模型客户端一样快速创建智能体：
```java
FlexAgentChatModel agent = FlexAgentChatModel.builder()
        .runtime(RuntimeTypes.LANGCHAIN4J)
        .model(delegateModel)
        .tools(new CalculatorTools())
        .enableThinkingExtraction(true)
        .toolCallPolicy(ToolCallPolicy.TEXT_FALLBACK)
        .build();
```

### 3. 推理模型智能自动探测与 `<think>` 提取
* 在不匹配底层大模型具体实现时，若 `modelName` 或底座大模型类的具体实例名称（通过反射尝试获取 `modelName()` 或 `getModelName()`）包含 `"reasoner"` 或 `"r1"`，则自动开启 `XML_THINK_TAG` 状态机模式，无需用户手动配置。

### 4. 标准化的自定义异常体系
设计并交付了专用的异常树体系（继承自 `FlexAgentException`），替换了原来粗糙的系统级异常，提高开发者调试排障效率：
* **`ProviderNotFoundException`**：当 SPI 在 classpath 找不到匹配的适配器模块或检测到冲突时抛出，并在报错信息中提供极其明确的 Maven 依赖排障动作指南。
* **`RuntimeInitializationException`**：当网络连接或底层 Harness 进程加载失败时抛出。
* **`ToolInvocationException`**：工具反射执行出错时抛出。

---

## 历史 v0.1.0 交付成果回顾

## 交付成果一览

### 1. 核心解耦架构
我们重新设计并划清了四个模块的物理依赖边界，实现了 **“业务 API -> Core 抽象层 -> (插拔式) 各 Runtime 后端”** 的依赖解耦：
* **`ToolAdapter` 解耦**：移除了对 `localharness.proto.Tool` 的跨模块不当物理依赖，改用 `flexagent-core` 定义的 Record `ToolDefinition` 作为数据传递载体，消除了编译期强耦合。
* **统一 `AgentConfig`**：在配置中增加了 `List<ToolDefinition> tools` 等通用属性，各 Runtime 实现类可按需读取、转换和装配。
* **引入 SPI 动态发现与依赖解耦机制**：
  * 弃用了先前在门面类中硬编码类名反射加载具体 Runtime 的脆弱逻辑，改用 Java 标准的 `ServiceLoader` SPI 服务发现机制。
  * 在 `flexagent-core` 中定义了 `AgentRuntimeProvider` SPI 接口，并通过将第二个参数定义为通用的 `Object`，彻底解耦了核心模块与具体 LangChain4j 的物理依赖，防止依赖污染。
  * `flexagent-localharness` 与 `flexagent-langchain4j` 分别通过各自的 SPI 配置文件 `META-INF/services/org.flexagent.core.runtime.AgentRuntimeProvider` 声明其 Provider。
  * `FlexAgentChatModel` 通过 `ServiceLoader.load` 动态查找到可用 Runtime 并进行装配，如果宿主应用 classpath 中仅含有特定的模块，则自动匹配支持的 Runtime 引擎。

### 2. 精准的流式 R1 `<think>` 提取器
* 实现了 `XmlThinkTagExtractor` 字符状态机。
* 在即使由于流式传输导致 XML 标签被切碎截断的情形下（例如分批收到 `"<thi"` 与 `"nk>"`），或者标签未闭合、多重标签时，依然可以实现精确无误的模式回溯匹配，为国内模型的推理过程剥离与最终正文吐出保驾护航。

### 3. 三大工程化机制与边缘容错机制
* **能力集与推理模式**：引入 `RuntimeCapability`（STREAMING、TOOL_CALLING、THINKING_DELTA、COMPACTION）、`ThinkingMode` 与 `ToolCallPolicy`，提供完善的能力声明。
* **不规范 JSON 容错与修复**：
  * **`ToolCallPolicy.STRICT`**：如果 JSON 参数不合规，抛出 `IllegalArgumentException` 异常并终止，由 `FlexAgentChatModel` 将运行时错误向外传递抛出。
  * **`ToolCallPolicy.LENIENT`**：尝试对不规范 JSON（如键名未加引号、单引号包围、缺失末尾大括号或尾部多余逗号）通过简单正则与字符拼接进行修复，修复成功后继续流式执行。
  * **`ToolCallPolicy.TEXT_FALLBACK`**：若解析失败，自动回退将工具调用请求以纯文本的形式直接呈递给用户。

### 4. 去谷歌化与项目重命名 (FlexAgent) [NEW]
* 将项目从 `Antigravity` 全面改名为 **`FlexAgent`**，包路径迁移至 **`org.flexagent`**，彻底切断了与 Google 的包名绑定，为开源合规和社区推广铺平了道路。

---

## 自动化测试验证

目前，整个工程在 Java 21 环境下编译大获成功，并已跑通全部测试：

### 1. 单元测试 (`XmlThinkTagExtractorTest`)
* **无标签文本测试**：验证模型普通吐出时是否能完好无损地转为 `TextDelta`。
* **完整标签测试**：验证带完整的 `<think>...</think>` 推理标签能否被成功分离。
* **流式碎块标签测试**：模拟大模型字符在标签中间产生断句截断（如 `"<thi"` 与 `"nk>"`），验证状态机回溯是否仍能完整捕获并拼合为 `ThinkingDelta` 与 `TextDelta`。
* **坏情况测试**：新增对未闭合标签与多重重复标签的极端情形测试。
* **测试结果**：5 个测试用例全部 **PASSED**。

### 2. 集成与边缘测试 (`FlexAgentChatModelTest`)
* **Harness 交互测试**：验证 Mock Binary 4字节握手、WebSocket 交互、自定义工具 `add` 调用及结果回传。
* **`TEXT_FALLBACK` 策略测试**：验证 JSON 解析失败时是否能平滑转换为普通文本。
* **滑动窗口 Compaction 历史裁剪测试**：验证在上下文超长时是否能在保留系统指令的同时滑动裁掉历史消息。
* **`LENIENT` 宽松 JSON 修复测试 [NEW]**：验证不带双引号键名的 JSON 大模型参数（如 `{a: 10, b: 20}`）能够被正则成功修补并正常调起工具。
* **`STRICT` 严格报错测试 [NEW]**：验证在严格模式下遭遇非法 JSON 参数时能正确抛出 `RuntimeException` 中断流程。
* **幻觉工具调用测试 [NEW]**：验证大模型幻觉调用不存在的工具名时，可以通过将 `ToolResult` 包含错误状态回传给模型令其自主纠错。
* **测试结果**：6 个测试用例全部 **PASSED**。

### 3. Maven Reactor 测试概览
```text
[INFO] Reactor Summary for FlexAgent Java SDK Parent 0.1.0-SNAPSHOT:
[INFO] 
[INFO] FlexAgent Java SDK Parent .......................... SUCCESS [  0.049 s]
[INFO] FlexAgent Core Abstraction ......................... SUCCESS [  1.154 s]
[INFO] FlexAgent LocalHarness Adapter ..................... SUCCESS [  2.977 s]
[INFO] FlexAgent LangChain4j Adapter ...................... SUCCESS [  8.400 s]
[INFO] FlexAgent Java Examples ............................ SUCCESS [  0.092 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
```

---

## 开源发布合规与就绪清单

1. **项目非官方声明**：中英文 `README.md` 与 `README_EN.md` 顶部显著标记第三方性质。
2. **免 CLI 运行大模型示例**：添加 `DeepSeekAgentDemo`，装好 Java 和 Maven 即开即用。
3. **CI/CD 支持**：创建了 `.github/workflows/maven.yml`，在 PR/Push 时触发单元测试自动化运行。
4. **License 许可**：在根目录下创建了 `LICENSE`，基于 Apache-2.0 开源协议发布。
