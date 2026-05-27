#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Gradle reads sdk.dir from local.properties; prefer ANDROID_HOME / ANDROID_SDK_ROOT in CI/Docker.
if [[ -n "${ANDROID_HOME:-}" || -n "${ANDROID_SDK_ROOT:-}" ]]; then
  SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT}}"
  mkdir -p android
  echo "sdk.dir=${SDK_ROOT}" > android/local.properties
fi

rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk || true

# Use -t / -o (cargo-ndk flags). Long forms like --output are passed through to `cargo` and break the build.
cargo ndk \
  -t aarch64-linux-android \
  -t armv7-linux-androideabi \
  -t x86_64-linux-android \
  -o android/rust-build \
  build --release -p mycelium-ffi

cargo run -p mycelium-ffi --bin uniffi-bindgen -- generate \
  crates/mycelium-ffi/src/mycelium.udl \
  --language kotlin \
  --out-dir android/uniffi-bindings/src/main/kotlin/

# When ktlint is missing, UniFFI can emit `} fun` without a newline; repair invalid Kotlin.
UNIFFI_KT="android/uniffi-bindings/src/main/kotlin/uniffi/mycelium/mycelium.kt"
if [[ ! -f "$UNIFFI_KT" ]]; then
  UNIFFI_KT="$(find android/uniffi-bindings/src/main/kotlin -name 'mycelium.kt' | head -1)"
fi
if [[ -n "$UNIFFI_KT" && -f "$UNIFFI_KT" ]]; then
  perl -0777 -pi -e 's/\}\s+fun `addBootstrapPeer`/}\n\nfun `addBootstrapPeer`/' "$UNIFFI_KT"
fi

cd android
./gradlew assembleDebug
