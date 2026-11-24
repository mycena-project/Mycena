#!/usr/bin/env bash
# Write or print the Android FFI build stamp (hash of UDL + lib.rs + Cargo.toml).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STAMP="$ROOT/android/rust-build/.ffi-stamp"
UDL="$ROOT/crates/mycena-ffi/src/mycena.udl"
LIBRS="$ROOT/crates/mycena-ffi/src/lib.rs"
CARGO_TOML="$ROOT/crates/mycena-ffi/Cargo.toml"

compute() {
  {
    [[ -f "$UDL" ]] && shasum -a 256 "$UDL"
    [[ -f "$LIBRS" ]] && shasum -a 256 "$LIBRS"
    [[ -f "$CARGO_TOML" ]] && shasum -a 256 "$CARGO_TOML"
  } 2>/dev/null | shasum -a 256 | awk '{print $1}'
}

case "${1:-}" in
  write)
    mkdir -p "$(dirname "$STAMP")"
    compute >"$STAMP"
    ;;
  *)
    compute
    ;;
esac
