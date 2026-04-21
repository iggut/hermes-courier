
# Hermes Courier Android

This directory contains the Android app scaffold for Hermes Courier.

## Build

Use **JDK 17** or **JDK 21** for Gradle (Android Gradle Plugin aligns with LTS releases). JDK 25+ is not supported by the Kotlin toolchain used here and will fail during the Gradle Kotlin DSL / compiler bootstrap.

## Validation

- JVM unit tests (pure helpers): `./gradlew :app:testDebugUnitTest`
- Debug APK build: `./gradlew app:assembleDebug --no-daemon`
- Repo script (Android + optional iOS project regen on macOS): `../scripts/platform-validation.sh`
- CI runs the same unit tests on **JDK 17 and 21** (see `.github/workflows/ci.yml`).
- On-demand Android builds are available via `.github/workflows/android-on-demand-build.yml`; run it manually from GitHub Actions to produce a debug APK or debug bundle artifact.
- Use a supported JDK as above; if `java -version` reports JDK 25+, install JDK 17 or 21 and point `JAVA_HOME` at it before running Gradle.

## Real-device (ADB) runbook

1. Verify device connection: `adb devices`
2. **Tailscale / production-style HTTPS** — the WebUI pairing QR should use the gateway’s reachable HTTPS base URL (for example a MagicDNS host ending in `.ts.net`). The phone and the WebUI host must be on the same tailnet. Do not rely on `adb reverse` to validate that path; it is only for local `127.0.0.1` or LAN HTTP dev.
3. If using a local Hermes WebUI adapter on port 8787 only, forward it to the device: `adb reverse tcp:8787 tcp:8787`
4. Install debug build:
   - `./gradlew app:assembleDebug --no-daemon`
   - `adb install -r app/build/outputs/apk/debug/app-debug.apk`
5. Reset app state for first-run checks: `adb shell pm clear com.hermescourier.android.debug`
6. Launch app: `adb shell am start -n com.hermescourier.android.debug/com.hermescourier.android.MainActivity`
7. Capture diagnostics:
   - Logs: `adb logcat -d | rg "Hermes|AndroidRuntime|FATAL EXCEPTION"`
   - Screenshot: `adb exec-out screencap -p > /tmp/hermes-courier.png`
   - UI tree: `adb shell uiautomator dump /sdcard/hermes-courier-ui.xml && adb pull /sdcard/hermes-courier-ui.xml`

## Release-readiness notes

- Debug builds allow cleartext traffic to support local development environments.
- Release builds disable cleartext traffic via manifest placeholders; use HTTPS for production/internal release deployment.

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
