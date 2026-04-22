# Backend issues surfaced by the mobile clients

This file records concrete, reproducible gateway behaviours that differ from the
signed contract in `shared/contract/hermes-courier-api.yaml`. It is intentionally
kept short and evidence-based. Each entry documents what the client saw on the
wire so the backend team can triage without having to rediscover it.

Do **not** work around a backend issue here by silently faking behaviour on the
client. Add a narrow, honest, user-visible degradation path in the client and
link it back to the entry in this file.

## 2026-04-22 · `/v1/events` WebSocket upgrade returns HTTP 426 with `supported: false`

**Endpoint:** `GET wss://<gateway>/v1/events` (mobile WebSocket upgrade).

**Observed response:**

```
HTTP/1.1 426 Upgrade Required
Content-Type: application/json

{
  "type": "events_unavailable",
  "detail": "WebSocket realtime is not implemented on Hermes WebUI /v1/events yet.",
  "supported": false,
  "endpoint": "/v1/events",
  "retryable": true,
  "fallbackPollEndpoints": [
    "/v1/dashboard",
    "/v1/approvals",
    "/v1/conversation"
  ]
}
```

**Reproduction:**

1. Android debug build at `android/app/build/outputs/apk/debug/app-debug.apk`
   installed on a paired device (`adb -s <serial> install -r -t ...`).
2. Launch the app; background thread opens a WebSocket to `/v1/events` with
   `Authorization: Bearer <paired-via-qr token>`.
3. Gateway immediately responds with the 426 body above. `RealWebSocket` raises
   `java.net.ProtocolException: Expected HTTP 101 response but was '426 Upgrade Required'`.
4. Captured raw logcat transcript: `tmp/realtime-diag/initial.log` /
   `tmp/realtime-diag/after-fix.log` (tag `HermesRealtime`).

**Why the client sees "reconnecting" forever (pre-fix):**
the gateway's `retryable: true` hint was honoured but `supported: false` was
not. The client backed off exponentially and re-attempted indefinitely. The
user-facing status string was frozen on "Realtime reconnecting in 30s" so the
UI looked like a transient outage rather than a backend capability gap.

**Client fix (shipped, see `HermesGatewayClient.kt` → `RealtimeConnectionManager`):**

- Parse the `events_unavailable` envelope on any WebSocket `onFailure`.
- When `supported: false`, cancel the reconnect loop instead of backing off.
- Surface an honest status:
  `Realtime unsupported by gateway (polling fallback: /v1/dashboard, /v1/approvals, /v1/conversation)`.
- `retryable: true` is intentionally ignored when `supported: false` — retrying
  a route that the server states is unimplemented is pointless and wastes
  device battery / gateway cycles.

**Requested gateway fixes (tracked here, not patched from the client):**

1. Implement RFC 6455 WebSocket upgrade handling on `GET /v1/events` and emit
   the `RealtimeEventEnvelope` schema from
   `shared/contract/hermes-courier-api.yaml`, or
2. If realtime is intentionally out of scope for this gateway, keep the 426
   response **but** drop `retryable: true` (it contradicts `supported: false`)
   and describe the polling protocol in the OpenAPI contract instead of only
   in the 426 body. The mobile clients can then honour the polling contract
   the same way they honour other documented fallbacks.

**Related contract drift to fix in the YAML:**

- The `/v1/events` path in `hermes-courier-api.yaml` implies WebSocket/SSE works
  today. Either add a `503`/`426` response shape that matches what the gateway
  actually sends, or mark the path as conditionally available with an explicit
  `x-hermes-availability` extension. Clients currently have to parse a body
  shape (`events_unavailable`) that the contract never declares.

## 2026-04-22 · Session control returns HTTP 200 with `supported: false`

**Endpoint:** `POST /v1/sessions/{sessionId}/actions`
(and implicitly the fallback `POST /v1/sessions/{sessionId}/{action}`).

**Observed request and response** (captured on-device against the live paired
gateway, logcat tag `HermesSessionCtl`, session
`20260421_093031_5329e2`):

```
POST /v1/sessions/20260421_093031_5329e2/actions
Authorization: Bearer <paired-via-qr token>
Content-Type: application/json
{"action":"pause"}

HTTP/1.1 200 OK
{
  "sessionId": "20260421_093031_5329e2",
  "action": "pause",
  "status": "unsupported",
  "detail": "Session-control actions are not mapped in Hermes WebUI yet.",
  "updatedAt": "2026-04-22T01:32:26.235611+00:00",
  "supported": false,
  "endpoint": "/v1/sessions/20260421_093031_5329e2/actions"
}
```

Same response shape for `{"action":"resume"}` at
`2026-04-22T01:32:29.236245+00:00`. No state change was observed on the
gateway; both calls are effectively no-ops.

**Android behaviour (correct):** the Android client renders
`Status: pause 20260421_093031_5329e2: unsupported (Session-control actions
are not mapped in Hermes WebUI yet.)` on the session-detail screen and keeps
the session in its prior `idle` state. The mutating round-trip was exercised
end-to-end as a real device validation and was safe.

**Requested gateway fixes:**

1. Implement at least `pause`, `resume`, and `terminate` in the Hermes WebUI
   backend — the OpenAPI already defines the request/response shapes.
2. While unsupported, keep responding with the current `supported: false`
   envelope. Do **not** start returning 200/204 with no body, because the
   mobile clients rely on `supported` to avoid showing false "action
   succeeded" states.
3. Consider 501 Not Implemented over 200 for the unsupported case. 200 with
   `supported: false` works, but HTTP status codes that match semantics help
   any future non-Courier clients.

## 2026-04-22 · Library capabilities not implemented (`/v1/skills`, `/v1/memory`, `/v1/cron`, `/v1/logs`)

**Endpoints:**

- `GET /v1/skills`
- `GET /v1/memory`
- `GET /v1/cron`
- `GET /v1/logs`

**Observed response (live paired gateway, 2026-04-22):** all four return
`HTTP 404 Not Found`. No JSON envelope is provided. These routes are defined in
`shared/contract/hermes-courier-api.yaml` (Phase-1 WebUI parity bundle) and the
Android client treats any `404 / 405 / 501` on a capability listing as
equivalent to an `UnavailablePayload` of the form
`{"type":"<name>_unavailable","supported":false,"endpoint":"<path>", ...}`.

**Reproduction:** pair the device via QR, open the Dashboard, tap any of the
Skills / Memory / Cron / Logs tiles under *Agent library*. The app shows a
red "Gateway declared this capability unavailable" card with the specific
endpoint and an HTTP 404 detail. Screenshots:

- `artifacts/screenshots/dashboard-library.png`
- `artifacts/screenshots/skills-unavailable.png`
- `artifacts/screenshots/memory-unavailable.png`

**Why the client does not retry:** parity with `/v1/events` — an explicit
unavailable signal means we do not hammer the gateway. A manual
"Refresh library" button is available on each library screen so the user can
re-check once the backend ships the feature, without any auto-polling.

**Requested gateway work:** implement the four listing endpoints per the
contract schemas (`Skill`, `MemoryItem`, `CronJob`, `LogEntry`). When a
capability is intentionally scoped out, return the shared `UnavailablePayload`
shape (type `skills_unavailable` / `memory_unavailable` / `cron_unavailable` /
`logs_unavailable`) with a useful `detail` field. Either HTTP 404 or
HTTP 501 is acceptable; the Android client handles both, but returning the
JSON envelope with `supported: false` lets us surface the gateway's own
message verbatim in the UI.
