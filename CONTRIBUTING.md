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
# Run the same verification gate used by CI
mvn -B -ntp clean verify
```

---

## 🌿 Branching Strategy & PR Process

1.  **Fork** the repository and clone your fork locally.
2.  Create a descriptive branch name:
    *   `feature/your-feature-name` for new features.
    *   `bugfix/your-bug-name` for bug fixes.
    *   `docs/your-doc-changes` for documentation updates.
3.  Implement your changes. **Ensure you write corresponding unit tests** in the touched module.
4.  Run the CI-equivalent verification locally using `mvn -B -ntp clean verify`.
5.  Commit your changes with clear, structured messages (we recommend the [Conventional Commits](https://www.conventionalcommits.org/) specification, e.g., `feat: add Spring Boot support` or `fix: xml tag parse edge cases`).
6.  Push your branch to your fork and submit a **Pull Request (PR)** to the `main` branch.

---

## 🧪 Coding Standards

*   **Java Version**: Use Java 21 native features where appropriate (e.g., Records, Virtual Threads).
*   **Decoupling Principle**: Keep `flexagent-core` physically free of third-party SDK dependencies (like LangChain4j or Spring AI).
*   **Test Coverage**: All new features and bug fixes must have corresponding JUnit 5 test cases.
*   **CI Evidence**: PRs should include the verification command output and update docs/changelog for user-facing changes.
