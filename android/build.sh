#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# shellcheck source=../scripts/ensure-rust-env.sh
source "$ROOT/scripts/ensure-rust-env.sh"
require_cargo
add_android_rust_targets
ensure_cargo_ndk

# Gradle reads sdk.dir from local.properties; prefer ANDROID_HOME / ANDROID_SDK_ROOT in CI/Docker.
if [[ -n "${ANDROID_HOME:-}" || -n "${ANDROID_SDK_ROOT:-}" ]]; then
  SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT}}"
  mkdir -p android
  echo "sdk.dir=${SDK_ROOT}" > android/local.properties
fi

# Use -t / -o (cargo-ndk flags). Long forms like --output are passed through to `cargo` and break the build.
ANDROID_TARGETS=(
  aarch64-linux-android
  armv7-linux-androideabi
  x86_64-linux-android
)
if [[ -n "${CI:-}" ]]; then
  # GitHub-hosted runners OOM the disk when three Android release trees plus the
  # restored cargo cache sit together (Android CI: "No space left on device").
  # cargo-ndk -o copies each .so into android/rust-build before we drop target/.
  rm -rf target/aarch64-linux-android target/armv7-linux-androideabi target/x86_64-linux-android
  for t in "${ANDROID_TARGETS[@]}"; do
    cargo ndk -t "$t" -o android/rust-build build --release -p mycelium-ffi
    rm -rf "target/${t}"
  done
else
  cargo ndk \
    -t aarch64-linux-android \
    -t armv7-linux-androideabi \
    -t x86_64-linux-android \
    -o android/rust-build \
    build --release -p mycelium-ffi
fi

mkdir -p android/rust-build
"$ROOT/scripts/android-ffi-stamp.sh" write

"$ROOT/scripts/prune-android-native-libs.sh"

"$ROOT/scripts/regenerate-uniffi-kotlin.sh"

if [[ "${MYCELIUM_SKIP_GRADLE:-0}" == "1" ]]; then
  echo "Skipped Gradle (MYCELIUM_SKIP_GRADLE=1). Native libs + UniFFI are ready."
  exit 0
fi

cd android
# Default: Development APK (debug-signed). Technical Beta: scripts/build-technical-beta-apk.sh
./gradlew assembleDebug
