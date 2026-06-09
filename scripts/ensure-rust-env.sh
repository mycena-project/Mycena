#!/usr/bin/env bash
# Load Rust toolchain into PATH (rustup installs ~/.cargo/env).
# Source from other scripts:  source "$(dirname "$0")/ensure-rust-env.sh"
set -euo pipefail

_ensure_rust_env() {
  if [[ -f "${HOME}/.cargo/env" ]]; then
    # shellcheck disable=SC1091
    source "${HOME}/.cargo/env"
  fi
  if [[ -d "${CARGO_HOME:-$HOME/.cargo}/bin" ]]; then
    export PATH="${CARGO_HOME:-$HOME/.cargo}/bin:$PATH"
  fi
}

require_cargo() {
  _ensure_rust_env
  if command -v cargo >/dev/null 2>&1; then
    return 0
  fi
  cat >&2 <<'EOF'
ERROR: cargo not found on PATH.

Install Rust (includes rustup + cargo):
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
  source "$HOME/.cargo/env"

Then rerun the Android build.
EOF
  exit 1
}

require_rustup() {
  _ensure_rust_env
  if command -v rustup >/dev/null 2>&1; then
    return 0
  fi
  cat >&2 <<'EOF'
ERROR: rustup not found on PATH.

Recommended (no local Rust): build in Docker
  make android-apk
  ./scripts/docker-android-build.sh

Or install rustup for host builds (make android-apk-local):
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
  source "$HOME/.cargo/env"
  rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
EOF
  exit 1
}

ensure_cargo_ndk() {
  require_cargo
  if command -v cargo-ndk >/dev/null 2>&1; then
    return 0
  fi
  echo "Installing cargo-ndk…" >&2
  cargo install cargo-ndk --locked
}

add_android_rust_targets() {
  require_rustup
  rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
}

# When sourced, run env setup immediately.
_ensure_rust_env
