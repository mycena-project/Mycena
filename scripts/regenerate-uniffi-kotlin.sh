#!/usr/bin/env bash
# Regenerate UniFFI Kotlin bindings (single mycena.kt, no nested duplicates).
# Prefer: ./scripts/regenerate-uniffi-bindings.sh [kotlin|swift|all]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# shellcheck source=ensure-rust-env.sh
source "$ROOT/scripts/ensure-rust-env.sh"
require_cargo

rm -rf android/uniffi-bindings/src/main/kotlin/uniffi/mycena/uniffi
find android/uniffi-bindings/src/main/kotlin -name 'mycena.kt' -delete

cargo run -p mycena-ffi --bin uniffi-bindgen -- generate \
  crates/mycena-ffi/src/mycena.udl \
  --language kotlin \
  --out-dir android/uniffi-bindings/src/main/kotlin/

UNIFFI_KT="$(find android/uniffi-bindings/src/main/kotlin -name 'mycena.kt' | head -1)"
COUNT="$(find android/uniffi-bindings/src/main/kotlin -name 'mycena.kt' | wc -l | tr -d ' ')"
if [[ "$COUNT" -ne 1 ]]; then
  echo "ERROR: expected exactly one mycena.kt, found $COUNT" >&2
  find android/uniffi-bindings/src/main/kotlin -name 'mycena.kt' -print >&2 || true
  exit 1
fi
if [[ -f "$UNIFFI_KT" ]]; then
  perl -0777 -pi -e 's/\}\s+fun `addBootstrapPeer`/}\n\nfun `addBootstrapPeer`/' "$UNIFFI_KT"
fi
echo "UniFFI Kotlin: $UNIFFI_KT"
