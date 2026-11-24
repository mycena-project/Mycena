#!/usr/bin/env bash
# Clippy gate: mycena-node, mycena-core, mycena-coin, mycena-miniapp deny
# clippy::unwrap_used / clippy::expect_used in production code (#![deny] in each lib.rs).
# Test modules opt out via #![allow(...)] inside #[cfg(test)] blocks.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "check-no-unwrap-prod: clippy unwrap/expect gate on security crates..."
CARGO_HOME="${CARGO_HOME:-$ROOT/.cargo-home}" cargo clippy \
  -p mycena-node \
  -p mycena-core \
  -p mycena-coin \
  -p mycena-miniapp \
  --lib \
  -- \
  -D clippy::unwrap_used \
  -D clippy::expect_used

echo "Unwrap/expect production gate OK"
