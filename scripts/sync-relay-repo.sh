#!/usr/bin/env bash
# Mirrors the Mycena workspace into the mycena-relay repo (same crate layout as CI/Fly).
# Usage: ./scripts/sync-relay-repo.sh /path/to/mycena-relay-repo
#
# Every crate under crates/ is mirrored (including new members), stale ones are
# removed, and third-party/ plus Cargo.toml/Cargo.lock/rust-toolchain.toml are
# copied, so Fly and CI build with exactly the same workspace as the app.

set -euo pipefail
RELAY_REPO="${1:?Usage: $0 /path/to/mycena-relay-repo}"
WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "Syncing Mycena workspace → $RELAY_REPO"

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

# Crates that no longer exist in the workspace (e.g. after a rename) are removed.
for dest in "$RELAY_REPO/crates"/*; do
  [[ -d "$dest" ]] || continue
  name=$(basename "$dest")
  if [[ ! -d "$WORKSPACE_ROOT/crates/$name" ]]; then
    rm -rf "$dest"
    echo "  removed stale crates/$name"
  fi
done

# Patched dependencies ([patch.crates-io]) and the pinned toolchain.
mkdir -p "$RELAY_REPO/third-party"
rsync -a --delete --exclude target "$WORKSPACE_ROOT/third-party/" "$RELAY_REPO/third-party/"
echo "  synced third-party/"

cp "$WORKSPACE_ROOT/Cargo.toml" "$RELAY_REPO/Cargo.toml"
cp "$WORKSPACE_ROOT/Cargo.lock" "$RELAY_REPO/Cargo.lock"
cp "$WORKSPACE_ROOT/rust-toolchain.toml" "$RELAY_REPO/rust-toolchain.toml"

echo "Done. Review changes in $RELAY_REPO, then commit. Install on Fly.io with that repo's scripts/deploy-relay-fly.sh."
