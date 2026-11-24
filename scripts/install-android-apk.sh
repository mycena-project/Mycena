#!/usr/bin/env bash
# Install Mycena debug APK on a USB-connected device via adb.
#
# Usage:
#   ./scripts/install-android-apk.sh                 # local build output
#   ./scripts/install-android-apk.sh --build         # build then install
#   ./scripts/install-android-apk.sh --from-github   # latest CI debug APK artifact
#   ./scripts/install-android-apk.sh --trigger-github  # run workflow, then download
#   ./scripts/install-android-apk.sh /path/to.apk
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEFAULT_APK="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
APK="$DEFAULT_APK"
DO_BUILD=0
FROM_GITHUB=0
TRIGGER_GITHUB=0
NO_REPLACE=0
PACKAGE="${MYCENA_ANDROID_PACKAGE:-io.github.mycena_project.app}"
REPO="${MYCENA_GITHUB_REPO:-mycena-project/Mycena}"
ARTIFACT="${MYCENA_ANDROID_ARTIFACT:-mycena-debug-apk}"
# Workflows that upload the debug APK (in priority order).
WORKFLOWS=("android-debug-apk.yml" "android-ci.yml")

for arg in "$@"; do
  case "$arg" in
    --build) DO_BUILD=1 ;;
    --from-github) FROM_GITHUB=1 ;;
    --trigger-github) TRIGGER_GITHUB=1; FROM_GITHUB=1 ;;
    --no-replace) NO_REPLACE=1 ;;
    -h|--help)
      sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    --*) echo "Unknown option: $arg" >&2; exit 2 ;;
    *) APK="$arg" ;;
  esac
done

if [[ "$DO_BUILD" -eq 1 ]]; then
  "$ROOT/scripts/mingc-android-build.sh"
  APK="$DEFAULT_APK"
fi

find_github_apk() {
  local tmpdir="$1"
  if ! command -v gh >/dev/null 2>&1; then
    echo "gh CLI required (https://cli.github.com)" >&2
    return 1
  fi

  local wf run_id found=""
  for wf in "${WORKFLOWS[@]}"; do
    run_id="$(gh run list --repo "$REPO" --workflow "$wf" --limit 30 \
      --json databaseId,conclusion \
      -q '[.[] | select(.conclusion=="success")][0].databaseId' 2>/dev/null || true)"
    if [[ -n "$run_id" && "$run_id" != "null" ]]; then
      echo "Using workflow $wf run $run_id"
      gh run download "$run_id" --repo "$REPO" -n "$ARTIFACT" -D "$tmpdir" 2>/dev/null \
        || gh run download "$run_id" --repo "$REPO" -D "$tmpdir"
      found="$(find "$tmpdir" -name '*.apk' | head -1)"
      if [[ -n "$found" ]]; then
        echo "$found"
        return 0
      fi
    fi
  done
  return 1
}

if [[ "$FROM_GITHUB" -eq 1 ]]; then
  tmpdir="$(mktemp -d)"
  trap 'rm -rf "$tmpdir"' EXIT

  if [[ "$TRIGGER_GITHUB" -eq 1 ]]; then
    echo "Starting Android debug APK workflow on $REPO…"
    gh workflow run android-debug-apk.yml --repo "$REPO" --ref main
    echo "Waiting for workflow to finish (up to 90 min)…"
    gh run watch --repo "$REPO" --exit-status || true
  fi

  if ! found="$(find_github_apk "$tmpdir")"; then
    echo "No successful CI APK found on $REPO." >&2
    echo "" >&2
    echo "The debug APK workflow only runs on demand. Try:" >&2
    echo "  ./scripts/install-android-apk.sh --trigger-github" >&2
    echo "  # or after the next green Android CI on main:" >&2
    echo "  ./scripts/install-android-apk.sh --from-github" >&2
    echo "  # or build locally:" >&2
    echo "  ./scripts/install-android-apk.sh --build" >&2
    exit 1
  fi
  APK="$found"
  echo "Using $APK"
fi

if [[ ! -f "$APK" ]]; then
  echo "APK not found: $APK" >&2
  echo "Run: make android-apk  or  ./scripts/install-android-apk.sh --build" >&2
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

install_apk() {
  local output rc
  set +e
  output="$(adb install -r "$APK" 2>&1)"
  rc=$?
  set -e
  if [[ $rc -eq 0 ]]; then
    echo "$output"
    return 0
  fi
  echo "$output" >&2
  if echo "$output" | grep -qE 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match'; then
    if [[ "$NO_REPLACE" -eq 1 ]]; then
      echo "" >&2
      echo "Install blocked: signature mismatch (e.g. Play/CI build vs local debug)." >&2
      echo "Fix: adb uninstall $PACKAGE && adb install \"$APK\"" >&2
      echo "Or rerun without --no-replace (auto-uninstall, app data lost)." >&2
      return 1
    fi
    echo "" >&2
    echo "Signature mismatch — uninstalling $PACKAGE and installing debug APK …" >&2
    echo "(Local debug keys differ from the installed build; app data will be reset.)" >&2
    adb uninstall "$PACKAGE" >/dev/null 2>&1 || adb shell pm uninstall --user 0 "$PACKAGE" >/dev/null 2>&1 || true
    adb install "$APK"
    return $?
  fi
  return "$rc"
}

echo "Installing $APK …"
install_apk
echo "Done. Open Mycena on the device; version is shown under Settings."
