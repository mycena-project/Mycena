#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/ui-copy/data-handling"
DST="$ROOT/android/app/src/main/assets/data-handling"
mkdir -p "$DST"
cp "$SRC"/*.json "$DST/"
echo "synced data-handling copy -> $DST"
