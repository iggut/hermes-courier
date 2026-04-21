
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

Realtime frames are documented as `RealtimeEventEnvelope` in `hermes-courier-api.yaml` (optional `dashboard`, `sessions`, `approvals`, `conversation`, `approvalResult`, `sessionControlResult`, …).

The first release uses demo/local implementations in both apps when a gateway is unavailable.
