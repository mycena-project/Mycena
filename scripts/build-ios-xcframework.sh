#!/usr/bin/env bash
# Build mycelium-ffi for iOS device + simulator and pack MyceliumFFI.xcframework.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
OUT="$ROOT/ios/rust-build"
LIB="libuniffi_mycelium.a"
ARCHS=(aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios)
for t in "${ARCHS[@]}"; do
  rustup target add "$t" 2>/dev/null || true
done
mkdir -p "$OUT"
for t in "${ARCHS[@]}"; do
  echo "Building mycelium-ffi for $t"
  cargo build -p mycelium-ffi --release --target "$t"
done
DEVICE="$ROOT/target/aarch64-apple-ios/release/$LIB"
SIM_ARM="$ROOT/target/aarch64-apple-ios-sim/release/$LIB"
SIM_X86="$ROOT/target/x86_64-apple-ios/release/$LIB"
SIM_UNI="$OUT/sim-universal.a"
lipo -create "$SIM_ARM" "$SIM_X86" -output "$SIM_UNI"
rm -rf "$OUT/MyceliumFFI.xcframework"
xcodebuild -create-xcframework \
  -library "$DEVICE" -headers "$ROOT/crates/mycelium-ffi/include" \
  -library "$SIM_UNI" -headers "$ROOT/crates/mycelium-ffi/include" \
  -output "$OUT/MyceliumFFI.xcframework" 2>/dev/null || {
  echo "Note: create-xcframework may need a module map; link static libs manually — see ios/README.md"
  mkdir -p "$OUT/MyceliumFFI.xcframework"
  cp "$DEVICE" "$OUT/MyceliumFFI.xcframework/device-$LIB"
  cp "$SIM_UNI" "$OUT/MyceliumFFI.xcframework/sim-$LIB"
}
echo "iOS FFI artifacts in $OUT"
