
# Hermes Courier Secure API Contract

This directory defines the shared API contract used by both the Android and iOS Hermes Courier apps.

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
- `GET /v1/approvals`
- `POST /v1/approvals/{approvalId}/decision`
- `GET /v1/events` for streaming updates

The first release uses demo/local implementations in both apps while the backend gateway is completed.
