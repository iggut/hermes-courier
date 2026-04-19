
# Hermes Courier iOS

This directory contains the iOS app scaffold for Hermes Courier.

## Stack

- Swift
- SwiftUI
- XcodeGen project manifest
- async secure gateway bootstrap

## What is here

- app entry point and root shell
- dashboard, chat, approvals, sessions, and settings views
- shared secure API contract models
- placeholder gateway auth and client services

## Generating the Xcode project

Install XcodeGen, then run:

```bash
cd ios
xcodegen generate
```

After generation, open the produced `HermesCourier.xcodeproj` in Xcode.
