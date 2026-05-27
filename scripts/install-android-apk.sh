#!/usr/bin/env bash
# Install Mycelium debug APK on a USB-connected device via adb.
#
# Usage:
#   ./scripts/install-android-apk.sh                 # local build output
#   ./scripts/install-android-apk.sh --build         # build then install
#   ./scripts/install-android-apk.sh --from-github   # latest CI debug artifact
#   ./scripts/install-android-apk.sh /path/to.apk
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEFAULT_APK="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
APK="$DEFAULT_APK"
DO_BUILD=0
FROM_GITHUB=0
REPO="${MYCELIUM_GITHUB_REPO:-the maintainer/Mycelium}"
WORKFLOW="${MYCELIUM_ANDROID_WORKFLOW:-android-debug-apk.yml}"

for arg in "$@"; do
  case "$arg" in
    --build) DO_BUILD=1 ;;
    --from-github) FROM_GITHUB=1 ;;
    -h|--help)
      sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    --*) echo "Unknown option: $arg" >&2; exit 2 ;;
    *) APK="$arg" ;;
  esac
done

if [[ "$DO_BUILD" -eq 1 ]]; then
  "$ROOT/scripts/build-android-apk.sh"
  APK="$DEFAULT_APK"
fi

if [[ "$FROM_GITHUB" -eq 1 ]]; then
  if ! command -v gh >/dev/null 2>&1; then
    echo "gh CLI required for --from-github (https://cli.github.com)" >&2
    exit 1
  fi
  tmpdir="$(mktemp -d)"
  trap 'rm -rf "$tmpdir"' EXIT
  run_id="$(gh run list --repo "$REPO" --workflow "$WORKFLOW" --limit 20 --json databaseId,conclusion,status \
    -q '[.[] | select(.conclusion=="success")][0].databaseId')"
  if [[ -z "$run_id" || "$run_id" == "null" ]]; then
    echo "No successful '$WORKFLOW' run found on $REPO" >&2
    exit 1
  fi
  echo "Downloading artifact from run $run_id…"
  gh run download "$run_id" --repo "$REPO" -D "$tmpdir"
  found="$(find "$tmpdir" -name '*.apk' | head -1)"
  if [[ -z "$found" ]]; then
    echo "No APK in workflow artifacts" >&2
    exit 1
  fi
  APK="$found"
  echo "Using $APK"
fi

if [[ ! -f "$APK" ]]; then
  echo "APK not found: $APK" >&2
  echo "Run: ./scripts/build-android-apk.sh  or  ./scripts/install-android-apk.sh --build" >&2
  exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found — install Android platform-tools" >&2
  exit 1
fi

devices="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
count="$(echo "$devices" | grep -c . || true)"
if [[ "$count" -eq 0 ]]; then
  echo "No adb device in 'device' state. Enable USB debugging and authorize this computer." >&2
  adb devices
  exit 1
fi
if [[ "$count" -gt 1 ]]; then
  echo "Multiple devices — using first: $(echo "$devices" | head -1)" >&2
fi

echo "Installing $APK …"
adb install -r "$APK"
echo "Done. Open Mycelium on the device; version is shown under Settings."
