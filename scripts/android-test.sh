#!/usr/bin/env bash
# Generate UniFFI Kotlin (gitignored) and run Android JVM unit tests.
#
# Does not build an APK. Does not require the Android NDK.
# Requires: Rust/cargo (host), JDK 17, Android SDK (for Gradle).
#
#   ./scripts/android-test.sh
#   ./scripts/android-test.sh --tests io.github.mycena_project.app.bootstrap.ShareApkArtifactTest
#
# Generation is the existing script (scripts/regenerate-uniffi-kotlin.sh), not a second binder.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

"$ROOT/scripts/regenerate-uniffi-kotlin.sh"

KT="$ROOT/android/uniffi-bindings/src/main/kotlin/uniffi/mycena/mycena.kt"
if [[ ! -f "$KT" ]]; then
  echo "FAIL: UniFFI Kotlin still missing after regenerate: $KT" >&2
  exit 1
fi

cd "$ROOT/android"
./gradlew testDebugUnitTest --console=plain "$@"
