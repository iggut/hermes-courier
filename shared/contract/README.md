
# Hermes Courier Secure API Contract

This directory defines the shared API contract used by both the Android and iOS Hermes Courier apps.

**Source of truth:** Hermes-agent / WebUI should publish the canonical protocol; this YAML should be updated from that pipeline or verified against it. The repo script `scripts/check-contract-paths.sh` fails CI if documented routes diverge from client path constants (`android/.../HermesApiPaths.kt`, `ios/.../HermesContract.swift`).

## Goals

- zero-trust mobile gateway access
- short-lived authentication sessions
- explicit approval workflows
- portable models for both platforms

## Contract surface

- `POST /v1/auth/challenge`
- `POST /v1/auth/response`
- `GET /v1/dashboard`
- `GET /v1/sessions`
- `GET /v1/sessions/{sessionId}`
- `POST /v1/sessions/{sessionId}/actions` (body: `{ "action": "pause" | "resume" | "terminate" }`, optional empty body on alternate routes)
- `POST /v1/sessions/{sessionId}/{action}` (alternate control style; empty JSON body)
- `GET /v1/approvals`
- `POST /v1/approvals/{approvalId}/decision` (body: `decision` = `approve` \| `deny`, optional `reason`)
- `GET /v1/conversation` for the dashboard companion feed
- `POST /v1/conversation` to submit a chat/instruct message
- `GET /v1/events` for streaming updates (mobile clients use a WebSocket to this path)
- `GET /v1/skills` and `GET /v1/skills/{skillId}` (read-only skills / tools listing)
- `GET /v1/memory` and `GET /v1/memory/{memoryId}` (read-only memory entries)
- `GET /v1/cron` and `GET /v1/cron/{cronId}` (read-only scheduled task listing)
- `GET /v1/logs` (recent log / activity entries; optional `limit` and `severity` query params)

Realtime frames are documented as `RealtimeEventEnvelope` in `hermes-courier-api.yaml` (optional `dashboard`, `sessions`, `approvals`, `conversation`, `approvalResult`, `sessionControlResult`, …).

The first release uses demo/local implementations in both apps when a gateway is unavailable.

## Optional / conditional capabilities

A number of routes can respond with an `UnavailablePayload` shape when the
gateway has not implemented the feature. The format is:

```json
{
  "type": "events_unavailable",
  "supported": false,
  "detail": "<short human-readable reason>",
  "endpoint": "/v1/events",
  "retryable": false,
  "fallbackPollEndpoints": ["/v1/dashboard"]
}
```

Clients must honour `supported: false` as terminal (no retries). `/v1/events`,
`/v1/sessions/{sessionId}/actions`, `/v1/skills`, `/v1/memory`, `/v1/cron`, and
`/v1/logs` are all allowed to respond this way. Backend gaps observed in
practice are tracked in `BACKEND_ISSUES.md`.
