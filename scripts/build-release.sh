#!/usr/bin/env bash
# Build a release APK. Set ANDROID_KEYSTORE_* env vars for production signing;
# otherwise Gradle signs with the debug keystore (suitable for local testing only).
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew assembleRelease --no-daemon
apk="$(find app/build/outputs/apk/release -name '*.apk' | head -n 1)"
if [[ -z "$apk" ]]; then
  echo "Release APK not found under app/build/outputs/apk/release" >&2
  exit 1
fi
echo "Release APK: $apk"
