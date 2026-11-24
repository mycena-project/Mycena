#!/usr/bin/env bash
# Scorched-earth wipe of all Mycena desktop local data on this Mac.
# Use when re-onboarding still shows old contacts or identity.
#
#   make desktop-nuke
#   ./scripts/nuke-desktop-data.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DATA_DIR="${MYCENA_DATA_DIR:-$HOME/Library/Application Support/mycena}"

"$ROOT/scripts/stop-desktop.sh"

if [[ -d "$DATA_DIR" ]]; then
  echo "Removing $DATA_DIR ..."
  rm -rf "$DATA_DIR"
fi

if [[ -d "$DATA_DIR" ]]; then
  echo "ERROR: could not remove $DATA_DIR" >&2
  exit 1
fi

echo "Desktop data destroyed. Next: make desktop-dev (onboarding will start fresh)."
