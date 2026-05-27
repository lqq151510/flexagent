# Release Checklist

为了保障项目开源时的社区体验，避免项目发布出现红线问题或“开箱不可用”的情况，请在发布前核对以下清单：

## 1. 法律合规与项目定位 (Must Have)
- [x] **项目声明**：在中英文 `README.md` 的首屏顶部，显著位置注明本项目是**社区驱动的第三方适配层，而非 Google 官方项目**。
- [x] **商业授权 (License)**：在项目根目录中包含 `LICENSE` 文件（采用 Apache-2.0 协议，利于开源社区接入）。
- [x] **隐私与数据安全**：
  - [x] 仔细审查，在源码 and 测试资源中**没有硬编码提交 any 真实的 API Key、鉴权 Token**。
  - [x] 确保没有提交任何 Google 的闭源 localharness 二进制，也没有提交任何 Google 官方版权的私有协议原件。

## 2. 用户开箱体验 (Must Have)
- [x] **5分钟 Quick Start**：README 首屏直接提供 10 行以内的 Java 示例（声明 @Tool、初始化 LangChain4jRuntime 并执行 send 的最简案例），能让普通 Java 开发者一眼看懂项目价值。
- [x] **核心 Demo 独立性**：`antigravity-examples` 里的直连大模型 Demo（对接本地 Ollama 或国内 DeepSeek-R1 API），**不能依赖本地安装有 Antigravity CLI / localharness 二进制**。确保零环境负担，克隆下来装好 Maven 直接就能在本地跑起来。
- [x] **Demo 可靠运行**：
  - [x] 编写的 `examples/deepseek-agent` (通过远程 API) 可直接测试且运行畅通。
  - [x] 编写的 `examples/ollama-agent` (通过本地 127.0.0.1:11434) 能在不安装外部 CLI 时连通。

## 3. 工程健壮性与持续集成 (Must Have)
- [x] **本地测试覆盖**：在本地根目录执行 `mvn clean test` 时，所有模块 of 测试用例（包括提取器坏情况测试、Runtime 多轮测试）均 **100% 成功通过**。
- [x] **CI 流水线**：添加 GitHub Actions 配置（`.github/workflows/maven.yml`），使每一次 PR 和 Push 均能在 CI 环境中运行 `mvn test` 并保持 Green。

## 4. 宣传与开源加分项 (Nice to Have)
- [x] **架构示意图**：在 README 中包含 Mermaid 或流程图，直观展现 `@Tool -> ToolAdapter -> ToolDefinition -> 各 Runtime` 的核心解耦边界。
- [x] **高级用例代码**：给出流式输出思维链（`<think>` 提取）、工具不合规 JSON 容错策略的配置与演示样例。
- [x] **中英文双语 README**：提供 `README.md` (中文) 和 `README_EN.md` (英文) 以接轨国际社区。
