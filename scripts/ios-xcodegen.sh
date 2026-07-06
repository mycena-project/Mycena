#!/usr/bin/env bash
# Generate Mycelium.xcodeproj from project.yml (uses bundled XcodeGen if brew is unavailable).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IOS="$ROOT/ios"
XCODEGEN="$ROOT/scripts/tools/xcodegen/bin/xcodegen"
if [[ ! -x "$XCODEGEN" ]]; then
  if command -v xcodegen >/dev/null 2>&1; then
    XCODEGEN=xcodegen
  else
    echo "XcodeGen not found. Run: curl -fsSL .../xcodegen.zip -o scripts/tools/xcodegen.zip && unzip ..." >&2
    exit 1
  fi
fi
cd "$IOS"
"$XCODEGEN" generate
echo "Generated $IOS/Mycelium.xcodeproj"
