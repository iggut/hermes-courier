
# Hermes Courier

**Hermes Courier** is a secure Android and iOS companion app for controlling [Hermes Agent](https://github.com/iggut/hermes-agent) from your phone.
It is designed for mobile-first operation while preserving the power of the Hermes web experience — and adding a few things only a phone can do well.

Built with the same spirit as [hermes-webui](https://github.com/nesquena/hermes-webui), Hermes Courier aims to provide feature parity with the web UI and extend it with stronger mobile security, faster approvals, and device-native workflows.

## Why it exists

Hermes is powerful when you're at a desk, but you also need a safe way to:

- check what your agent is doing
- approve or deny sensitive actions
- jump into a live conversation
- review memory and recent activity
- manage automations and scheduled tasks
- stay connected while away from your computer

Hermes Courier is built for those moments.

## Core goals

- **All Hermes WebUI features, on mobile**
- **Stronger security by default**
- **Fast, one-handed control**
- **Clear status and intervention tools**
- **Companion-level visibility without exposing raw secrets**

## CI / builds

- Automated checks run on push and pull request in `.github/workflows/ci.yml`.
- Manual Android builds are available on demand in `.github/workflows/android-on-demand-build.yml`.
- The on-demand workflow can produce a debug APK or a debug bundle artifact from GitHub Actions.

## Planned features

### WebUI parity

- live chat and conversation history
- streaming responses and tool execution updates
- model and provider controls
- session browsing and search
- memory visibility and recall tools
- cron / scheduled task management
- logs, errors, and activity views
- approvals for sensitive commands
- multi-platform message routing

### Mobile-first enhancements

- biometric authentication for app unlock and approvals
- hardware-backed secure storage for tokens and session secrets
- push notifications for approvals, alerts, and task completion
- lock-screen-safe notification controls
- quick actions and deep links for common agent tasks
- offline-safe drafts and queued actions
- compact incident view for fast triage
- voice input and voice note support
- mobile-friendly session cards and swipe actions

## Security model

Security is a first-class feature.

Planned protections include:

- end-to-end encrypted transport to the Hermes backend
- device-bound credentials and short-lived session tokens
- biometric gating for sensitive actions
- local secret storage using Android Keystore / hardware-backed APIs where available
- explicit user confirmation for risky operations
- minimal exposure of raw tokens, logs, and private memory on screen

## Suggested architecture

- **Android app:** Kotlin + Jetpack Compose
- **iOS app:** Swift + SwiftUI
- **Auth layer:** secure session bootstrap with short-lived tokens
- **Transport:** HTTPS / secure websocket channel to Hermes
- **State:** local encrypted cache for offline-safe views
- **Notification layer:** push events for approvals, status, and completion alerts
- **Backend companion:** Hermes Agent + the companion routing/memory stack where applicable

## Repository structure

- `android/` — Jetpack Compose Android app (contract-aligned gateway client)
- `ios/` — SwiftUI iOS app generated from `ios/project.yml` (XcodeGen)
- `shared/contract/` — OpenAPI contract and path definitions shared by both clients

## Roadmap

### Phase 1 — Foundation

- establish repo structure
- define API contract with Hermes
- scaffold Android and iOS clients
- prototype secure gateway auth
- wire basic session browsing and approvals

### Phase 2 — Core usage

- real websocket / event stream support
- secure token persistence
- approval workflows
- session history and search
- settings and gateway management

### Phase 3 — Mobile polish

- biometrics and device attestation
- push notifications
- offline-safe queueing
- dark mode polish and accessibility improvements
- launch-ready onboarding

## Status

Both mobile apps implement the shared contract paths (dashboard, sessions, approvals, conversation, approval decisions, and `/v1/events` realtime). Local/demo transports and settings flows are in place; production gateway hardening (pinning, attestation, full backend) remains on the roadmap above.

**UI navigation:** there is no separate “chat” tab — the conversation feed is embedded in **Dashboard** on both platforms (tabs: Dashboard, Sessions, Approvals, Settings).
