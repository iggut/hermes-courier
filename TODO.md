# Hermes Courier TODO

Generated from a repo audit with Claude Code.

## P0 — Blockers

- [ ] Fix `HermesChallengeSigner.sign()` so the nonce/device payload is actually signed.
  - File: `android/app/src/main/java/com/hermescourier/android/domain/auth/HermesChallengeSigner.kt`
  - Verify: add/update a unit test that signs a known nonce and confirm the signature is non-empty and stable for the same input.

- [ ] Implement `HermesAuthChallengeResponse.toJson()` so it returns the real response payload instead of an empty `JSONObject`.
  - File: `android/app/src/main/java/com/hermescourier/android/domain/gateway/HermesGatewayClient.kt`
  - Verify: inspect the serialized request body in tests and confirm the gateway receives the expected fields.

## P1 — Cleanup and repo hygiene

- [ ] Remove tracked diagnostic leftovers under `tmp/` and `artifacts/` from git.
  - Verify: `git ls-files -- tmp artifacts` returns nothing after cleanup.

- [ ] Add `tmp/` and `artifacts/` to `.gitignore` so they do not get re-added.
  - File: `.gitignore`
  - Verify: create a new file in one of those directories and confirm it stays untracked.

- [ ] Update the README CI section so it matches the current workflows.
  - File: `README.md`
  - Verify: search the README for `ci.yml` and confirm references are accurate.

- [ ] Restore or replace the missing push/PR CI workflow.
  - File: `.github/workflows/ci.yml` or a new replacement workflow file
  - Verify: push a branch and confirm an automated workflow run starts.

## P2 — Stability and maintainability

- [ ] Replace the `runBlocking` call on the main-thread path in `HermesCourierViewModel`.
  - File: `android/app/src/main/java/com/hermescourier/android/domain/HermesCourierViewModel.kt`
  - Verify: the app stays responsive under launch/login smoke testing and no ANR appears.

- [ ] Normalize Kotlin DSL formatting in `android/app/build.gradle.kts`.
  - File: `android/app/build.gradle.kts`
  - Verify: run the Kotlin formatter or a style check and confirm consistent indentation.

- [ ] Review the debug-only cleartext configuration and keep release builds locked down.
  - File: `android/app/build.gradle.kts`
  - Verify: confirm release variants keep cleartext disabled and only debug builds allow it.
