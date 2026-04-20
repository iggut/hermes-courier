
# Hermes Courier Android

This directory contains the Android app scaffold for Hermes Courier.

## Build

Use **JDK 17** or **JDK 21** for Gradle (Android Gradle Plugin aligns with LTS releases). JDK 25+ is not supported by the Kotlin toolchain used here and will fail during the Gradle Kotlin DSL / compiler bootstrap.

## Validation

- JVM unit tests (pure helpers): `./gradlew :app:testDebugUnitTest`
- Use a supported JDK as above; if `java -version` reports JDK 25+, install JDK 17 or 21 and point `JAVA_HOME` at it before running Gradle.

## Stack

- Kotlin
- Jetpack Compose + Material 3
- Navigation Compose
- Single-activity architecture
- coroutine-driven secure gateway bootstrap

## What is here

- bottom navigation: **Dashboard** (includes the live conversation feed), **Sessions**, **Approvals**, and **Settings**
- reusable cards and shell chrome
- secure gateway bootstrap, demo/local gateway client, and EncryptedSharedPreferences-backed settings
- Android Keystore-backed challenge signing and encrypted token storage scaffolding
- shared secure API contract (`shared/contract/`) aligned with the gateway paths

## Next steps

- replace demo gateway with production mTLS transport
- add certificate pinning and device attestation where required by deployment
- deepen integration with live Hermes session and approval APIs
