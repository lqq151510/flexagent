# Pull Request Guidelines

This page explains how we keep FlexAgent changes reviewable, safe, and easy to maintain.

## Before You Open a PR

- Make sure the change is focused and small enough to review.
- Run the CI-equivalent verification locally with `mvn -B -ntp clean verify`.
- Update documentation when the public behavior changes.
- Add or update tests for behavior changes.

## Branch Naming

- `feature/<short-description>` for new features
- `bugfix/<short-description>` for bug fixes
- `docs/<short-description>` for documentation-only changes

## Review Expectations

- Keep `flexagent-core` free of direct third-party SDK dependencies.
- Prefer the smallest diff that fully addresses the issue.
- Avoid mixing unrelated refactors into feature or bug-fix PRs.
- Include context in the PR description when the change touches runtime behavior.

## Required Checks

- `mvn -B -ntp clean verify`
- `mvn -q -DskipTests test-compile`
- Manual demo validation when the change affects examples or user-facing flows

## PR Description Checklist

- What changed
- Why it changed
- How it was verified
- Runtime/API risk and rollout notes
- Documentation and changelog status
- Any follow-up work that remains

## Review Quality Gate

- Check whether the PR changes public API, runtime state, session memory, compaction, or tool invocation behavior.
- Confirm new or changed behavior has a focused test in the touched module.
- Confirm `flexagent-core` remains independent from LangChain4j, Spring AI, MCP, and other third-party LLM SDKs.
- Use CI artifacts (`surefire-reports`, `jacoco-reports`) to diagnose failures instead of relying only on log snippets.
