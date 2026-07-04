#!/usr/bin/env bash
# Clippy gate: mycelium-node, mycelium-core, mycelium-coin, mycelium-miniapp deny
# clippy::unwrap_used / clippy::expect_used in production code (#![deny] in each lib.rs).
# Test modules opt out via #![allow(...)] inside #[cfg(test)] blocks.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "check-no-unwrap-prod: clippy unwrap/expect gate on security crates..."
CARGO_HOME="${CARGO_HOME:-$ROOT/.cargo-home}" cargo clippy \
  -p mycelium-node \
  -p mycelium-core \
  -p mycelium-coin \
  -p mycelium-miniapp \
  --lib \
  -- \
  -D clippy::unwrap_used \
  -D clippy::expect_used

echo "Unwrap/expect production gate OK"
