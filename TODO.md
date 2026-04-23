# Hermes Courier TODO

Generated from a repo audit with Claude Code.

All items below were resolved in a single pass. See commit history for details.

## Completed

### P0 — Blockers ✅

- [x] Fix `HermesChallengeSigner.sign()` so the nonce/device payload is actually signed.
  - The `signature.update(buildChallengeSignableMessage(...))` call was missing; signing operated on an empty payload.
  - Extracted `buildChallengeSignableMessage()` as an `internal` function for testability.
  - Added `HermesChallengeSignerTest` (6 JVM tests) verifying non-empty, deterministic, verifiable signatures that cover the full signable message.

- [x] Implement `HermesAuthChallengeResponse.toJson()` so it returns the real response payload instead of an empty `JSONObject`.
  - Populated all four fields: `challengeId`, `nonce`, `expiresAt`, `trustLevel`.
  - Changed visibility from `private` to `internal` for test access.
  - Existing tests in `HermesGatewaySerializationTest` already cover serialization round-trip and determinism.

### P1 — Cleanup and repo hygiene ✅

- [x] Remove tracked diagnostic leftovers under `tmp/` and `artifacts/` from git.
  - Verified: `git ls-files -- tmp artifacts` returns nothing; files were already removed from tracking.

- [x] Add `tmp/` and `artifacts/` to `.gitignore` so they do not get re-added.
  - Entries already present in `.gitignore`.

- [x] Update the README CI section so it matches the current workflows.
  - README correctly references `.github/workflows/ci.yml` which exists and runs on push/PR.

- [x] Restore or replace the missing push/PR CI workflow.
  - `.github/workflows/ci.yml` already exists with Android (JDK 17/21) and iOS unit test jobs.

### P2 — Stability and maintainability ✅

- [x] Replace the `runBlocking` call on the main-thread path in `HermesCourierViewModel`.
  - Removed `runBlocking` from `pairingStatusFromTokenStore()`, converted it to a proper `suspend` function.
  - `initialState()` now uses a static placeholder string instead of calling the now-suspend function.

- [x] Normalize Kotlin DSL formatting in `android/app/build.gradle.kts`.
  - Fixed `dependencies` block indentation (was over-indented inside `android`).

- [x] Review the debug-only cleartext configuration and keep release builds locked down.
  - Already correct: `debug` sets `usesCleartextTraffic=true`, `release` sets `usesCleartextTraffic=false`. No change needed.
