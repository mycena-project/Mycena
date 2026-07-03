#!/usr/bin/env bash
# Fail if app-layer crates import mycelium-core or mycelium-node directly in source.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

APP_LAYER_CRATES=(
  mycelium-app
  mycelium-miniapp
  mycelium-coin
  miniapp-sdk
)

violations=0

for crate in "${APP_LAYER_CRATES[@]}"; do
  toml="crates/$crate/Cargo.toml"
  src="crates/$crate/src"

  if [[ ! -f "$toml" ]]; then
    echo "check-layer-boundaries: missing $toml" >&2
    exit 1
  fi

  if grep -E 'mycelium-core\s*=\s*\{|mycelium-node\s*=\s*\{' "$toml" >/dev/null 2>&1; then
    echo "Layer violation: $crate Cargo.toml depends directly on mycelium-core or mycelium-node"
    grep -E 'mycelium-core|mycelium-node' "$toml" || true
    violations=1
  fi

  if [[ -d "$src" ]] && rg -q 'mycelium_core::|mycelium_node::' "$src" 2>/dev/null; then
    echo "Layer violation: $crate source uses mycelium_core:: or mycelium_node::"
    rg 'mycelium_core::|mycelium_node::' "$src" || true
    violations=1
  fi
done

if [[ "$violations" -ne 0 ]]; then
  exit 1
fi

echo "Layer boundaries OK (${APP_LAYER_CRATES[*]})"
