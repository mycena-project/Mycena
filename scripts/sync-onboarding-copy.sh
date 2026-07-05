#!/usr/bin/env bash
# Copy editable onboarding copy into Android assets (also done by Gradle preBuild).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/ui-copy/onboarding"
DST="$ROOT/android/app/src/main/assets/onboarding"
mkdir -p "$DST"
cp "$SRC"/*.json "$DST/"
echo "synced onboarding copy -> $DST"
