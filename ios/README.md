
# Hermes Courier iOS

This directory contains the iOS app scaffold for Hermes Courier.

## Stack

- Swift
- SwiftUI
- XcodeGen project manifest
- async secure gateway bootstrap

## Toolchain

- **macOS** with **Xcode** (recent release recommended) — required to build and run the iOS app
- Deployment target **iOS 17.0** (see `project.yml`)
- Install [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen` or equivalent)

## What is here

- app entry point and tab shell (**Dashboard**, **Sessions**, **Approvals**, **Settings**)
- conversation content is shown on **Dashboard**, not as a separate tab
- shared secure API contract models and gateway client aligned with `shared/contract/`
- demo/local gateway behavior with optional live gateway URL in Settings

## Generating the Xcode project

Install XcodeGen, then run:

```bash
cd ios
xcodegen generate
```

After generation, open the produced `HermesCourier.xcodeproj` in Xcode.

### Unit tests (macOS)

Pure logic tests live in `HermesCourierTests/`. They are verified on **macOS** in CI (`.github/workflows/ci.yml`) and can be run locally in two ways:

1. **Xcode:** open `HermesCourier.xcodeproj`, select the **HermesCourier** scheme, then **Product → Test** (`⌘U`).
2. **Command line** (after `xcodegen generate`), using the iOS Simulator — this resolves the first available iPhone simulator on the machine:

```bash
cd ios
xcodegen generate
DESTINATION="$(xcrun simctl list devices available | awk -F '[()]' '/iPhone/ {print $2; exit}')"
xcodebuild \
  -project HermesCourier.xcodeproj \
  -scheme HermesCourier \
  -destination "id=$DESTINATION" \
  -only-testing:HermesCourierTests \
  test
```
