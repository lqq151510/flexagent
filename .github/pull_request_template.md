## Summary

Describe what changed and why. Link issues with `Fixes #...` when applicable.

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update
- [ ] CI / test / maintenance

## Verification

Paste the commands you ran and their result.

```bash
mvn -B -ntp clean verify
```

## Risk and rollout

- Runtime/API behavior changed: yes / no
- Public docs or examples updated: yes / no
- Follow-up work required: none / describe

## Reviewer checklist

- [ ] The diff is focused and does not mix unrelated refactors.
- [ ] Runtime changes preserve `flexagent-core` independence from third-party LLM SDKs.
- [ ] Tests cover the changed behavior or explain why no test is needed.
- [ ] Documentation and `CHANGELOG.md` are updated for user-facing changes.
- [ ] CI artifacts are sufficient to debug failures (test reports / coverage reports).
