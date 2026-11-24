#!/usr/bin/env bash
# Fail if app-layer crates import mycena-core or mycena-node directly in source.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

APP_LAYER_CRATES=(
  mycena-app
  mycena-miniapp
  mycena-coin
  miniapp-sdk
  mycena-desktop
  mycena-cli
  mycena-relay
)

violations=0

for crate in "${APP_LAYER_CRATES[@]}"; do
  toml="crates/$crate/Cargo.toml"
  src="crates/$crate/src"

  if [[ ! -f "$toml" ]]; then
    echo "check-layer-boundaries: missing $toml" >&2
    exit 1
  fi

  if grep -E 'mycena-core\s*=\s*\{|mycena-node\s*=\s*\{' "$toml" >/dev/null 2>&1; then
    echo "Layer violation: $crate Cargo.toml depends directly on mycena-core or mycena-node"
    grep -E 'mycena-core|mycena-node' "$toml" || true
    violations=1
  fi

  if [[ -d "$src" ]] && rg -q 'mycena_core::|mycena_node::' "$src" 2>/dev/null; then
    echo "Layer violation: $crate source uses mycena_core:: or mycena_node::"
    rg 'mycena_core::|mycena_node::' "$src" || true
    violations=1
  fi

  if [[ -d "$src" ]] && rg -q 'as_core_keypair|node-internal' "$src" "$toml" 2>/dev/null; then
    echo "Layer violation: $crate references as_core_keypair or node-internal (removed — use EncKey / L1 node secrets)"
    rg 'as_core_keypair|node-internal' "$src" "$toml" || true
    violations=1
  fi
done

if [[ "$violations" -ne 0 ]]; then
  exit 1
fi

echo "Layer boundaries OK (${APP_LAYER_CRATES[*]})"
