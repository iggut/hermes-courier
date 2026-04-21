#!/usr/bin/env bash
# Live-gateway smoke check against the current contract + decoder assumptions.
# Required env:
#   HERMES_LIVE_GATEWAY_BASE_URL=https://gateway.example.com
# Optional env:
#   HERMES_LIVE_GATEWAY_BEARER_TOKEN=...
#   HERMES_LIVE_GATEWAY_AUTH_MODE=auto|token
#   HERMES_LIVE_GATEWAY_ALLOW_MUTATING=1
#   HERMES_LIVE_GATEWAY_CURL_INSECURE=1
#   HERMES_LIVE_GATEWAY_TIMEOUT_SECONDS=20
#   HERMES_LIVE_GATEWAY_SESSION_ID=...
#   HERMES_LIVE_GATEWAY_APPROVAL_ID=...
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PYTHON_BIN="${PYTHON_BIN:-python3}"

if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  echo "live-gateway-smoke-test: FAILED ($PYTHON_BIN not found)"
  exit 1
fi

"$PYTHON_BIN" "$ROOT/scripts/live_gateway_smoke.py"
