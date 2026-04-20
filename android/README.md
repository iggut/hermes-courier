
# Hermes Courier Android

This directory contains the Android app scaffold for Hermes Courier.

## Build

Use **JDK 17** or **JDK 21** for Gradle (Android Gradle Plugin aligns with LTS releases). JDK 25+ may fail early in the Gradle run.

## Stack

- Kotlin
- Jetpack Compose + Material 3
- Navigation Compose
- Single-activity architecture
- coroutine-driven secure gateway bootstrap

## What is here

- dashboard, chat, approvals, sessions, and settings screens
- reusable cards and shell chrome
- gateway/auth layer scaffolding
- shared secure API contract alignment
- starter Gradle project files

## Next steps

- replace demo gateway with real mTLS transport
- persist tokens in Android Keystore-backed storage
- add certificate pinning and device attestation
- connect real Hermes session and approval APIs
