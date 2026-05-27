#!/usr/bin/env bash
# Increment android/version.properties (versionCode + patch versionName).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROPS="$ROOT/android/version.properties"

if [[ ! -f "$PROPS" ]]; then
  cat > "$PROPS" <<'EOF'
versionCode=1
versionName=0.1.0
EOF
fi

version_code="$(grep -E '^versionCode=' "$PROPS" | cut -d= -f2 | tr -d '[:space:]')"
version_name="$(grep -E '^versionName=' "$PROPS" | cut -d= -f2 | tr -d '[:space:]')"

[[ "$version_code" =~ ^[0-9]+$ ]] || { echo "Invalid versionCode in $PROPS" >&2; exit 1; }

new_code=$((version_code + 1))

IFS='.' read -r major minor patch _ <<< "${version_name}."
major="${major:-0}"
minor="${minor:-1}"
patch="${patch:-0}"
new_name="${major}.${minor}.$((patch + 1))"

cat > "$PROPS" <<EOF
# Bumped automatically by scripts/bump-android-version.sh and CI debug builds.
versionCode=${new_code}
versionName=${new_name}
EOF

echo "Android version → ${new_name} (versionCode ${new_code})"
