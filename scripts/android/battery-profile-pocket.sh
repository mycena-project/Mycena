#!/usr/bin/env bash
# Pocket / no-peers battery profiling for Mycelium (Battery Historian workflow).
#
# Scenario: BLE bootstrap on (or off for control), no peers, screen off, device on battery.
#
# Usage:
#   ./scripts/android/battery-profile-pocket.sh prepare   # OK with USB — unplug right after
#   # … overnight on battery (4–8 h), screen off …
#   ./scripts/android/battery-profile-pocket.sh collect   # USB OK again for adb
#
# Overnight (recommended):
#   DURATION_HOURS=8 ./scripts/android/battery-profile-pocket.sh overnight
#
# Commands:
#   prepare    Install, reset stats, start MeshService (USB/adb OK — unplug before pocketing)
#   schedule   Detached wait + collect (survives terminal close)
#   collect    Bugreport + summary (validates ≥3 h on-battery since unplug)
#   status     Session / scheduler / battery state
#   all        prepare + schedule
#   overnight  prepare + schedule with DURATION_HOURS=8 (override via env)
#   control    prepare with BLE bootstrap OFF
#
# Environment:
#   DURATION_HOURS=4          Wait before auto-collect (default 4; use 8 overnight)
#   MIN_ON_BATTERY_HOURS=3    Minimum on-battery time for VALID_RUN (default 3)
#   FORCE_ON_AC=1             Skip unplug reminder; keep charging whole run (mAh invalid)
#   SCENARIO=ble_on|mesh_only|ble_off   mesh_only = BLE an, Internet/Rendezvous aus
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PACKAGE="${PACKAGE:-network.mycelium.app}"
DURATION_HOURS="${DURATION_HOURS:-4}"
MIN_ON_BATTERY_HOURS="${MIN_ON_BATTERY_HOURS:-3}"
SCENARIO="${SCENARIO:-ble_on}"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
PROFILE_ROOT="$ROOT/artifacts/battery-profile"
LATEST_FILE="$PROFILE_ROOT/LATEST.txt"
OUT_DIR="${OUT_DIR:-$PROFILE_ROOT/$TS}"
LOGCAT_FILE="$OUT_DIR/logcat-meshservice.txt"
META_FILE="$OUT_DIR/session-meta.txt"
SCHEDULER_PID="$OUT_DIR/scheduler.pid"
SCHEDULER_SCRIPT="$OUT_DIR/run-scheduled.sh"

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

battery_field() {
  local key="$1"
  adb shell dumpsys battery 2>/dev/null | tr -d '\r' | awk -F': ' -v k="$key" '
    $1 ~ k { gsub(/^[ \t]+/, "", $2); print $2; exit }
  '
}

is_on_battery() {
  local ac usb wireless
  ac="$(battery_field "AC powered")"
  usb="$(battery_field "USB powered")"
  wireless="$(battery_field "Wireless powered")"
  [[ "$ac" == "false" && "$usb" == "false" && "$wireless" != "true" ]]
}

warn_charging_at_prepare() {
  if [[ "${FORCE_ON_AC:-0}" == "1" ]]; then
    echo "Note: FORCE_ON_AC=1 — staying on charger; mAh will be invalid." >&2
    return 0
  fi
  if is_on_battery; then
    echo "Device on battery — good for immediate pocketing."
    return 0
  fi
  echo ""
  echo "USB/charger detected — that is OK for setup (adb needs the cable)."
  echo "The drain timer starts when you UNPLUG, not at prepare."
  echo ""
  echo "  → Unplug now, pocket the phone, leave overnight (${DURATION_HOURS}h+ on battery)."
  echo "  → Reconnect USB in the morning only for collect (or let schedule pull it)."
  echo ""
}

git_short_sha() {
  (cd "$ROOT" && git rev-parse --short HEAD 2>/dev/null) || echo "unknown"
}

app_version_name() {
  adb shell dumpsys package "$PACKAGE" 2>/dev/null \
    | tr -d '\r' \
    | awk -F= '/versionName=/ {print $2; exit}'
}

write_latest_pointer() {
  mkdir -p "$PROFILE_ROOT"
  {
    echo "out_dir=$OUT_DIR"
    echo "session_id=$(basename "$OUT_DIR")"
    echo "scenario=$SCENARIO"
    echo "duration_hours=$DURATION_HOURS"
    echo "prepared_utc=$(grep -m1 '^started_utc=' "$META_FILE" 2>/dev/null | cut -d= -f2- || echo "$TS")"
    if [[ -f "$SCHEDULER_PID" ]]; then
      echo "scheduler_pid=$(cat "$SCHEDULER_PID")"
      echo "collect_after_utc=$(grep -m1 '^collect_after_utc=' "$META_FILE" 2>/dev/null | cut -d= -f2- || true)"
    fi
  } >"$LATEST_FILE"
}

resolve_out_dir() {
  if [[ -n "${OUT_DIR:-}" && "$OUT_DIR" != "$PROFILE_ROOT/$TS" ]]; then
    return 0
  fi
  if [[ -f "$LATEST_FILE" ]]; then
    local from_latest
    from_latest="$(grep -m1 '^out_dir=' "$LATEST_FILE" | cut -d= -f2- || true)"
    if [[ -z "$from_latest" ]]; then
      local legacy_id
      legacy_id="$(grep -E '^[0-9]{8}T[0-9]{6}Z$' "$LATEST_FILE" | head -1 || true)"
      if [[ -n "$legacy_id" && -d "$PROFILE_ROOT/$legacy_id" ]]; then
        from_latest="$PROFILE_ROOT/$legacy_id"
      fi
    fi
    if [[ -n "$from_latest" && -d "$from_latest" ]]; then
      OUT_DIR="$from_latest"
      META_FILE="$OUT_DIR/session-meta.txt"
      LOGCAT_FILE="$OUT_DIR/logcat-meshservice.txt"
      SCHEDULER_PID="$OUT_DIR/scheduler.pid"
      SCHEDULER_SCRIPT="$OUT_DIR/run-scheduled.sh"
    fi
  fi
}

apply_profile_prefs() {
  local ble="$1"
  local internet="$2"
  local rendezvous="$3"
  local tmp prefs_tmp
  tmp="$(mktemp)"
  prefs_tmp="$(mktemp)"
  adb shell run-as "$PACKAGE" cat shared_prefs/settings.xml 2>/dev/null >"$prefs_tmp" || true
  if [[ ! -s "$prefs_tmp" ]]; then
    cat >"$prefs_tmp" <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
</map>
XML
  fi
  python3 - "$ble" "$internet" "$rendezvous" "$prefs_tmp" "$tmp" <<'PY'
import sys
import xml.etree.ElementTree as ET

ble, internet, rendezvous, src, dst = sys.argv[1:6]
tree = ET.parse(src)
root = tree.getroot()
updates = {
    "ble_bootstrap_enabled": ble,
    "internet_enabled": internet,
    "rendezvous_enabled": rendezvous,
    "max_connectivity_enabled": "false",
    "crowd_mode_enabled": "false",
}
for key, val in updates.items():
    found = False
    for el in root.findall("boolean"):
        if el.get("name") == key:
            el.set("value", val)
            found = True
            break
    if not found:
        ET.SubElement(root, "boolean", name=key, value=val)
tree.write(dst, encoding="utf-8", xml_declaration=True)
PY
  adb shell run-as "$PACKAGE" mkdir -p shared_prefs
  adb shell run-as "$PACKAGE" tee shared_prefs/settings.xml >/dev/null <"$tmp"
  rm -f "$tmp" "$prefs_tmp"
}

merge_ble_pref() {
  apply_profile_prefs true true true
}

set_ble_pref_off() {
  local tmp prefs_tmp
  tmp="$(mktemp)"
  prefs_tmp="$(mktemp)"
  adb shell run-as "$PACKAGE" cat shared_prefs/settings.xml 2>/dev/null >"$prefs_tmp" || true
  if [[ ! -s "$prefs_tmp" ]]; then
    cat >"$prefs_tmp" <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
</map>
XML
  fi
  python3 - "$prefs_tmp" "$tmp" <<'PY'
import sys
import xml.etree.ElementTree as ET

src, dst = sys.argv[1], sys.argv[2]
tree = ET.parse(src)
root = tree.getroot()
for key, val in [("ble_bootstrap_enabled", "false"), ("max_connectivity", "false")]:
    found = False
    for el in root.findall("boolean"):
        if el.get("name") == key:
            el.set("value", val)
            found = True
            break
    if not found:
        ET.SubElement(root, "boolean", name=key, value=val)
tree.write(dst, encoding="utf-8", xml_declaration=True)
PY
  adb shell run-as "$PACKAGE" mkdir -p shared_prefs
  adb shell run-as "$PACKAGE" tee shared_prefs/settings.xml >/dev/null <"$tmp"
  rm -f "$tmp" "$prefs_tmp"
}

grant_bootstrap_permissions() {
  local perm
  for perm in BLUETOOTH_SCAN BLUETOOTH_ADVERTISE BLUETOOTH_CONNECT \
    ACCESS_FINE_LOCATION NEARBY_WIFI_DEVICES POST_NOTIFICATIONS; do
    adb shell pm grant "$PACKAGE" "android.permission.$perm" 2>/dev/null || true
  done
}

write_meta_header() {
  mkdir -p "$OUT_DIR"
  local level status ac
  level="$(battery_field level)"
  status="$(battery_field status)"
  ac="$(battery_field "AC powered")"
  {
    echo "started_utc=$TS"
    echo "package=$PACKAGE"
    echo "scenario=$SCENARIO"
    echo "duration_hours_requested=$DURATION_HOURS"
    echo "min_on_battery_hours=$MIN_ON_BATTERY_HOURS"
    echo "device_model=$(adb shell getprop ro.product.model | tr -d '\r')"
    echo "android_release=$(adb shell getprop ro.build.version.release | tr -d '\r')"
    echo "build_id=$(adb shell getprop ro.build.id | tr -d '\r')"
    echo "commit=$(git_short_sha)"
    echo "branch=$(cd "$ROOT" && git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
    echo "app_version=$(app_version_name)"
    echo "battery_start_level=$level"
    echo "battery_start_status=$status"
    echo "ac_powered_at_prepare=$ac"
    echo "on_battery_at_prepare=$(is_on_battery && echo true || echo false)"
  } >"$META_FILE"
}

prepare() {
  require_adb
  warn_charging_at_prepare
  mkdir -p "$OUT_DIR"
  write_meta_header
  echo "measurement_window=starts_on_unplug" >>"$META_FILE"

  echo "=== Mycelium pocket battery profile — prepare ==="
  echo "Session:  $(basename "$OUT_DIR")"
  echo "Output:   $OUT_DIR"
  echo "Scenario: $SCENARIO"
  echo "Battery:  $(battery_field level)% ($(is_on_battery && echo on battery || echo charging))"
  echo ""

  if [[ "${SKIP_INSTALL:-0}" != "1" ]]; then
    echo "Installing debug APK…"
    "$ROOT/scripts/install-android-apk.sh" --build
    echo "app_version=$(app_version_name)" >>"$META_FILE"
  fi

  echo "Resetting batterystats…"
  if ! adb shell dumpsys batterystats --reset; then
    echo "Warning: batterystats reset failed — reboot device and retry prepare." >&2
  fi

  echo "Clearing logcat buffer…"
  adb logcat -c

  grant_bootstrap_permissions

  if [[ "$SCENARIO" == "ble_off" ]]; then
    echo "Control run: BLE bootstrap OFF…"
    set_ble_pref_off
    echo "ble_bootstrap_enabled=false" >>"$META_FILE"
  elif [[ "$SCENARIO" == "mesh_only" ]]; then
    echo "Mesh-only: BLE on, Internet + Rendezvous off…"
    apply_profile_prefs true false false
    echo "ble_bootstrap_enabled=true" >>"$META_FILE"
    echo "internet_enabled=false" >>"$META_FILE"
    echo "rendezvous_enabled=false" >>"$META_FILE"
  else
    echo "Full profile: BLE + Internet + Rendezvous on…"
    merge_ble_pref
    echo "ble_bootstrap_enabled=true" >>"$META_FILE"
    echo "internet_enabled=true" >>"$META_FILE"
    echo "rendezvous_enabled=true" >>"$META_FILE"
  fi

  echo "Starting MainActivity (MeshService foreground)…"
  adb shell am force-stop "$PACKAGE"
  sleep 1
  adb shell am start -n "$PACKAGE/.MainActivity"
  sleep 8
  adb shell input keyevent KEYCODE_HOME
  adb shell input keyevent KEYCODE_SLEEP

  sleep 3
  adb shell dumpsys activity services "$PACKAGE/.MeshService" >"$OUT_DIR/meshservice-dumpsys-start.txt" || true

  write_latest_pointer

  echo ""
  echo "=== Pocket checklist ==="
  echo "  [x] Batterystats reset"
  if [[ "$SCENARIO" == "ble_off" ]]; then
    echo "  [x] BLE bootstrap OFF (control run)"
  elif [[ "$SCENARIO" == "mesh_only" ]]; then
    echo "  [x] Mesh-only: BLE on, Internet/Rendezvous off"
  else
    echo "  [x] BLE bootstrap ON (+ Internet/Rendezvous)"
  fi
  if ! is_on_battery && [[ "${FORCE_ON_AC:-0}" != "1" ]]; then
    echo "  [ ] UNPLUG USB/charger NOW (timer starts here)"
  fi
  echo "  [ ] No Mycelium devices within ~30 m"
  echo "  [ ] Screen off — do not force-stop the app"
  echo "  [ ] Stay on battery for ${DURATION_HOURS}+ hours (overnight is fine)"
  echo ""
  echo "Detached collect (recommended — survives terminal close):"
  echo "  OUT_DIR=$OUT_DIR DURATION_HOURS=$DURATION_HOURS \\"
  echo "    ./scripts/android/battery-profile-pocket.sh schedule"
  echo ""
  echo "Or manual collect after pocketing:"
  echo "  OUT_DIR=$OUT_DIR ./scripts/android/battery-profile-pocket.sh collect"
  echo ""
  echo "Status anytime:"
  echo "  ./scripts/android/battery-profile-pocket.sh status"
}

write_scheduler_script() {
  cat >"$SCHEDULER_SCRIPT" <<RUNNER
#!/usr/bin/env bash
set -euo pipefail
ROOT="$ROOT"
OUT_DIR="$OUT_DIR"
DURATION_HOURS="$DURATION_HOURS"
PACKAGE="$PACKAGE"
LOG="$OUT_DIR/collect.log"
echo "\$(date -u +%Y-%m-%dT%H:%MZ) scheduler started pid=\$\$ duration=\${DURATION_HOURS}h" >>"\$LOG"
secs=\$((DURATION_HOURS * 3600))
sleep "\$secs"
echo "\$(date -u +%Y-%m-%dT%H:%MZ) scheduler waking for collect" >>"\$LOG"
export OUT_DIR="\$OUT_DIR"
export PACKAGE="\$PACKAGE"
"\$ROOT/scripts/android/battery-profile-pocket.sh" collect >>"\$LOG" 2>&1
rc=\$?
echo "\$(date -u +%Y-%m-%dT%H:%MZ) collect finished exit=\$rc" >>"\$LOG"
exit "\$rc"
RUNNER
  chmod +x "$SCHEDULER_SCRIPT"
}

schedule() {
  require_adb
  resolve_out_dir
  if [[ ! -f "$META_FILE" ]]; then
    echo "No session at $OUT_DIR — run prepare first." >&2
    exit 2
  fi
  mkdir -p "$OUT_DIR"

  if [[ -f "$SCHEDULER_PID" ]]; then
    local old_pid
    old_pid="$(cat "$SCHEDULER_PID")"
    if kill -0 "$old_pid" 2>/dev/null; then
      echo "Scheduler already running (pid $old_pid) for $OUT_DIR" >&2
      exit 1
    fi
  fi

  local collect_after
  collect_after="$(date -u -v+"${DURATION_HOURS}H" +%Y%m%dT%H%M%SZ 2>/dev/null \
    || date -u -d "+${DURATION_HOURS} hours" +%Y%m%dT%H%M%SZ 2>/dev/null \
    || echo "in_${DURATION_HOURS}h")"
  echo "collect_after_utc=$collect_after" >>"$META_FILE"
  echo "$(date -u +%Y-%m-%dT%H:%MZ) schedule started (${DURATION_HOURS}h → collect ~$collect_after)" \
    >>"$OUT_DIR/collect.log"

  write_scheduler_script
  nohup "$SCHEDULER_SCRIPT" >>"$OUT_DIR/collect.log" 2>&1 &
  echo $! >"$SCHEDULER_PID"
  write_latest_pointer

  echo "=== Scheduled detached collect ==="
  echo "Session:  $(basename "$OUT_DIR")"
  echo "PID:      $(cat "$SCHEDULER_PID")"
  echo "Collect:  ~$collect_after UTC (${DURATION_HOURS}h)"
  echo "Log:      $OUT_DIR/collect.log"
  echo ""
  echo "Unplug device and pocket it now. Check progress:"
  echo "  ./scripts/android/battery-profile-pocket.sh status"
}

collect() {
  require_adb
  resolve_out_dir
  mkdir -p "$OUT_DIR"

  echo "=== Collecting bugreport and batterystats ==="
  echo "Session: $OUT_DIR"
  echo "(bugreport can take several minutes)"
  echo "$(date -u +%Y-%m-%dT%H:%MZ) starting collect" >>"$OUT_DIR/collect.log"

  {
    echo "collected_utc=$(date -u +%Y%m%dT%H%M%SZ)"
    echo "battery_end_level=$(battery_field level)"
    echo "battery_end_status=$(battery_field status)"
    echo "ac_powered_at_collect=$(battery_field "AC powered")"
    echo "on_battery_at_collect=$(is_on_battery && echo true || echo false)"
  } >>"$META_FILE"

  adb logcat -d -s MeshService BleBootstrap BleScanner BleScanDuty WifiAwareDiscovery \
    WifiMulticastLock ThermalMonitor >"$LOGCAT_FILE" 2>/dev/null || true

  adb shell dumpsys batterystats >"$OUT_DIR/batterystats-dumpsys.txt"
  adb shell dumpsys batterystats --checkin >"$OUT_DIR/batterystats-checkin.txt" 2>/dev/null || true
  adb shell dumpsys batterystats "$PACKAGE" >"$OUT_DIR/batterystats-package.txt"
  adb shell dumpsys bluetooth_manager >"$OUT_DIR/bluetooth-manager-dumpsys.txt" 2>/dev/null || true
  adb shell dumpsys wifi >"$OUT_DIR/wifi-dumpsys.txt" 2>/dev/null || true
  adb shell dumpsys power >"$OUT_DIR/power-dumpsys.txt" 2>/dev/null || true

  BUGREPORT_ZIP="$OUT_DIR/bugreport.zip"
  echo "Generating bugreport → $BUGREPORT_ZIP"
  local attempt=0
  while [[ $attempt -lt 3 ]]; do
    if adb bugreport "$BUGREPORT_ZIP"; then
      break
    fi
    attempt=$((attempt + 1))
    echo "bugreport attempt $attempt failed — retry in 10s" >>"$OUT_DIR/collect.log"
    sleep 10
  done
  if [[ ! -f "$BUGREPORT_ZIP" ]]; then
    echo "ERROR: bugreport failed after 3 attempts" >&2
    exit 1
  fi

  echo "Parsing summary…"
  "$ROOT/scripts/android/parse-bugreport-battery.sh" "$OUT_DIR" | tee "$OUT_DIR/summary.txt"

  local valid_line rc=0
  valid_line="$(grep -m1 '^VALID_RUN=' "$OUT_DIR/summary.txt" 2>/dev/null || echo 'VALID_RUN=unknown')"
  echo "$valid_line" >>"$META_FILE"
  echo "$(date -u +%Y-%m-%dT%H:%MZ) collect done $valid_line" >>"$OUT_DIR/collect.log"

  if [[ "$valid_line" == "VALID_RUN=no" ]]; then
    echo "" >&2
    echo "WARNING: Invalid drain window (device was likely on charger or run too short)." >&2
    echo "Re-run: unplug → prepare → pocket ${DURATION_HOURS}h → collect" >&2
    rc=2
  fi

  echo ""
  echo "Upload $BUGREPORT_ZIP to Battery Historian:"
  echo "  https://bathist.ef.lc/"
  echo ""
  echo "Docs snippet: $OUT_DIR/docs-snippet.md"
  echo "Copy into docs/BATTERY_PROFILING_RESULTS.md section 4."

  return "$rc"
}

status_cmd() {
  require_adb
  resolve_out_dir

  echo "=== Battery profile status ==="
  echo "Device battery: $(battery_field level)% ($(is_on_battery && echo on battery || echo charging))"
  echo ""

  if [[ -f "$LATEST_FILE" ]]; then
    echo "Latest session ($(basename "$LATEST_FILE")):"
    sed 's/^/  /' "$LATEST_FILE"
    echo ""
  else
    echo "No LATEST.txt — run prepare first."
    echo ""
  fi

  if [[ -f "$SCHEDULER_PID" ]]; then
    local pid
    pid="$(cat "$SCHEDULER_PID")"
    if kill -0 "$pid" 2>/dev/null; then
      echo "Scheduler: RUNNING pid=$pid"
      if [[ -f "$OUT_DIR/collect.log" ]]; then
        echo "Last log lines:"
        tail -3 "$OUT_DIR/collect.log" | sed 's/^/  /'
      fi
    else
      echo "Scheduler: STOPPED (stale pid $pid)"
      if [[ -f "$OUT_DIR/collect.log" ]]; then
        echo "Last log lines:"
        tail -5 "$OUT_DIR/collect.log" | sed 's/^/  /'
      fi
    fi
  else
    echo "Scheduler: not scheduled"
  fi

  if [[ -f "$OUT_DIR/summary.txt" ]]; then
    echo ""
    grep -E '^VALID_RUN=|^ON_BATTERY_HOURS=|^COMPUTED_DRAIN_MAH=' "$OUT_DIR/summary.txt" 2>/dev/null \
      | sed 's/^/  /' || true
  fi
}

run_all() {
  prepare
  schedule
}

run_overnight() {
  DURATION_HOURS="${DURATION_HOURS:-8}"
  echo "Overnight profile: ${DURATION_HOURS}h on battery after unplug."
  run_all
}

run_mesh_overnight() {
  SCENARIO=mesh_only
  DURATION_HOURS="${DURATION_HOURS:-8}"
  echo "Mesh-only overnight profile: ${DURATION_HOURS}h on battery after unplug."
  run_all
}

cmd="${1:-}"
case "$cmd" in
  prepare) prepare ;;
  collect) collect ;;
  schedule) schedule ;;
  status) status_cmd ;;
  all) run_all ;;
  overnight) run_overnight ;;
  mesh-only) run_mesh_overnight ;;
  mesh-overnight) run_mesh_overnight ;;
  control)
    SCENARIO=ble_off
    prepare
    ;;
  control-all)
    SCENARIO=ble_off
    run_all
    ;;
  control-overnight)
    SCENARIO=ble_off
    run_overnight
    ;;
  -h|--help)
    sed -n '2,35p' "$0" | sed 's/^# \{0,1\}//'
    ;;
  *)
    echo "Usage: $0 {prepare|schedule|collect|status|all|overnight|mesh-only|mesh-overnight|control|control-all|control-overnight}" >&2
    exit 2
    ;;
esac
