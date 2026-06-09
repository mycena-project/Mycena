#!/usr/bin/env bash
# Exit 0 if Android UniFFI native libs are missing or out of date vs Kotlin/UDL.
# Exit 1 when rust-build artifacts match current FFI (safe for gradle-only APK build).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STAMP="$ROOT/android/rust-build/.ffi-stamp"
UDL="$ROOT/crates/mycelium-ffi/src/mycelium.udl"
LIBRS="$ROOT/crates/mycelium-ffi/src/lib.rs"
CARGO_TOML="$ROOT/crates/mycelium-ffi/Cargo.toml"

stamp_inputs() {
  { [[ -f "$UDL" ]] && shasum -a 256 "$UDL"; } 2>/dev/null || true
  { [[ -f "$LIBRS" ]] && shasum -a 256 "$LIBRS"; } 2>/dev/null || true
  { [[ -f "$CARGO_TOML" ]] && shasum -a 256 "$CARGO_TOML"; } 2>/dev/null || true
}

compute_stamp() {
  stamp_inputs | shasum -a 256 | awk '{print $1}'
}

newest_so_mtime() {
  local newest=0 m path
  while IFS= read -r path; do
    [[ -z "$path" ]] && continue
    m=$(stat -f '%m' "$path" 2>/dev/null || stat -c '%Y' "$path" 2>/dev/null || echo 0)
    if [[ "$m" -gt "$newest" ]]; then
      newest="$m"
    fi
  done < <(find "$ROOT/android/rust-build" -name 'libuniffi_mycelium.so' 2>/dev/null)
  echo "$newest"
}

newest_ffi_source_mtime() {
  local newest=0 m
  for f in "$UDL" "$LIBRS" "$CARGO_TOML"; do
    [[ -f "$f" ]] || continue
    m=$(stat -f '%m' "$f" 2>/dev/null || stat -c '%Y' "$f" 2>/dev/null || echo 0)
    if [[ "$m" -gt "$newest" ]]; then
      newest="$m"
    fi
  done
  echo "$newest"
}

so_has_symbol() {
  local so="$1" sym="$2"
  if command -v llvm-nm >/dev/null 2>&1; then
    llvm-nm -g "$so" 2>/dev/null | grep -q "$sym" && return 0
  fi
  if nm -gU "$so" 2>/dev/null | grep -q "$sym"; then
    return 0
  fi
  if nm -D "$so" 2>/dev/null | grep -q "$sym"; then
    return 0
  fi
  return 1
}

# --- checks ---

if ! find "$ROOT/android/rust-build" -name 'libuniffi_mycelium.so' 2>/dev/null | grep -q .; then
  echo "android-ffi-stale: no libuniffi_mycelium.so in android/rust-build" >&2
  exit 0
fi

expected="$(compute_stamp)"
if [[ ! -f "$STAMP" ]] || [[ "$(cat "$STAMP" 2>/dev/null || true)" != "$expected" ]]; then
  echo "android-ffi-stale: FFI stamp mismatch (rebuild native libs)" >&2
  exit 0
fi

so_mtime="$(newest_so_mtime)"
ffi_mtime="$(newest_ffi_source_mtime)"
if [[ "$ffi_mtime" -gt "$so_mtime" ]]; then
  echo "android-ffi-stale: FFI sources newer than native libs" >&2
  exit 0
fi

# Kotlin may reference symbols absent from an older .so (e.g. after gradle-only regen).
sample_so="$(find "$ROOT/android/rust-build" -name 'libuniffi_mycelium.so' | head -1)"
if [[ -n "$sample_so" ]]; then
  for sym in \
    uniffi_uniffi_mycelium_checksum_func_set_rendezvous_enabled \
    uniffi_uniffi_mycelium_fn_func_set_rendezvous_enabled; do
    if ! so_has_symbol "$sample_so" "$sym"; then
      echo "android-ffi-stale: native lib missing $sym" >&2
      exit 0
    fi
  done
fi

exit 1
