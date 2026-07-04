#!/usr/bin/env bash
# Copy canonical web privacy policy into Android in-app assets.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/docs/legal/privacy-policy.html"
DST="$ROOT/android/app/src/main/assets/privacy_policy.html"
if [[ ! -f "$SRC" ]]; then
  echo "missing source: $SRC" >&2
  exit 1
fi
python3 - <<'PY' "$SRC" "$DST"
import re
import sys
from pathlib import Path

src, dst = Path(sys.argv[1]), Path(sys.argv[2])
text = src.read_text(encoding="utf-8")
# In-app WebView does not need canonical link to GitHub Pages.
text = re.sub(r"\s*<link rel=\"canonical\"[^>]*>\n", "\n", text)
text = text.replace(
    "</body>",
    "<p>This document is the in-app and Play Store privacy policy reference (July 2026).</p>\n</body>",
)
dst.parent.mkdir(parents=True, exist_ok=True)
dst.write_text(text, encoding="utf-8")
print(f"synced {src} -> {dst}")
PY
