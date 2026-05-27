#!/usr/bin/env bash
# Remove stale native libs from android/rust-build (e.g. pre-rename libmycelium_ffi.so).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUST_BUILD="$ROOT/android/rust-build"

if [[ ! -d "$RUST_BUILD" ]]; then
  exit 0
fi

# UniFFI Kotlin loads "uniffi_mycelium" (see mycelium.kt). Legacy .so names must not ship in the APK.
while IFS= read -r -d '' stale; do
  echo "Removing stale native lib: ${stale#$ROOT/}"
  rm -f "$stale"
done < <(find "$RUST_BUILD" -name 'libmycelium_ffi.so' -print0 2>/dev/null)
