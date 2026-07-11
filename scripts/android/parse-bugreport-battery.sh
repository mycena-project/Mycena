#!/usr/bin/env bash
# Extract battery-relevant lines from a battery-profile-pocket.sh output directory.
#
# Usage:
#   ./scripts/android/parse-bugreport-battery.sh artifacts/battery-profile/<timestamp>
#
set -euo pipefail

DIR="${1:-}"
PACKAGE="${PACKAGE:-network.mycelium.app}"

if [[ -z "$DIR" || ! -d "$DIR" ]]; then
  echo "Usage: $0 <profile-output-dir>" >&2
  exit 2
fi

BS="$DIR/batterystats-dumpsys.txt"
PKG="$DIR/batterystats-package.txt"
BT="$DIR/bluetooth-manager-dumpsys.txt"
PWR="$DIR/power-dumpsys.txt"
META="$DIR/session-meta.txt"

section() {
  echo ""
  echo "======== $1 ========"
}

section "Session"
if [[ -f "$META" ]]; then
  cat "$META"
else
  echo "(no session-meta.txt)"
fi

section "Package UID"
if [[ -f "$BS" ]]; then
  grep -E "Uid u0a[0-9]+: $PACKAGE|Package $PACKAGE" "$BS" | head -5 || echo "(package line not found in batterystats)"
fi

section "Estimated power (mAh) — package"
if [[ -f "$PKG" ]]; then
  grep -E "Estimated power use|Computed drain|Uid |Foreground activities|Background for|Cached for|Total cpu|Total sensor|Wifi|Bluetooth|Mobile radio|Screen" "$PKG" | head -40 || true
  echo "---"
  grep -A2 -i "bluetooth\|ble scan\|scan result" "$PKG" | head -30 || true
else
  echo "(missing batterystats-package.txt)"
fi

section "Wake locks (app + bluetooth)"
for f in "$BS" "$PKG" "$PWR"; do
  [[ -f "$f" ]] || continue
  hits="$(grep -i "wake lock\|wakelock\|Wake lock" "$f" | grep -iE "$PACKAGE|bluetooth|Ble|mesh|mycelium" || true)"
  if [[ -n "$hits" ]]; then
    echo "--- from $(basename "$f") ---"
    echo "$hits" | head -25
  fi
done
if ! grep -qiE "wake lock.*($PACKAGE|mycelium)" "$BS" "$PKG" "$PWR" 2>/dev/null; then
  echo "(no explicit app wakelock lines matched — check Battery Historian Wakelock panel)"
fi

section "BLE / Bluetooth scan (batterystats)"
if [[ -f "$BS" ]]; then
  grep -iE "ble scan|bluetooth scan|scan mode|SCAN_MODE|Bluetooth Misc|Bluetooth Idle|Bluetooth Rx|Bluetooth Tx" "$BS" \
    | grep -iE "scan|bluetooth|ble|$PACKAGE" | head -40 || echo "(no BLE scan section — OEM/Android version dependent)"
fi

section "Bluetooth manager (active scans)"
if [[ -f "$BT" ]]; then
  grep -iE "scan|advertis|appScan|ScanSettings|mode|LOW_|BALANCED|$PACKAGE" "$BT" | head -40 || true
fi

section "Global drain ranking (top UIDs)"
if [[ -f "$BS" ]]; then
  grep -E "^  Uid |^    Total" "$BS" | head -30 || true
fi

section "Logcat hints (scan restarts / BLE tags)"
LOG="$DIR/logcat-meshservice.txt"
if [[ -f "$LOG" ]]; then
  echo "BleScanner/MeshService lines: $(wc -l <"$LOG" | tr -d ' ')"
  grep -iE "scan|advertis|low.?power|restart|WifiAware" "$LOG" | tail -20 || true
else
  echo "(no logcat capture)"
fi

echo ""
echo "Next: open bugreport.zip in Battery Historian → System stats → App selection → $PACKAGE"
echo "Record: CPU / Mobile radio / Wi‑Fi / Bluetooth / Sensors / Wakelocks time series."
