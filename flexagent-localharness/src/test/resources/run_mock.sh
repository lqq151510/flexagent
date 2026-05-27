#!/bin/bash
# Re-route via uv to resolve dependencies cleanly in tests, pointing to the python SDK project directory
exec uv run --project /Users/liuyongze/Desktop/sdk/flexagent-sdk-python --with websockets python3 /Users/liuyongze/Desktop/sdk/flexagent-localharness/src/test/resources/mock_harness.py
