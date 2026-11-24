#!/usr/bin/env bash
# Capture device identity and filtered logcat for freeze hardware runs.
# Output lives under artifacts/hardware-runs/ (gitignored). Do not commit it.
#
# Usage:
#   ./scripts/android/capture-freeze-evidence.sh info
#   ./scripts/android/capture-freeze-evidence.sh start [run-id]
#   ./scripts/android/capture-freeze-evidence.sh stop
#   ./scripts/android/capture-freeze-evidence.sh crash
#   ./scripts/android/capture-freeze-evidence.sh apk-sha [path-to-apk]
#
# Multiple devices: export ANDROID_SERIAL=<adb serial>
# Package override:  export MYCENA_ANDROID_PACKAGE=io.github.mycena_project.app
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PACKAGE="${MYCENA_ANDROID_PACKAGE:-io.github.mycena_project.app}"
RUNS_ROOT="$ROOT/artifacts/hardware-runs"
LATEST_FILE="$RUNS_ROOT/LATEST.txt"
DEFAULT_APK="$ROOT/artifacts/technical-beta/mycena-0.1.75-technical-beta-android.apk"
if [[ ! -f "$DEFAULT_APK" ]]; then
  DEFAULT_APK="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
fi

# Crash-relevant tags only. Do not use *:V — chat/mail bodies can appear on other tags.
LOG_TAGS=(
  MycenaStability:I
  MeshService:I
  AndroidRuntime:E
  DEBUG:I
  libc:F
  ShareAppViewModel:I
  WifiDirectSharer:I
  LocalHotspot:I
  WifiDirectShare:I
  WifiPauseHelper:I
  OfflineBootstrap:I
  BleBootstrap:I
  BleScanner:I
  BleScanDuty:I
  BleInviteGatt:I
  WifiDirectBearer:I
  LocalDataWiper:I
  WifiAwareDiscovery:I
)

adb_bin() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    adb -s "$ANDROID_SERIAL" "$@"
  else
    adb "$@"
  fi
}

require_adb() {
  if ! command -v adb >/dev/null 2>&1; then
    echo "adb not found — install Android platform-tools" >&2
    exit 1
  fi
  local count
  count="$(adb_bin devices | awk 'NR>1 && $2=="device" {c++} END {print c+0}')"
  if [[ "$count" -eq 0 ]]; then
    echo "No adb device in 'device' state. Enable USB debugging and authorize this computer." >&2
    adb_bin devices
    exit 1
  fi
  if [[ "$count" -gt 1 && -z "${ANDROID_SERIAL:-}" ]]; then
    echo "Multiple devices — set ANDROID_SERIAL to one of:" >&2
    adb_bin devices
    exit 1
  fi
}

prop() {
  adb_bin shell getprop "$1" 2>/dev/null | tr -d '\r' || true
}

cmd="${1:-}"
shift || true

case "$cmd" in
  -h|--help|help|"")
    sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
    ;;
esac

require_adb

print_info() {
  local apk="${1:-}"
  echo "package=$PACKAGE"
  echo "manufacturer=$(prop ro.product.manufacturer)"
  echo "brand=$(prop ro.product.brand)"
  echo "model=$(prop ro.product.model)"
  echo "device=$(prop ro.product.device)"
  echo "android_release=$(prop ro.build.version.release)"
  echo "sdk=$(prop ro.build.version.sdk)"
  echo "oneui=$(prop ro.build.version.oneui)"
  echo "build_id=$(prop ro.build.id)"
  echo "build_fingerprint=$(prop ro.build.fingerprint)"
  echo "security_patch=$(prop ro.build.version.security_patch)"
  adb_bin shell dumpsys package "$PACKAGE" 2>/dev/null \
    | tr -d '\r' \
    | awk '
        /versionName=/ { gsub(/.*versionName=/,""); print "app_versionName="$1 }
        /versionCode=/ && !seen++ { gsub(/.*versionCode=/,""); print "app_versionCode="$1 }
      '
  echo "git_sha=$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
  echo "captured_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  if [[ -n "$apk" && -f "$apk" ]]; then
    if command -v shasum >/dev/null 2>&1; then
      echo "apk_sha256=$(shasum -a 256 "$apk" | awk '{print $1}')"
    elif command -v sha256sum >/dev/null 2>&1; then
      echo "apk_sha256=$(sha256sum "$apk" | awk '{print $1}')"
    fi
    echo "apk_path=$apk"
  fi
}

case "$cmd" in
  info)
    print_info "${1:-$DEFAULT_APK}"
    ;;

  apk-sha)
    apk="${1:-$DEFAULT_APK}"
    if [[ ! -f "$apk" ]]; then
      echo "APK not found: $apk" >&2
      exit 1
    fi
    if command -v shasum >/dev/null 2>&1; then
      shasum -a 256 "$apk"
    else
      sha256sum "$apk"
    fi
    ;;

  start)
    run_id="${1:-$(date -u +%Y%m%dT%H%M%SZ)}"
    out="$RUNS_ROOT/$run_id"
    mkdir -p "$out"
    echo "$out" >"$LATEST_FILE"
    print_info "$DEFAULT_APK" | tee "$out/device.txt"
    echo "Clearing logcat buffer…"
    adb_bin logcat -c || true
    : >"$out/logcat.txt"
    if [[ -n "${ANDROID_SERIAL:-}" ]]; then
      nohup adb -s "$ANDROID_SERIAL" logcat -v threadtime "${LOG_TAGS[@]}" *:S \
        >>"$out/logcat.txt" 2>"$out/logcat.stderr" &
    else
      nohup adb logcat -v threadtime "${LOG_TAGS[@]}" *:S \
        >>"$out/logcat.txt" 2>"$out/logcat.stderr" &
    fi
    echo $! >"$out/logcat.pid"
    echo "Capturing → $out/logcat.txt (pid $(cat "$out/logcat.pid"))"
    echo "When finished: ./scripts/android/capture-freeze-evidence.sh stop"
    ;;

  stop)
    out="${RUN_DIR:-}"
    if [[ -z "$out" && -f "$LATEST_FILE" ]]; then
      out="$(cat "$LATEST_FILE")"
    fi
    if [[ -z "$out" || ! -d "$out" ]]; then
      echo "No active run. Start one, or set RUN_DIR=artifacts/hardware-runs/<id>" >&2
      exit 1
    fi
    if [[ -f "$out/logcat.pid" ]]; then
      pid="$(cat "$out/logcat.pid")"
      kill "$pid" 2>/dev/null || true
      wait "$pid" 2>/dev/null || true
      rm -f "$out/logcat.pid"
    fi
    adb_bin logcat -b crash -d -v threadtime >"$out/logcat-crash.txt" 2>/dev/null || true
    adb_bin shell dumpsys dropbox --print data_app_crash >"$out/dropbox-data_app_crash.txt" 2>/dev/null || true
    adb_bin shell dumpsys dropbox --print SYSTEM_TOMBSTONE >"$out/dropbox-tombstone.txt" 2>/dev/null || true
    echo "Stopped. Files in $out"
    echo "Copy a one-line pointer into docs/HARDWARE_TEST_MATRIX.md — do not commit this directory."
    ;;

  crash)
    echo "=== logcat crash buffer ==="
    adb_bin logcat -b crash -d -v threadtime || true
    echo ""
    echo "=== filtered MycenaStability / AndroidRuntime ==="
    adb_bin logcat -d -v threadtime MycenaStability:I AndroidRuntime:E DEBUG:I *:S || true
    echo ""
    echo "=== dropbox data_app_crash (may be empty) ==="
    adb_bin shell dumpsys dropbox --print data_app_crash 2>/dev/null | tail -n 200 || true
    ;;

  *)
    echo "Unknown command: $cmd" >&2
    echo "Try: $0 --help" >&2
    exit 2
    ;;
esac
