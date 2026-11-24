#!/usr/bin/env bash
# Extract battery-relevant metrics from battery-profile-pocket.sh output.
#
# Usage:
#   ./scripts/android/parse-bugreport-battery.sh artifacts/battery-profile/<timestamp>
#
# Writes:
#   stdout + summary fields (VALID_RUN, ON_BATTERY_HOURS, …)
#   <dir>/docs-snippet.md — paste into BATTERY_PROFILING_RESULTS.md section 4
#
set -euo pipefail

DIR="${1:-}"
PACKAGE="${PACKAGE:-io.github.mycena_project.app}"
MIN_ON_BATTERY_HOURS="${MIN_ON_BATTERY_HOURS:-3}"

if [[ -z "$DIR" || ! -d "$DIR" ]]; then
  echo "Usage: $0 <profile-output-dir>" >&2
  exit 2
fi

BS="$DIR/batterystats-dumpsys.txt"
PKG="$DIR/batterystats-package.txt"
BT="$DIR/bluetooth-manager-dumpsys.txt"
PWR="$DIR/power-dumpsys.txt"
META="$DIR/session-meta.txt"
SNIPPET="$DIR/docs-snippet.md"

section() {
  echo ""
  echo "======== $1 ========"
}

# --- parse helpers (python for reliable extraction) ---
read -r -d '' PARSE_PY <<'PY' || true
import re
import sys
from pathlib import Path

package = sys.argv[1]
pkg_path = Path(sys.argv[2])
bt_path = Path(sys.argv[3])
meta_path = Path(sys.argv[4])
min_hours = float(sys.argv[5])

def read_text(p):
    try:
        return p.read_text(errors="replace")
    except FileNotFoundError:
        return ""

pkg = read_text(pkg_path)
bt = read_text(bt_path)
meta = read_text(meta_path)

def parse_duration_ms(text: str) -> int:
    """Parse Android batterystats durations like '20h 17m 22s 632ms' or '0ms'."""
    text = text.strip().split("(")[0].strip()
    if not text or text == "0ms":
        return 0
    ms_only = re.fullmatch(r"(\d+)ms", text)
    if ms_only:
        return int(ms_only.group(1))
    ms_tail = 0
    m = re.search(r"(\d+)ms", text)
    if m:
        ms_tail = int(m.group(1))
        text = text[: m.start()].strip()
    total = ms_tail
    m = re.search(r"(\d+)h", text)
    if m:
        total += int(m.group(1)) * 3_600_000
    m = re.search(r"(\d+)m", text)
    if m:
        total += int(m.group(1)) * 60_000
    m = re.search(r"(\d+)s", text)
    if m:
        total += int(m.group(1)) * 1_000
    return total

def meta_get(key, default=""):
    m = re.search(rf"^{re.escape(key)}=(.*)$", meta, re.M)
    return m.group(1).strip() if m else default

on_battery_ms = 0
m = re.search(r"Time on battery:\s*([^(]+)", pkg)
if m:
    on_battery_ms = parse_duration_ms(m.group(1))
on_battery_h = on_battery_ms / 3_600_000

computed_drain = ""
m = re.search(r"Computed drain:\s*([\d.]+)", pkg)
if m:
    computed_drain = m.group(1)

actual_drain = ""
m = re.search(r"actual drain:\s*([\d.]+)", pkg)
if m:
    actual_drain = m.group(1)

bt_scan_ms = 0
m = re.search(r"Bluetooth scan time:\s*([^\n]+)", pkg)
if m:
    bt_scan_ms = parse_duration_ms(m.group(1).split("(")[0])

# App UID breakdown (cpu=/bluetooth=/mobile_radio= line under UID u0a…)
uid_mah = {}
uid_total = ""
for line in pkg.splitlines():
    m = re.search(r"UID u0a\d+:\s*([\d.]+)", line)
    if m:
        uid_total = m.group(1)
    if re.search(r"cpu=[\d.]+.*mobile_radio=[\d.]+", line):
        for key in ("cpu", "bluetooth", "mobile_radio", "wifi", "sensors"):
            m = re.search(rf"{key}=([\d.]+)", line)
            if m:
                uid_mah[key] = m.group(1)
        break

# BLE: parse each `  io.github.mycena_project.app:` block; keep the one with most scan time
ble = {
    "scan_starts": "",
    "scan_stops": "",
    "scan_total_ms": "",
    "low_power_ms": "",
    "balanced_ms": "",
    "low_latency_ms": "",
    "scan_results": "",
}
best_total = -1
in_block = False
cur = {}
pkg_hdr = re.compile(rf"^  {re.escape(package)}(?: \(Registered\))?:$")
for line in bt.splitlines():
    if pkg_hdr.match(line):
        if in_block and cur.get("scan_total_ms"):
            total = int(cur["scan_total_ms"])
            if total > best_total:
                best_total = total
                ble = cur.copy()
        in_block = True
        cur = {
            "scan_starts": "",
            "scan_stops": "",
            "scan_total_ms": "",
            "low_power_ms": "",
            "balanced_ms": "",
            "low_latency_ms": "",
            "scan_results": "",
        }
        continue
    if not in_block:
        continue
    if re.match(r"^  \S", line) and not line.startswith("    "):
        if cur.get("scan_total_ms"):
            total = int(cur["scan_total_ms"])
            if total > best_total:
                best_total = total
                ble = cur.copy()
        in_block = False
        cur = {}
        continue
    m = re.search(r"LE scans\s+\(Started/Stopped\)\s*:\s*(\d+)\s*/\s*(\d+)", line)
    if m:
        cur["scan_starts"], cur["scan_stops"] = m.group(1), m.group(2)
    m = re.search(
        r"Scan time\(ms\)\s+\(Active/Suspend/Total\)\s*:\s*[\d]+\s*/\s*[\d]+\s*/\s*(\d+)",
        line,
    )
    if m:
        cur["scan_total_ms"] = m.group(1)
    m = re.search(
        r"Scan time per mode\(ms\).*?:\s*[\d]+\s*/\s*(\d+)\s*/\s*(\d+)\s*/\s*(\d+)",
        line,
    )
    if m:
        cur["low_power_ms"], cur["balanced_ms"], cur["low_latency_ms"] = (
            m.group(1),
            m.group(2),
            m.group(3),
        )
    m = re.search(
        r"Number of results\s+\(ScreenOff/ScreenOn/Total\)\s*:\s*[\d]+\s*/\s*[\d]+\s*/\s*(\d+)",
        line,
    )
    if m:
        cur["scan_results"] = m.group(1)
if in_block and cur.get("scan_total_ms"):
    total = int(cur["scan_total_ms"])
    if total > best_total:
        ble = cur.copy()

# Valid if enough on-battery time and non-zero drain (collect over USB is OK)
valid = on_battery_ms >= int(min_hours * 3_600_000) and computed_drain not in ("", "0")

print(f"VALID_RUN={'yes' if valid else 'no'}")
print(f"ON_BATTERY_MS={on_battery_ms}")
print(f"ON_BATTERY_HOURS={on_battery_h:.2f}")
print(f"COMPUTED_DRAIN_MAH={computed_drain or 'n/a'}")
print(f"ACTUAL_DRAIN_MAH={actual_drain or 'n/a'}")
print(f"BT_SCAN_MS={bt_scan_ms if bt_scan_ms else 'n/a'}")
print(f"UID_TOTAL_MAH={uid_total or 'n/a'}")
print(f"BLE_LP_MS={ble['low_power_ms'] or 'n/a'}")
print(f"BLE_BALANCED_MS={ble['balanced_ms'] or 'n/a'}")
print(f"BLE_LL_MS={ble['low_latency_ms'] or 'n/a'}")
print(f"BLE_SCAN_TOTAL_MS={ble['scan_total_ms'] or 'n/a'}")
print(f"BLE_SCAN_SESSIONS={ble['scan_starts'] or 'n/a'}/{ble['scan_stops'] or 'n/a'}")
print(f"BLE_SCAN_RESULTS={ble['scan_results'] or 'n/a'}")
for k, v in uid_mah.items():
    print(f"MAH_{k.upper()}={v}")
print(f"SESSION_ID={meta_get('started_utc', Path(sys.argv[6]).name)}")
print(f"SCENARIO={meta_get('scenario', 'ble_on')}")
print(f"COMMIT={meta_get('commit', 'n/a')}")
print(f"APP_VERSION={meta_get('app_version', 'n/a')}")
print(f"BATTERY_START={meta_get('battery_start_level', 'n/a')}")
print(f"BATTERY_END={meta_get('battery_end_level', 'n/a')}")
PY

METRICS="$(
  python3 -c "$PARSE_PY" "$PACKAGE" "$PKG" "$BT" "$META" "$MIN_ON_BATTERY_HOURS" "$(basename "$DIR")" 2>/dev/null \
    || echo "VALID_RUN=no"
)"

get_metric() {
  local key="$1"
  echo "$METRICS" | awk -F= -v k="$key" '$1==k {print $2; exit}'
}

VALID_RUN="$(get_metric VALID_RUN)"
ON_BATTERY_HOURS="$(get_metric ON_BATTERY_HOURS)"
COMPUTED_DRAIN="$(get_metric COMPUTED_DRAIN_MAH)"

section "Validity"
echo "VALID_RUN=$VALID_RUN"
echo "ON_BATTERY_HOURS=$ON_BATTERY_HOURS (minimum ${MIN_ON_BATTERY_HOURS}h required)"
echo "COMPUTED_DRAIN_MAH=$COMPUTED_DRAIN"
if [[ "$VALID_RUN" == "no" ]]; then
  echo ""
  echo "INVALID: On-battery time below ${MIN_ON_BATTERY_HOURS}h or zero computed drain."
  echo "Stay unplugged from prepare through the pocket window (USB at collect is OK)."
fi

section "Session"
if [[ -f "$META" ]]; then
  cat "$META"
else
  echo "(no session-meta.txt)"
fi

section "Package drain (mAh categories)"
if [[ -f "$PKG" ]]; then
  grep -E "Time on battery:|Computed drain:|actual drain:|Bluetooth scan time:" "$PKG" | head -10 || true
  echo "---"
  grep -E "^\s+(cpu|mobile_radio|wifi|bluetooth|sensors|camera|flashlight):" "$PKG" | head -15 || true
  echo "---"
  grep -E "Estimated power use|Uid u0a|Foreground service|Background for" "$PKG" | head -20 || true
else
  echo "(missing batterystats-package.txt)"
fi

section "BLE scan (app block, largest)"
if [[ -f "$BT" ]]; then
  awk -v pkg="$PACKAGE" '
    $0 ~ "^  " pkg ":$" || $0 ~ "^  " pkg " \\(Registered\\):$" { show=1; print; next }
    show && /^  [A-Za-z0-9._-]+/ && $0 !~ /^    / && $0 !~ pkg { exit }
    show { print }
  ' "$BT" | head -30 || echo "(no app scan block found)"
else
  echo "(missing bluetooth-manager-dumpsys.txt)"
fi

section "Wake locks (app)"
for f in "$BS" "$PKG" "$PWR"; do
  [[ -f "$f" ]] || continue
  hits="$(grep -i "wake lock" "$f" | grep -iE "$PACKAGE|mycena" || true)"
  if [[ -n "$hits" ]]; then
    echo "--- $(basename "$f") ---"
    echo "$hits" | head -15
  fi
done

section "Logcat (duty cycle / thermal)"
LOG="$DIR/logcat-meshservice.txt"
if [[ -f "$LOG" ]]; then
  lines="$(wc -l <"$LOG" | tr -d ' ')"
  echo "Lines captured: $lines"
  grep -iE "duty|thermal|scan|BleScanDuty|paused|window" "$LOG" | tail -15 || true
else
  echo "(no logcat capture)"
fi

# --- docs snippet ---
{
  echo "### Session \`$(get_metric SESSION_ID)\` ($([ "$(get_metric SCENARIO)" = ble_off ] && echo control || echo measurement))"
  echo ""
  echo "| | |"
  echo "|--|--|"
  echo "| Valid | **$VALID_RUN** |"
  echo "| Scenario | $(get_metric SCENARIO) |"
  echo "| App | \`$PACKAGE\` v$(get_metric APP_VERSION), commit \`$(get_metric COMMIT)\` |"
  echo "| On battery | ${ON_BATTERY_HOURS} h |"
  echo "| SOC start → end | $(get_metric BATTERY_START)% → $(get_metric BATTERY_END)% |"
  echo "| Computed drain | $(get_metric COMPUTED_DRAIN_MAH) mAh |"
  echo ""
  if [[ "$VALID_RUN" == "yes" ]]; then
    echo "**mAh split (Mycena UID, screen-off FGS):**"
    echo ""
    echo "| Kategorie | mAh |"
    echo "|-----------|-----|"
    echo "| **UID gesamt** | $(get_metric UID_TOTAL_MAH) |"
    for pair in "cpu:MAH_CPU" "mobile_radio:MAH_MOBILE_RADIO" "wifi:MAH_WIFI" "bluetooth:MAH_BLUETOOTH" "sensors:MAH_SENSORS"; do
      label="${pair%%:*}"
      key="${pair##*:}"
      val="$(get_metric "$key")"
      [[ -n "$val" && "$val" != "n/a" ]] && echo "| $label | $val |"
    done
    echo ""
  fi
  echo "**BLE scan:**"
  echo ""
  echo "| Metrik | Wert |"
  echo "|--------|------|"
  lp="$(get_metric BLE_LP_MS)"
  bal="$(get_metric BLE_BALANCED_MS)"
  total="$(get_metric BLE_SCAN_TOTAL_MS)"
  if [[ "$lp" != "n/a" && "$total" != "n/a" && "$total" -gt 0 ]]; then
    pct=$((lp * 100 / total))
    echo "| LOW_POWER | ${lp} ms (~${pct} %) |"
  else
    echo "| LOW_POWER ms | $lp |"
  fi
  echo "| BALANCED ms | $bal |"
  echo "| LOW_LATENCY ms | $(get_metric BLE_LL_MS) |"
  echo "| Scan total ms | $total |"
  echo "| Sessions | $(get_metric BLE_SCAN_SESSIONS) |"
  echo "| Results | $(get_metric BLE_SCAN_RESULTS) |"
  echo ""
} >"$SNIPPET"

echo ""
echo "Wrote $SNIPPET"
echo "Next: paste snippet into docs/BATTERY_PROFILING_RESULTS.md section 4."
