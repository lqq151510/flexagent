# FlexAgent
> Lightweight Java Agent Runtime Adapter for LangChain4j and OpenAI-compatible models.

[![CI Build](https://github.com/lqq151510/flexagent/actions/workflows/maven.yml/badge.svg)](https://github.com/lqq151510/flexagent/actions/workflows/maven.yml)
[![Java](https://img.shields.io/badge/Java-21%2B-blue)](https://img.shields.io/badge/Java-21%2B-blue)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-v0.5.0-blue)](https://img.shields.io/badge/version-v0.5.0-blue)

FlexAgent is a lightweight Java Agent Runtime adapter layer designed to decouple business tools from underlying model runtimes, featuring first-class support for LangChain4j, OpenAI-compatible models, and reasoning stream parsing.

> ⚠️ **Disclaimer**
> This project is a community-driven third-party adapter. **It is not an official Google project and is not affiliated with, endorsed by, or connected to Google, Gemini, or Antigravity.**
> The `flexagent-localharness` module is an optional adapter. **This project does not bundle, modify, or distribute any proprietary or copyrighted local harness binary.**

---

## 🚀 10-Minute Quick Start

You can easily clone and run the project locally without installing any external harness engines.

### 1. Clone & Build
Ensure you have JDK 21 and Maven installed.
```bash
# Clone the repository
git clone https://github.com/lqq151510/flexagent.git
cd flexagent

# Compile and run all tests
mvn clean test
```

### 2. Run the DeepSeek R1 Demo
Set your DeepSeek API key and execute the runner:
```bash
export DEEPSEEK_API_KEY="your-actual-api-key"

mvn -pl flexagent-examples exec:java -Dexec.mainClass="org.flexagent.examples.DeepSeekAgentDemo"
```
You will see:
1. FlexAgent dynamically loads the `langchain4j` runtime backend using Java SPI.
2. The reasoning process from the LLM (e.g., `deepseek-reasoner`) is printed in real-time as `[Thinking] xxx`.
3. FlexAgent intercepts tool calling directives and executes your local Java tool via reflection.
4. The tool result is returned, and the LLM prints the final response.

---

## 🌟 Key Features

* **Pluggable Runtime SPI**: Core `AgentRuntime` interface allows switching backends between standard `LangChain4j` loop and experimental `localharness` debugger, with zero impact on business code.
* **Tool Decoupling**: Business `@Tool` methods are converted into standard `ToolDefinition` objects. Adapters automatically map parameters, shielding business tools from base LLM framework migrations.
* **Streaming `<think>` Tag Parser**: High-performance `XmlThinkTagExtractor` state machine that extracts `ThinkingDelta` and `TextDelta` segments, resilient against packet fragmentation or unclosed tags.
* **Tool Call Failure Recovery**: Built-in `STRICT`, `LENIENT`, and `TEXT_FALLBACK` policies to gracefully handle hallucinated or malformed JSON arguments produced by LLMs.
* **Session Memory and TTL**: Supports both `InMemoryAgentMemory` and `RedisAgentMemory` for per-session history isolation, cross-request recall, and expiration control.

---

## 🛠️ Modules

* **`flexagent-core`**: Core abstractions including `AgentRuntime` SPI, `ToolDefinition`, reasoning stream state machine, and basic models. **Does not depend on any third-party LLM SDKs.**
* **`flexagent-langchain4j`**: Adapter implementing runtime controls using the LangChain4j ecosystem.
* **`flexagent-localharness`**: Experimental adapter communicating with an external localharness binary via WebSocket.
* **`flexagent-examples`**: Quick-start templates.

---

## 📖 Documentation

* **Architecture & Comparison**: [FlexAgent vs LangChain4j vs Spring AI](docs/comparison-with-langchain4j-and-spring-ai.md)
* **Session Memory Quickstart**: [docs/memory_quickstart.md](docs/memory_quickstart.md)
* **Spring Boot Memory / Redis Setup**: [docs/spring-boot-memory.md](docs/spring-boot-memory.md)
* **Context Compaction**: [Context Compaction](docs/examples/context-compaction.md)
* **Roadmap**: [ROADMAP.md](ROADMAP.md)
* **Changelog**: [CHANGELOG.md](CHANGELOG.md)
* **Maintenance Guide**: [MAINTENANCE.md](MAINTENANCE.md)
* **Pull Request Guidelines**: [PULL_REQUEST_GUIDELINES.md](PULL_REQUEST_GUIDELINES.md)

---

## 🧩 Open Source Maintenance & Maturity

* **Continuous integration**: Maven build and tests run on every push and pull request.
* **Version history**: `CHANGELOG.md` records each release and its major changes.
* **Contribution readiness**: The repository includes `CONTRIBUTING.md`, `SECURITY.md`, and a PR template.
* **Modular delivery**: Core, adapters, examples, and the Spring Boot starter are maintained as separate modules for clean evolution.
