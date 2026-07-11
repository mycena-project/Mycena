#!/usr/bin/env bash
# Pocket / no-peers battery profiling for Mycelium (Battery Historian workflow).
#
# Scenario: BLE bootstrap on, no peers in range, screen off, device in pocket/backpack.
# Captures adb bugreport + batterystats after a multi-hour run.
#
# Usage:
#   ./scripts/android/battery-profile-pocket.sh prepare   # reset stats, install, start service
#   # … 4+ hours later, screen off, device stationary …
#   ./scripts/android/battery-profile-pocket.sh collect   # bugreport + summary
#   ./scripts/android/battery-profile-pocket.sh all       # prepare then wait DURATION_HOURS
#
# Environment:
#   DURATION_HOURS=4          Wait time for `all` (default 4)
#   PACKAGE=network.mycelium.app
#   OUT_DIR=artifacts/battery-profile/<timestamp>
#   SKIP_INSTALL=1            Skip APK install during prepare
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PACKAGE="${PACKAGE:-network.mycelium.app}"
DURATION_HOURS="${DURATION_HOURS:-4}"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_DIR="${OUT_DIR:-$ROOT/artifacts/battery-profile/$TS}"
LOGCAT_FILE="$OUT_DIR/logcat-meshservice.txt"
META_FILE="$OUT_DIR/session-meta.txt"

require_adb() {
  if ! command -v adb >/dev/null 2>&1; then
    echo "adb not found — install Android platform-tools" >&2
    exit 1
  fi
  local count
  count="$(adb devices | awk 'NR>1 && $2=="device" {c++} END {print c+0}')"
  if [[ "$count" -eq 0 ]]; then
    echo "No adb device connected (state must be 'device')." >&2
    adb devices
    exit 1
  fi
}

device_info() {
  adb shell getprop ro.product.model 2>/dev/null | tr -d '\r'
  adb shell getprop ro.build.version.release 2>/dev/null | tr -d '\r'
  adb shell getprop ro.build.id 2>/dev/null | tr -d '\r'
}

write_meta() {
  mkdir -p "$OUT_DIR"
  {
    echo "started_utc=$TS"
    echo "package=$PACKAGE"
    echo "duration_hours_requested=$DURATION_HOURS"
    echo "device_model=$(adb shell getprop ro.product.model | tr -d '\r')"
    echo "android_release=$(adb shell getprop ro.build.version.release | tr -d '\r')"
    echo "build_id=$(adb shell getprop ro.build.id | tr -d '\r')"
    echo "scenario=pocket_no_peers_ble_bootstrap_on"
  } >"$META_FILE"
}

prepare() {
  require_adb
  mkdir -p "$OUT_DIR"
  write_meta

  echo "=== Mycelium pocket battery profile — prepare ==="
  echo "Output: $OUT_DIR"
  echo ""

  if [[ "${SKIP_INSTALL:-0}" != "1" ]]; then
    echo "Installing debug APK…"
    "$ROOT/scripts/install-android-apk.sh" --build
  fi

  echo "Resetting batterystats (requires root on some OEM builds)…"
  adb shell dumpsys batterystats --reset || {
    echo "Warning: batterystats reset failed — unplug, reboot device, replug, retry." >&2
  }

  echo "Clearing logcat buffer…"
  adb logcat -c

  echo "Enabling BLE bootstrap in settings prefs (debug run-as)…"
  cat <<'EOF' | adb shell run-as "$PACKAGE" tee shared_prefs/settings.xml >/dev/null
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="ble_bootstrap_enabled" value="true" />
    <boolean name="internet_enabled" value="true" />
    <boolean name="rendezvous_enabled" value="true" />
</map>
EOF
  for perm in BLUETOOTH_SCAN BLUETOOTH_ADVERTISE BLUETOOTH_CONNECT ACCESS_FINE_LOCATION NEARBY_WIFI_DEVICES POST_NOTIFICATIONS; do
    adb shell pm grant "$PACKAGE" "android.permission.$perm" 2>/dev/null || true
  done

  echo "Starting MainActivity (starts MeshService as foreground service)…"
  adb shell am force-stop "$PACKAGE"
  sleep 1
  adb shell am start -n "$PACKAGE/.MainActivity"
  sleep 8
  adb shell input keyevent KEYCODE_HOME
  adb shell input keyevent KEYCODE_SLEEP

  sleep 3
  adb shell dumpsys activity services "$PACKAGE/.MeshService" >"$OUT_DIR/meshservice-dumpsys-start.txt" || true

  echo ""
  echo "Manual checklist before pocketing the device:"
  echo "  [ ] Settings → Bluetooth mesh / BLE bootstrap enabled"
  echo "  [ ] No other Mycelium devices within ~30 m"
  echo "  [ ] Wi‑Fi can stay on (default) or off — note choice in session-meta.txt"
  echo "  [ ] Mobile data / internet as in typical field use (note in meta)"
  echo "  [ ] Screen off, app in background (do not force-stop)"
  echo "  [ ] Leave device stationary in pocket or backpack for ${DURATION_HOURS}+ hours"
  echo ""
  echo "Optional live trace (separate terminal):"
  echo "  adb logcat -s MeshService BleBootstrap BleScanner WifiAwareDiscovery WifiMulticastLock"
  echo ""
  echo "When done, run:"
  echo "  OUT_DIR=$OUT_DIR ./scripts/android/battery-profile-pocket.sh collect"
}

collect() {
  require_adb
  mkdir -p "$OUT_DIR"

  echo "=== Collecting bugreport and batterystats ==="
  echo "Output: $OUT_DIR"
  echo "(bugreport can take several minutes)"

  {
    echo "collected_utc=$(date -u +%Y%m%dT%H%M%SZ)"
    echo "battery_level=$(adb shell dumpsys battery | tr -d '\r' | grep level | head -1)"
  } >>"$META_FILE"

  adb logcat -d -s MeshService BleBootstrap BleScanner WifiAwareDiscovery WifiMulticastLock \
    >"$LOGCAT_FILE" 2>/dev/null || true

  adb shell dumpsys batterystats >"$OUT_DIR/batterystats-dumpsys.txt"
  adb shell dumpsys batterystats --checkin >"$OUT_DIR/batterystats-checkin.txt" 2>/dev/null || true
  adb shell dumpsys batterystats "$PACKAGE" >"$OUT_DIR/batterystats-package.txt"
  adb shell dumpsys bluetooth_manager >"$OUT_DIR/bluetooth-manager-dumpsys.txt" 2>/dev/null || true
  adb shell dumpsys wifi >"$OUT_DIR/wifi-dumpsys.txt" 2>/dev/null || true
  adb shell dumpsys power >"$OUT_DIR/power-dumpsys.txt" 2>/dev/null || true

  BUGREPORT_ZIP="$OUT_DIR/bugreport.zip"
  echo "Generating bugreport → $BUGREPORT_ZIP"
  adb bugreport "$BUGREPORT_ZIP"

  echo "Parsing summary…"
  "$ROOT/scripts/android/parse-bugreport-battery.sh" "$OUT_DIR" | tee "$OUT_DIR/summary.txt"

  echo ""
  echo "Upload $BUGREPORT_ZIP to Battery Historian for interactive charts:"
  echo "  https://bathist.ef.lc/   or   docker run -p 9999:9999 gcr.io/android-battery-historian/stable:3.0"
  echo ""
  echo "Copy measured values into docs/BATTERY_PROFILING_RESULTS.md section 4."
}

run_all() {
  prepare
  secs=$((DURATION_HOURS * 3600))
  echo "Waiting ${DURATION_HOURS}h ($(date -u -v+"${DURATION_HOURS}H" +%Y-%m-%dT%H:%MZ 2>/dev/null || date -u -d "+${DURATION_HOURS} hours" +%Y-%m-%dT%H:%MZ 2>/dev/null || echo 'see clock'))…"
  sleep "$secs"
  collect
}

cmd="${1:-}"
case "$cmd" in
  prepare) prepare ;;
  collect) collect ;;
  all) run_all ;;
  -h|--help)
    sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
    ;;
  *)
    echo "Usage: $0 {prepare|collect|all}" >&2
    exit 2
    ;;
esac
