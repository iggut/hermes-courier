
# Hermes Courier Android

This directory contains the Android app scaffold for Hermes Courier.

## Build

Use **JDK 17** or **JDK 21** for Gradle (Android Gradle Plugin aligns with LTS releases). JDK 25+ is not supported by the Kotlin toolchain used here and will fail during the Gradle Kotlin DSL / compiler bootstrap.

## Validation

- JVM unit tests (pure helpers): `./gradlew :app:testDebugUnitTest`
- Repo script (Android + optional iOS project regen on macOS): `../scripts/platform-validation.sh`
- CI runs the same unit tests on **JDK 17 and 21** (see `.github/workflows/ci.yml`).
- On-demand Android builds are available via `.github/workflows/android-on-demand-build.yml`; run it manually from GitHub Actions to produce a debug APK or debug bundle artifact.
- Use a supported JDK as above; if `java -version` reports JDK 25+, install JDK 17 or 21 and point `JAVA_HOME` at it before running Gradle.

## Stack

- Kotlin
- Jetpack Compose + Material 3
- Navigation Compose
- Single-activity architecture
- coroutine-driven secure gateway bootstrap

## What is here

- bottom navigation: **Dashboard**, **Chat / instruct** (secure message composer + live conversation feed), **Sessions**, **Approvals**, and **Settings**
- reusable cards and shell chrome
- secure gateway bootstrap, demo/local gateway client, explicit live gateway test flow, and EncryptedSharedPreferences-backed settings
- Android Keystore-backed challenge signing and encrypted token storage scaffolding
- shared secure API contract (`shared/contract/`) aligned with the gateway paths

## Operator readiness notes

- `Settings` now includes a per-endpoint verification report with explicit `ok`, `unsupported`, `failed`, `drift`, and `skipped` status labels.
- `Session detail` exposes pause/resume/terminate controls and disables them when verification reports the control endpoints as unsupported.
- `Approvals` includes pending-versus-queued visibility and keeps the latest delivery status visible for retry diagnostics.
