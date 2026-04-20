#!/usr/bin/env bash
# Platform validation: Android JVM unit tests; on macOS, regenerates the Xcode project when XcodeGen is available.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/android"

echo "==> Android: :app:testDebugUnitTest (JDK: ${JAVA_HOME:-default})"
./gradlew :app:testDebugUnitTest --no-daemon

if [[ "$(uname -s)" == "Darwin" ]] && command -v xcodegen >/dev/null 2>&1; then
  echo "==> iOS: xcodegen generate"
  (cd "$ROOT/ios" && xcodegen generate)
  echo "    OK. Run unit tests on macOS: see ios/README.md (xcodebuild section)."
else
  echo "==> iOS: skipped (requires macOS + XcodeGen for project generation; tests: ios/README.md)"
fi

echo "platform-validation: OK"
