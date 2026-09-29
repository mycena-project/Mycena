#!/usr/bin/env bash
# Mirror .github/workflows/ci.yml budget-tier gates before push burns Actions minutes.
set -euo pipefail
# macOS 27: ld-1267 cannot read arm64e.x1 in the CLT SDK. See macos-sdk.sh.
# shellcheck disable=SC1091
source "$(git rev-parse --show-toplevel)/scripts/hooks/macos-sdk.sh"
use_linkable_macos_sdk
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

echo "ci-check: cargo fmt --check"
cargo fmt --all -- --check

echo "ci-check: unwrap / invariant / deps gates"
./scripts/check-no-unwrap-prod.sh
./scripts/check-invariant-whitelist.sh
./scripts/check-unwrap-audit.sh
./scripts/check-dependencies.sh

echo "ci-check: cargo clippy (budget: exclude desktop)"
cargo clippy --workspace --exclude mycelium-desktop --all-targets -- -D warnings

if command -v cargo-nextest >/dev/null 2>&1 || cargo nextest --version >/dev/null 2>&1; then
  echo "ci-check: cargo nextest (lib, budget profile)"
  cargo nextest run --workspace --exclude mycelium-desktop --lib --profile ci-budget
else
  echo "ci-check: cargo test --lib (nextest not installed)"
  cargo test --workspace --exclude mycelium-desktop --lib
fi

echo "ci-check: OK (matches Mycelium CI budget tier)"
