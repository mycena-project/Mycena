#!/usr/bin/env bash
# Regenerate UniFFI bindings for mobile hosts from crates/mycelium-ffi/src/mycelium.udl
#
# Usage:
#   ./scripts/regenerate-uniffi-bindings.sh           # Android (Kotlin) — default
#   ./scripts/regenerate-uniffi-bindings.sh kotlin   # same as default
#   ./scripts/regenerate-uniffi-bindings.sh swift    # iOS (not wired yet)
#   ./scripts/regenerate-uniffi-bindings.sh all      # kotlin only until ios/ exists
#
# See docs/MOBILE_PLATFORMS.md
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

TARGET="${1:-kotlin}"

case "$TARGET" in
  kotlin|android)
    exec "$ROOT/scripts/regenerate-uniffi-kotlin.sh"
    ;;
  swift|ios)
    echo "iOS Swift bindings are not wired yet." >&2
    echo "Planned output: ios/Mycelium/Generated/" >&2
    echo "See ios/README.md and docs/MOBILE_PLATFORMS.md" >&2
    exit 1
    ;;
  all)
    "$ROOT/scripts/regenerate-uniffi-kotlin.sh"
    if [[ -d "$ROOT/ios/Mycelium/Generated" ]]; then
      "$ROOT/scripts/regenerate-uniffi-bindings.sh" swift
    fi
    ;;
  *)
    echo "Unknown target: $TARGET (use kotlin, swift, or all)" >&2
    exit 2
    ;;
esac
