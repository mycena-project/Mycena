#!/usr/bin/env bash
# Regenerate UniFFI Swift bindings for the iOS host.
# Prefer: ./scripts/regenerate-uniffi-bindings.sh [swift|ios|all]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# shellcheck source=ensure-rust-env.sh
source "$ROOT/scripts/ensure-rust-env.sh"
require_cargo

OUT="$ROOT/ios/Mycena/Generated"
rm -rf "$OUT"
mkdir -p "$OUT"

cargo run -p mycena-ffi --bin uniffi-bindgen -- generate \
  crates/mycena-ffi/src/mycena.udl \
  --language swift \
  --out-dir "$OUT"

SWIFT="$(find "$OUT" -name '*.swift' | head -1)"
COUNT="$(find "$OUT" -name '*.swift' | wc -l | tr -d ' ')"
if [[ "$COUNT" -lt 1 ]]; then
  echo "ERROR: expected at least one .swift file in $OUT" >&2
  exit 1
fi
echo "UniFFI Swift: $SWIFT ($(find "$OUT" -name '*.swift' | wc -l | tr -d ' ') files)"
