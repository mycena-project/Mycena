#!/usr/bin/env bash
# Observational ADB hardware-validation harness (freeze Technical Beta).
#
# Does NOT wipe, uninstall, grant permissions, or toggle Wi-Fi/Bluetooth.
# Does NOT mark HV rows PASS. Does NOT claim UI / QR / phone-to-phone success.
#
# Usage:
#   ./scripts/android/run-hardware-tests.sh inspect
#   ./scripts/android/run-hardware-tests.sh checkpoint HV-02 "Does onboarding appear correctly?"
#   ./scripts/android/run-hardware-tests.sh snapshot
#
# Device selector: ANDROID_SERIAL
# Package:         MYCENA_ANDROID_PACKAGE (default io.github.mycena_project.app)
# Expected SHA:    MYCENA_TECHNICAL_BETA_SHA256
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PACKAGE="${MYCENA_ANDROID_PACKAGE:-io.github.mycena_project.app}"
RUNS_ROOT="$ROOT/artifacts/hardware-runs"
LATEST_FILE="$RUNS_ROOT/LATEST.txt"
EXPECTED_SHA="${MYCENA_TECHNICAL_BETA_SHA256:-276e653ee6c128445a83b133bf98c9b3b83a306fc461859f4fbb6a9dacf11678}"
VERSION_PROPS="$ROOT/android/version.properties"
CAPTURE="$ROOT/scripts/android/capture-freeze-evidence.sh"

version_name="$(grep -E '^versionName=' "$VERSION_PROPS" | cut -d= -f2)"
version_code="$(grep -E '^versionCode=' "$VERSION_PROPS" | cut -d= -f2)"
DEFAULT_APK="$ROOT/artifacts/technical-beta/mycena-${version_name}-technical-beta-android.apk"
APK="${MYCENA_TECHNICAL_BETA_APK:-$DEFAULT_APK}"

die() { echo "FAIL: $*" >&2; exit 1; }

adb_bin() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    adb -s "$ANDROID_SERIAL" "$@"
  else
    adb "$@"
  fi
}

sha256_file() {
  local f="$1"
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$f" | awk '{print $1}'
  else
    sha256sum "$f" | awk '{print $1}'
  fi
}

find_apksigner() {
  local root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -z "$root" && -f "$ROOT/android/local.properties" ]]; then
    root="$(grep -E '^sdk.dir=' "$ROOT/android/local.properties" | cut -d= -f2- | tr -d '\r')"
  fi
  if [[ -n "$root" ]]; then
    local latest
    latest="$(ls -1d "$root"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"
    if [[ -n "$latest" && -x "$latest" ]]; then
      echo "$latest"
      return 0
    fi
  fi
  command -v apksigner >/dev/null 2>&1 && command -v apksigner && return 0
  return 1
}

run_dir_new() {
  local id="${1:-inspect-$(date -u +%Y%m%dT%H%M%SZ)}"
  local out="$RUNS_ROOT/$id"
  mkdir -p "$out"
  echo "$out" >"$LATEST_FILE"
  echo "$out"
}

run_dir_current() {
  local out="${RUN_DIR:-}"
  if [[ -z "$out" && -f "$LATEST_FILE" ]]; then
    out="$(cat "$LATEST_FILE")"
  fi
  [[ -n "$out" && -d "$out" ]] || die "no run directory; run inspect first or set RUN_DIR"
  echo "$out"
}

cmd="${1:-}"
shift || true

case "$cmd" in
  -h|--help|help|"")
    sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
    ;;
esac

inspect_apk() {
  local out="$1"
  {
    echo "package=$PACKAGE"
    echo "expected_versionName=$version_name"
    echo "expected_versionCode=$version_code"
    echo "expected_sha256=$EXPECTED_SHA"
    echo "apk_path=$APK"
    echo "git_sha=$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
    echo "captured_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  } | tee "$out/apk.txt"

  if [[ ! -f "$APK" ]]; then
    echo "apk_exists=no" | tee -a "$out/apk.txt"
    echo "auto_apk=auto_fail" | tee -a "$out/results.txt"
    return 1
  fi
  echo "apk_exists=yes" | tee -a "$out/apk.txt"
  local sha
  sha="$(sha256_file "$APK")"
  echo "apk_sha256=$sha" | tee -a "$out/apk.txt"
  if [[ "$sha" == "$EXPECTED_SHA" ]]; then
    echo "apk_sha_match=yes" | tee -a "$out/apk.txt"
    echo "auto_apk_sha=auto_ok" | tee -a "$out/results.txt"
  else
    echo "apk_sha_match=NO" | tee -a "$out/apk.txt"
    echo "auto_apk_sha=auto_fail" | tee -a "$out/results.txt"
    return 1
  fi

  if echo "$APK" | grep -qi debug; then
    echo "apk_filename_looks_debug=yes" | tee -a "$out/apk.txt"
    echo "auto_apk_not_debug=auto_fail" | tee -a "$out/results.txt"
    return 1
  fi
  echo "apk_filename_looks_debug=no" | tee -a "$out/apk.txt"

  local signer
  if signer="$(find_apksigner)"; then
    "$signer" verify --print-certs "$APK" >"$out/apksigner-certs.txt" 2>&1 || true
    if grep -qi "CN=Android Debug" "$out/apksigner-certs.txt"; then
      echo "apk_debug_signed=yes" | tee -a "$out/apk.txt"
      echo "auto_apk_signing=auto_fail" | tee -a "$out/results.txt"
      return 1
    fi
    echo "apk_debug_signed=no" | tee -a "$out/apk.txt"
    echo "auto_apk_signing=auto_ok" | tee -a "$out/results.txt"
    awk -F': ' '/SHA-256 digest/{print "apk_cert_sha256="$2; exit}' "$out/apksigner-certs.txt" \
      | tr -d ' ' | tee -a "$out/apk.txt"
  else
    echo "apksigner=unavailable" | tee -a "$out/apk.txt"
    echo "auto_apk_signing=auto_unverified" | tee -a "$out/results.txt"
  fi
  return 0
}

inspect_adb() {
  local out="$1"
  if ! command -v adb >/dev/null 2>&1; then
    echo "adb=missing" | tee "$out/adb.txt"
    echo "auto_adb=auto_fail" | tee -a "$out/results.txt"
    return 1
  fi
  echo "adb=$(command -v adb)" | tee "$out/adb.txt"
  adb_bin devices -l | tee -a "$out/adb.txt"
  local count
  count="$(adb_bin devices | awk 'NR>1 && $2=="device" {c++} END {print c+0}')"
  echo "device_count=$count" | tee -a "$out/adb.txt"
  if [[ "$count" -eq 0 ]]; then
    echo "auto_adb=auto_fail" | tee -a "$out/results.txt"
    echo "device_note=no_device_in_device_state" | tee -a "$out/adb.txt"
    return 1
  fi
  if [[ "$count" -gt 1 && -z "${ANDROID_SERIAL:-}" ]]; then
    echo "auto_adb=auto_fail" | tee -a "$out/results.txt"
    echo "device_note=multiple_devices_set_ANDROID_SERIAL" | tee -a "$out/adb.txt"
    return 1
  fi
  echo "auto_adb=auto_ok" | tee -a "$out/results.txt"
  return 0
}

prop() {
  adb_bin shell getprop "$1" 2>/dev/null | tr -d '\r' || true
}

inspect_device() {
  local out="$1"
  {
    echo "manufacturer=$(prop ro.product.manufacturer)"
    echo "brand=$(prop ro.product.brand)"
    echo "model=$(prop ro.product.model)"
    echo "device=$(prop ro.product.device)"
    echo "android_release=$(prop ro.build.version.release)"
    echo "sdk=$(prop ro.build.version.sdk)"
    echo "oneui=$(prop ro.build.version.oneui)"
    echo "sem_version=$(prop ro.build.version.sem)"
    echo "build_id=$(prop ro.build.id)"
    echo "build_fingerprint=$(prop ro.build.fingerprint)"
    echo "security_patch=$(prop ro.build.version.security_patch)"
    echo "serial_prop=$(prop ro.serialno)"
  } | tee "$out/device.txt"

  local mfr
  mfr="$(prop ro.product.manufacturer | tr '[:upper:]' '[:lower:]')"
  if [[ "$mfr" == *samsung* ]]; then
    echo "oem=samsung" | tee -a "$out/device.txt"
  else
    echo "oem=not_samsung" | tee -a "$out/device.txt"
  fi
  echo "auto_device_identity=auto_ok" | tee -a "$out/results.txt"
}

inspect_installed() {
  local out="$1"
  local dump="$out/dumpsys-package.txt"
  if ! adb_bin shell pm path "$PACKAGE" >"$out/pm-path.txt" 2>"$out/pm-path.err"; then
    echo "package_installed=no" | tee "$out/installed.txt"
    echo "auto_package_installed=auto_unverified" | tee -a "$out/results.txt"
    echo "note=package_not_installed_hv01_not_run" | tee -a "$out/installed.txt"
    return 0
  fi
  echo "package_installed=yes" | tee "$out/installed.txt"
  tr -d '\r' <"$out/pm-path.txt" | tee -a "$out/installed.txt"
  adb_bin shell dumpsys package "$PACKAGE" 2>/dev/null | tr -d '\r' >"$dump" || true
  awk '
    /versionName=/ { gsub(/.*versionName=/,""); print "installed_versionName="$1 }
    /versionCode=/ && !seen++ { gsub(/.*versionCode=/,""); print "installed_versionCode="$1 }
    /pkgFlags=/ { print "pkgFlags="$0 }
  ' "$dump" | tee -a "$out/installed.txt"

  if grep -E 'pkgFlags=.*DEBUGGABLE' "$dump" >/dev/null 2>&1; then
    echo "installed_debuggable=yes" | tee -a "$out/installed.txt"
    echo "auto_installed_not_debug=auto_fail" | tee -a "$out/results.txt"
  else
    echo "installed_debuggable=no" | tee -a "$out/installed.txt"
    echo "auto_installed_not_debug=auto_ok" | tee -a "$out/results.txt"
  fi

  local remote
  remote="$(tr -d '\r' <"$out/pm-path.txt" | sed -n 's/^package://p' | head -1)"
  if [[ -n "$remote" ]]; then
    adb_bin pull "$remote" "$out/installed.apk" >/dev/null 2>"$out/pull.err" || true
    if [[ -f "$out/installed.apk" ]]; then
      local isha
      isha="$(sha256_file "$out/installed.apk")"
      echo "installed_apk_sha256=$isha" | tee -a "$out/installed.txt"
      if [[ "$isha" == "$EXPECTED_SHA" ]]; then
        echo "installed_sha_match=yes" | tee -a "$out/installed.txt"
        echo "auto_installed_sha=auto_ok" | tee -a "$out/results.txt"
      else
        echo "installed_sha_match=NO" | tee -a "$out/installed.txt"
        echo "auto_installed_sha=auto_fail" | tee -a "$out/results.txt"
      fi
      rm -f "$out/installed.apk"
    fi
  fi

  local pid
  pid="$(adb_bin shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
  echo "pid=${pid:-none}" | tee -a "$out/installed.txt"
  echo "auto_process_state=auto_ok" | tee -a "$out/results.txt"

  adb_bin shell dumpsys package "$PACKAGE" 2>/dev/null \
    | tr -d '\r' \
    | awk '/requested permissions:/{p=1;next} /install permissions:/{p=1;next} /runtime permissions:/{p=1} p && /^[[:space:]]/{print} p && /^[^[:space:]]/ && !/permissions/{exit}' \
    >"$out/permissions.txt" || true
  echo "permissions_dump=$out/permissions.txt" | tee -a "$out/installed.txt"

  adb_bin shell ip -o addr 2>/dev/null | tr -d '\r' | awk '{print $2,$3}' >"$out/net-ifaces.txt" || true
  echo "net_ifaces=$out/net-ifaces.txt" | tee -a "$out/installed.txt"
}

inspect_crashes() {
  local out="$1"
  adb_bin logcat -b crash -d -v threadtime >"$out/logcat-crash.txt" 2>/dev/null || true
  adb_bin logcat -d -v threadtime MycenaStability:I AndroidRuntime:E DEBUG:I *:S \
    >"$out/logcat-stability.txt" 2>/dev/null || true
  if grep -q "AndroidRuntime" "$out/logcat-crash.txt" 2>/dev/null; then
    echo "crash_buffer_has_androidruntime=yes" | tee "$out/crash-summary.txt"
    echo "auto_crash_buffer=auto_unverified" | tee -a "$out/results.txt"
  else
    echo "crash_buffer_has_androidruntime=no" | tee "$out/crash-summary.txt"
    echo "auto_crash_buffer=auto_ok" | tee -a "$out/results.txt"
  fi
  echo "note=crash_buffer_ok_is_not_hv_pass" | tee -a "$out/crash-summary.txt"
}

write_catalog() {
  local out="$1"
  cat >"$out/CATALOG.txt" <<'EOF'
Automated (this harness, observational only):
  ADB present; exactly one device (or ANDROID_SERIAL)
  Device manufacturer/model/Android/API/One UI props
  On-disk Technical Beta APK exists; SHA-256 vs freeze expected
  On-disk APK not debug-named; not CN=Android Debug (if apksigner)
  If package installed: versionName/versionCode, debuggable flag, pulled APK SHA
  pidof; permission dump; interface names; crash/MycenaStability buffers

NOT automated (human / later HV; do not claim PASS from inspect):
  HV-01 fresh install (requires uninstall — destructive; operator-only)
  HV-02 first launch UI (spinner vs onboarding vs main)
  HV-03 onboarding complete
  HV-04 permission grant UX
  HV-05 mesh startup UX
  HV-06 BLE air
  HV-07 LAN
  HV-08 messaging (no message bodies in evidence)
  HV-10/11 phone-to-phone
  HV-18 panic wipe (destructive)
  QR scan success
  Samsung Auto Blocker / unknown-sources UX

Human results must be recorded with checkpoint (human_pass / human_fail / human_skip).
Never write a matrix pass from this script.
EOF
}

case "$cmd" in
  inspect)
    out="$(run_dir_new "inspect-$(date -u +%Y%m%dT%H%M%SZ)")"
    : >"$out/results.txt"
    echo "run_dir=$out"
    echo "mode=inspect"
    echo "destructive_tests=none"
    apk_ok=0
    inspect_apk "$out" && apk_ok=1 || apk_ok=0
    adb_ok=0
    if inspect_adb "$out"; then
      adb_ok=1
      inspect_device "$out"
      inspect_installed "$out"
      inspect_crashes "$out"
    fi
    write_catalog "$out"
    echo ""
    echo "=== results ==="
    cat "$out/results.txt"
    echo "evidence=$out"
    echo "hv_rows_marked_pass=none"
    if [[ "$apk_ok" -ne 1 ]]; then
      exit 1
    fi
    if [[ "$adb_ok" -ne 1 ]]; then
      echo "NEXT: connect exactly one authorized device, then re-run inspect."
      echo "HV-01 is NOT proposed until a device is visible to adb."
      exit 2
    fi
    echo "NEXT: operator may run HV-01 manually (uninstall+install). This script will not do that."
    ;;

  checkpoint)
    local_id="${1:-}"
    prompt="${2:-}"
    [[ -n "$local_id" && -n "$prompt" ]] || die "usage: $0 checkpoint HV-02 \"question\""
    out="$(run_dir_current)"
    echo ""
    echo "HUMAN: $prompt [y/N]"
    echo "(result will be human_pass / human_fail — not a matrix PASS)"
    answer=""
    if [[ -n "${HUMAN_ANSWER:-}" ]]; then
      answer="$HUMAN_ANSWER"
    elif [[ -t 0 ]]; then
      read -r answer || true
    else
      die "no TTY; set HUMAN_ANSWER=y|n|s and optional HUMAN_NOTE=..."
    fi
    note="${HUMAN_NOTE:-}"
    result="human_fail"
    case "$(echo "$answer" | tr '[:upper:]' '[:lower:]')" in
      y|yes)
        result="human_pass"
        ;;
      s|skip)
        result="human_skip"
        [[ -n "$note" ]] || {
          if [[ -t 0 ]]; then
            echo "Skip requires a short operator note:"
            read -r note || true
          fi
        }
        [[ -n "$note" ]] || die "human_skip requires HUMAN_NOTE"
        ;;
      *)
        result="human_fail"
        if [[ -z "$note" && -t 0 ]]; then
          echo "Failure requires a short operator note:"
          read -r note || true
        fi
        [[ -n "$note" ]] || die "human_fail requires a note (HUMAN_NOTE=...)"
        ;;
    esac
    {
      echo "test_id=$local_id"
      echo "result=$result"
      echo "note=${note}"
      echo "utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    } | tee -a "$out/human-${local_id}.txt"
    echo "$local_id $result" >>"$out/human-log.txt"
    echo "recorded $result for $local_id in $out (matrix still unrun until a human edits HARDWARE_TEST_MATRIX.md)"
    ;;

  snapshot)
    out="$(run_dir_current)"
    inspect_crashes "$out"
    echo "snapshot written under $out"
    ;;

  *)
    echo "Unknown command: $cmd" >&2
    echo "Try: $0 --help" >&2
    exit 2
    ;;
esac
