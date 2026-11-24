#!/usr/bin/env bash
# Verify an official release artifact against an integrity manifest.
#
#   ./scripts/release-verify.sh release-integrity.json path/to/app.apk
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="${1:?usage: release-verify.sh MANIFEST.json ARTIFACT}"
ARTIFACT="${2:?usage: release-verify.sh MANIFEST.json ARTIFACT}"

cargo run -q -p mycena-cli -- release verify "$MANIFEST" "$ARTIFACT" --repo "$ROOT"
