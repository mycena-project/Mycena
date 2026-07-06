#!/usr/bin/env bash
# Build mycelium-ffi for iOS device + simulator and pack MyceliumFFI.xcframework.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# shellcheck source=ensure-rust-env.sh
source "$ROOT/scripts/ensure-rust-env.sh"
require_cargo

export IPHONEOS_DEPLOYMENT_TARGET="${IPHONEOS_DEPLOYMENT_TARGET:-16.0}"
TARGET_DIR="${CARGO_TARGET_DIR:-$ROOT/target}"

"$ROOT/scripts/regenerate-uniffi-bindings.sh" swift

OUT="$ROOT/ios/rust-build"
HEADERS="$OUT/headers"
LIB="libuniffi_mycelium.a"
DEVICE_TRIPLE=aarch64-apple-ios
SIM_TRIPLES=(aarch64-apple-ios-sim)
if rustup target list --installed | grep -qx 'x86_64-apple-ios'; then
  SIM_TRIPLES+=(x86_64-apple-ios)
fi
for t in "$DEVICE_TRIPLE" "${SIM_TRIPLES[@]}"; do
  rustup target add "$t" 2>/dev/null || true
done
mkdir -p "$OUT" "$HEADERS"
cp "$ROOT/ios/Mycelium/Generated/myceliumFFI.h" "$HEADERS/"
cp "$ROOT/ios/Mycelium/Generated/myceliumFFI.modulemap" "$HEADERS/module.modulemap"

for t in "$DEVICE_TRIPLE" "${SIM_TRIPLES[@]}"; do
  echo "Building mycelium-ffi (staticlib) for $t"
  cargo rustc -p mycelium-ffi --release --target "$t" --lib --crate-type staticlib
done

DEVICE="$TARGET_DIR/$DEVICE_TRIPLE/release/$LIB"
SIM_LIBS=()
for t in "${SIM_TRIPLES[@]}"; do
  SIM_LIBS+=("$TARGET_DIR/$t/release/$LIB")
done
SIM_UNI="$OUT/sim-universal.a"
if [[ ${#SIM_LIBS[@]} -eq 1 ]]; then
  cp "${SIM_LIBS[0]}" "$SIM_UNI"
else
  lipo -create "${SIM_LIBS[@]}" -output "$SIM_UNI"
fi
rm -rf "$OUT/MyceliumFFI.xcframework"
if xcodebuild -create-xcframework \
  -library "$DEVICE" -headers "$HEADERS" \
  -library "$SIM_UNI" -headers "$HEADERS" \
  -output "$OUT/MyceliumFFI.xcframework"; then
  echo "iOS FFI xcframework: $OUT/MyceliumFFI.xcframework"
else
  echo "Note: create-xcframework failed; leaving flat static libs in $OUT" >&2
  mkdir -p "$OUT/MyceliumFFI.xcframework"
  cp "$DEVICE" "$OUT/MyceliumFFI.xcframework/device-$LIB"
  cp "$SIM_UNI" "$OUT/MyceliumFFI.xcframework/sim-$LIB"
  cp -R "$HEADERS" "$OUT/MyceliumFFI.xcframework/Headers"
fi
echo "iOS FFI artifacts in $OUT"
