# FlexAgent SPI 增强与核心层彻底解耦任务清单

- [x] Core 核心层解耦设计
  - [x] 创建 `AgentRuntimeConfig` 支持泛型转型与 options 配置
  - [x] 创建 `RuntimeTypes` 常量定义类
  - [x] 修改 `AgentRuntimeProvider` 接口，入参变更为 `AgentRuntimeConfig`
- [x] SPI 适配模块升级与冲突处理
  - [x] 重构 `flexagent-langchain4j` 的 `LangChain4jRuntimeProvider`
  - [x] 重构 `flexagent-localharness` 的 `LocalHarnessRuntimeProvider`
  - [x] 重构 `FlexAgentChatModel` 中的 `ServiceLoader` 动态发现、多匹配冲突拦截与空结果抛出逻辑
- [x] 文档与 Demo 调优
  - [x] 更新中英文 README 声明，澄清 `flexagent-localharness` 不包含专有二进制
  - [x] 更新 `DeepSeekAgentDemo`，输出当前生效的 Runtime 及具体 SPI Provider 类名
- [x] 验证与回归测试
  - [x] 运行静态扫描（校验 `com.google.antigravity` 与旧包名残留）
  - [x] 运行 `mvn clean test` 验证回归测试 100% 通过
  - [x] 运行 `mvn clean install -DskipTests` 发布本地 m2 仓库
  - [x] 运行 `DeepSeekAgentDemo` 验证 SPI 服务加载日志输出
