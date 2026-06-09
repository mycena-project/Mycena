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
cargo ndk \
  -t aarch64-linux-android \
  -t armv7-linux-androideabi \
  -t x86_64-linux-android \
  -o android/rust-build \
  build --release -p mycelium-ffi

mkdir -p android/rust-build
"$ROOT/scripts/android-ffi-stamp.sh" write

"$ROOT/scripts/prune-android-native-libs.sh"

"$ROOT/scripts/regenerate-uniffi-kotlin.sh"

cd android
./gradlew assembleDebug
