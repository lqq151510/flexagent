# Contributing to FlexAgent

Thank you for your interest in contributing to FlexAgent! We welcome contributions from the community to help make this lightweight Java Agent Runtime adapter better.

---

## 🛠️ Local Development Setup

To get started, make sure you have:
*   **JDK 21** or higher.
*   **Maven 3.8+**.

### Build and Test
Run the following commands in the root directory to verify everything compiles and tests pass:
```bash
# Clean build and compile all modules
mvn clean install -DskipTests

# Run the JUnit test suite
mvn test
```

---

## 🌿 Branching Strategy & PR Process

1.  **Fork** the repository and clone your fork locally.
2.  Create a descriptive branch name:
    *   `feature/your-feature-name` for new features.
    *   `bugfix/your-bug-name` for bug fixes.
    *   `docs/your-doc-changes` for documentation updates.
3.  Implement your changes. **Ensure you write corresponding unit tests** in the `flexagent-core` or `flexagent-langchain4j` test directories.
4.  Run all unit tests locally using `mvn test` to verify no regressions.
5.  Commit your changes with clear, structured messages (we recommend the [Conventional Commits](https://www.conventionalcommits.org/) specification, e.g., `feat: add Spring Boot support` or `fix: xml tag parse edge cases`).
6.  Push your branch to your fork and submit a **Pull Request (PR)** to the `main` branch.

---

## 🧪 Coding Standards

*   **Java Version**: Use Java 21 native features where appropriate (e.g., Records, Virtual Threads).
*   **Decoupling Principle**: Keep `flexagent-core` physically free of third-party SDK dependencies (like LangChain4j or Spring AI).
*   **Test Coverage**: All new features and bug fixes must have corresponding JUnit 5 test cases.
