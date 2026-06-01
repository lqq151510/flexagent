# FlexAgent Maintenance Guide

This document summarizes how FlexAgent is maintained and how contributors can work with the repository safely.

## Maintenance Principles

- Keep `flexagent-core` free from direct third-party LLM SDK dependencies.
- Preserve backward compatibility for public APIs whenever possible.
- Prefer small, testable changes over broad rewrites.
- Treat documentation, examples, and CI as part of the deliverable, not as optional extras.

## Release Workflow

1. Update implementation and tests.
2. Refresh `CHANGELOG.md` with user-facing changes.
3. Verify the build with `mvn clean test`.
4. Confirm README examples still match the current version and module layout.
5. Publish the release only after documentation and CI are in sync.

## Contribution Workflow

- Open an issue for bugs or feature ideas when the change is non-trivial.
- Use the existing PR template for all pull requests.
- Include tests for behavioral changes.
- Update docs when public APIs, setup steps, or runtime behavior change.

## Support Scope

- Supported Java baseline: JDK 21+
- Supported build tool: Maven
- Primary runtime adapter: LangChain4j
- Experimental adapter: `flexagent-localharness`

## Verification Checklist

- `mvn clean test`
- `mvn -q -DskipTests test-compile`
- Manual demo run from `flexagent-examples` when behavior changes affect user-facing flows
- README and changelog consistency check
