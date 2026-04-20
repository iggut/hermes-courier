#!/usr/bin/env bash
# Optional smoke check against a reachable Hermes gateway base URL (HTTPS).
# Set HERMES_LIVE_GATEWAY_BASE_URL, e.g. https://gateway.example.com
# mTLS-only gateways may fail plain curl; use a health route or an mTLS-capable probe instead.
set -euo pipefail

BASE="${HERMES_LIVE_GATEWAY_BASE_URL:-}"
if [[ -z "$BASE" ]]; then
  echo "live-gateway-smoke-test: SKIP (set HERMES_LIVE_GATEWAY_BASE_URL to exercise a live gateway)"
  exit 0
fi

BASE="${BASE%/}"
EXTRA=()
if [[ "${HERMES_LIVE_GATEWAY_CURL_INSECURE:-}" == "1" ]]; then
  EXTRA+=(-k)
fi

echo "live-gateway-smoke-test: GET $BASE/v1/dashboard"
CODE="000"
CODE="$(curl "${EXTRA[@]}" -sS --connect-timeout 8 --max-time 25 \
  -o /dev/null -w "%{http_code}" \
  -H "Accept: application/json" \
  "$BASE/v1/dashboard")" || CODE="000"

if [[ "$CODE" =~ ^[245][0-9][0-9]$ ]]; then
  echo "live-gateway-smoke-test: OK (HTTP $CODE)"
  exit 0
fi
echo "live-gateway-smoke-test: FAILED (HTTP $CODE)"
exit 1
