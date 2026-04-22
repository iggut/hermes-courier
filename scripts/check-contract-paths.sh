#!/usr/bin/env bash
# Ensures documented routes exist in the OpenAPI file and match client constants.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
YAML="${ROOT}/shared/contract/hermes-courier-api.yaml"
KT="${ROOT}/android/app/src/main/java/com/hermescourier/android/domain/gateway/HermesApiPaths.kt"
SWIFT="${ROOT}/ios/HermesCourierApp/Sources/Contract/HermesContract.swift"

for f in "$YAML" "$KT" "$SWIFT"; do
  if [[ ! -f "$f" ]]; then
    echo "Missing: $f" >&2
    exit 1
  fi
done

require_in_yaml() {
  local needle="$1"
  if ! grep -qF "$needle" "$YAML"; then
    echo "Contract YAML missing: $needle" >&2
    exit 1
  fi
}

require_in_yaml "/v1/auth/challenge"
require_in_yaml "/v1/auth/response"
require_in_yaml "/v1/dashboard"
require_in_yaml "/v1/sessions"
require_in_yaml "/v1/sessions/{sessionId}"
require_in_yaml "/v1/sessions/{sessionId}/actions"
require_in_yaml "/v1/sessions/{sessionId}/{action}"
require_in_yaml "/v1/approvals"
require_in_yaml "/v1/conversation"
require_in_yaml "/v1/events"
require_in_yaml "/v1/skills"
require_in_yaml "/v1/skills/{skillId}"
require_in_yaml "/v1/memory"
require_in_yaml "/v1/memory/{memoryId}"
require_in_yaml "/v1/cron"
require_in_yaml "/v1/cron/{cronId}"
require_in_yaml "/v1/logs"

require_kt_swift() {
  local kt_needle="$1"
  local swift_needle="$2"
  if ! grep -qF "$kt_needle" "$KT"; then
    echo "Kotlin HermesApiPaths missing: $kt_needle" >&2
    exit 1
  fi
  if ! grep -qF "$swift_needle" "$SWIFT"; then
    echo "Swift HermesAPIPaths missing: $swift_needle" >&2
    exit 1
  fi
}

require_kt_swift "v1/auth/challenge" "/v1/auth/challenge"
require_kt_swift "v1/auth/response" "/v1/auth/response"
require_kt_swift "v1/dashboard" "/v1/dashboard"
require_kt_swift "v1/sessions" "/v1/sessions"
require_kt_swift 'v1/sessions/$sessionId' '/v1/sessions/\(sessionId)'
require_kt_swift 'v1/sessions/$sessionId/actions' '/v1/sessions/\(sessionId)/actions'
require_kt_swift 'v1/sessions/$sessionId/$action' '/v1/sessions/\(sessionId)/\(action)'
require_kt_swift "v1/approvals" "/v1/approvals"
require_kt_swift "v1/conversation" "/v1/conversation"
require_kt_swift "v1/events" "/v1/events"
require_kt_swift "v1/skills" "/v1/skills"
require_kt_swift 'v1/skills/$skillId' '/v1/skills/\(skillId)'
require_kt_swift "v1/memory" "/v1/memory"
require_kt_swift 'v1/memory/$memoryId' '/v1/memory/\(memoryId)'
require_kt_swift "v1/cron" "/v1/cron"
require_kt_swift 'v1/cron/$cronId' '/v1/cron/\(cronId)'
require_kt_swift "v1/logs" "/v1/logs"

echo "Contract path check OK."
