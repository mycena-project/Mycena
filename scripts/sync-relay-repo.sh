#!/usr/bin/env bash
# Spiegelt das Mycelium-Workspace ins mycelium-relay-Repo (gleiche Crate-Struktur wie CI/Fly).
# Usage: ./scripts/sync-relay-repo.sh /path/to/mycelium-relay-repo
#
# Alle Crates unter crates/, die im Ziel-Repo existieren, werden gespiegelt (nicht nur
# core/node), damit z. B. CLI/Desktop/FFI nach Bootstrap-API-Änderungen weiter bauen.

set -euo pipefail
RELAY_REPO="${1:?Usage: $0 /path/to/mycelium-relay-repo}"
WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "Syncing Mycelium workspace → $RELAY_REPO"

shopt -s nullglob
for crate in "$WORKSPACE_ROOT/crates"/*; do
  [[ -d "$crate" ]] || continue
  name=$(basename "$crate")
  dest="$RELAY_REPO/crates/$name"
  if [[ -d "$dest" ]]; then
    mkdir -p "$dest"
    rsync -a --delete --exclude target "$crate/" "$dest/"
    echo "  synced crates/$name"
  fi
done
shopt -u nullglob

cp "$WORKSPACE_ROOT/Cargo.toml" "$RELAY_REPO/Cargo.toml"
cp "$WORKSPACE_ROOT/Cargo.lock" "$RELAY_REPO/Cargo.lock"

echo "Done. Review changes in $RELAY_REPO then commit + push to trigger Fly.io deploy."
