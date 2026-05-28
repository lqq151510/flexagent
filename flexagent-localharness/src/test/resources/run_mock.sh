#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
PY_SDK_DIR="${REPO_ROOT}/flexagent-sdk-python"
MOCK_SCRIPT="${SCRIPT_DIR}/mock_harness.py"

if command -v uv >/dev/null 2>&1; then
  exec uv run --project "${PY_SDK_DIR}" --with websockets python3 "${MOCK_SCRIPT}"
fi

exec python3 "${MOCK_SCRIPT}"
