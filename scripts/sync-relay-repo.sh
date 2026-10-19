#!/usr/bin/env bash
# Spiegelt das Mycelium-Workspace ins mycelium-relay-Repo (gleiche Crate-Struktur wie CI/Fly).
# Usage: ./scripts/sync-relay-repo.sh /path/to/mycelium-relay-repo
#
# Alle Crates unter crates/ aus dem Mycelium-Workspace werden gespiegelt (inkl. neu
# hinzugekommener Members wie miniapp-sdk), damit Fly/CI mit dem gleichen Cargo.toml baut.

set -euo pipefail
RELAY_REPO="${1:?Usage: $0 /path/to/mycelium-relay-repo}"
WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "Syncing Mycelium workspace → $RELAY_REPO"

shopt -s nullglob
for crate in "$WORKSPACE_ROOT/crates"/*; do
  [[ -d "$crate" ]] || continue
  name=$(basename "$crate")
  dest="$RELAY_REPO/crates/$name"
  mkdir -p "$dest"
  rsync -a --delete --exclude target "$crate/" "$dest/"
  echo "  synced crates/$name"
done
shopt -u nullglob

cp "$WORKSPACE_ROOT/Cargo.toml" "$RELAY_REPO/Cargo.toml"
cp "$WORKSPACE_ROOT/Cargo.lock" "$RELAY_REPO/Cargo.lock"

echo "Done. Review changes in $RELAY_REPO, then commit. Install on VPS with that repo's scripts/deploy-relay-vps.sh."
