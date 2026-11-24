#!/usr/bin/env bash
# Lean Android debug build with a disk-space guard.
#
#   make android-apk                      # arm64-v8a only (real phones), default budget 10 GB
#   ./scripts/android-build-lean.sh --abi all
#   ./scripts/android-build-lean.sh --budget-gb 8 --keep-cache
#
# What it does
#   1. Checks tools (cargo, rustup, cargo-ndk, Java 17, Android SDK + NDK).
#   2. Refuses to start unless the disk has budget + reserve free.
#   3. Builds the Rust core per ABI, generates the Kotlin bindings, runs Gradle.
#   4. Watches free disk space every 5 s while building. If the build has used
#      more than the budget, or free space drops below the reserve, it stops
#      the build at once and cleans up.
#   5. Copies the APK to artifacts/android/ and removes all intermediates
#      (unless --keep-cache), so the next build starts from a small footprint.
#
# Runs on the host (no Docker). The Docker image mingc/android-build-box is
# far larger than the 10 GB budget on its own; scripts/mingc-android-build.sh
# stays available as `make android-apk-docker` but is not recommended on a laptop.
#
# Options
#   --abi arm64-v8a|armeabi-v7a|x86_64|all   default: arm64-v8a
#   --budget-gb N        max disk the build may consume (default 10)
#   --reserve-gb N       min free space that must always remain (default 5)
#   --keep-cache         keep Rust/Gradle intermediates for faster rebuilds
#   --bump               bump versionCode/versionName before building
set -euo pipefail
set -m   # job control: the build runs in its own process group so the guard can stop it

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ABI="arm64-v8a"
BUDGET_GB=10
RESERVE_GB=5
KEEP_CACHE=0
BUMP=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --abi) ABI="$2"; shift 2 ;;
    --budget-gb) BUDGET_GB="$2"; shift 2 ;;
    --reserve-gb) RESERVE_GB="$2"; shift 2 ;;
    --keep-cache) KEEP_CACHE=1; shift ;;
    --bump) BUMP=1; shift ;;
    -h|--help) sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

case "$ABI" in
  arm64-v8a) TRIPLES=(aarch64-linux-android) ;;
  armeabi-v7a) TRIPLES=(armv7-linux-androideabi) ;;
  x86_64) TRIPLES=(x86_64-linux-android) ;;
  all) TRIPLES=(aarch64-linux-android armv7-linux-androideabi x86_64-linux-android) ;;
  *) echo "Unknown --abi $ABI" >&2; exit 2 ;;
esac

BUILD_DIR="$ROOT/.build-android"
export CARGO_TARGET_DIR="$BUILD_DIR/target"
GUARD_LOG="$BUILD_DIR/disk-guard.log"
ABORT_FLAG="$BUILD_DIR/aborted"

log() { printf '\033[1m[android-build]\033[0m %s\n' "$*"; }
die() { printf '\033[31m[android-build] ERROR:\033[0m %s\n' "$*" >&2; exit 1; }

free_kb() { df -Pk "$ROOT" | awk 'NR==2 {print $4}'; }
gb() { awk -v kb="$1" 'BEGIN { printf "%.1f", kb / 1048576 }'; }

# ---------------------------------------------------------------- cleanup ---
cleanup() {
  local status=$?
  [[ -n "${GUARD_PID:-}" ]] && kill "$GUARD_PID" 2>/dev/null || true
  if [[ "$KEEP_CACHE" -eq 0 ]]; then
    log "Cleaning build intermediates…"
    rm -rf "$BUILD_DIR/target" \
           "$ROOT/android/app/build" \
           "$ROOT/android/uniffi-bindings/build" \
           "$ROOT/android/build" \
           "$ROOT/android/.gradle"
  fi
  rm -f "$ABORT_FLAG"
  log "Free disk space now: $(gb "$(free_kb)") GB"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

# -------------------------------------------------------------- preflight ---
source "$ROOT/scripts/ensure-rust-env.sh"
require_cargo
require_rustup
command -v cargo-ndk >/dev/null 2>&1 || die "cargo-ndk missing: cargo install cargo-ndk --locked"
command -v java >/dev/null 2>&1 || die "Java 17 missing (e.g. brew install --cask temurin@17)"
java -version 2>&1 | grep -qE 'version "(17|21)' || log "Warning: Java 17 is the tested version; found: $(java -version 2>&1 | head -1)"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" && -f android/local.properties ]]; then
  SDK="$(grep -E '^sdk\.dir=' android/local.properties | cut -d= -f2- || true)"
fi
[[ -z "$SDK" && -d "$HOME/Library/Android/sdk" ]] && SDK="$HOME/Library/Android/sdk"
[[ -d "$SDK" ]] || die "Android SDK not found. Install Android Studio or set ANDROID_HOME."
export ANDROID_HOME="$SDK"
if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  NDK_DIR="$(ls -d "$SDK"/ndk/* 2>/dev/null | sort -V | tail -1 || true)"
  [[ -n "$NDK_DIR" ]] || die "Android NDK not found under $SDK/ndk (SDK Manager → SDK Tools → NDK)."
  export ANDROID_NDK_HOME="$NDK_DIR"
fi
[[ -f android/local.properties ]] || echo "sdk.dir=$SDK" > android/local.properties

START_KB="$(free_kb)"
BUDGET_KB=$(( BUDGET_GB * 1048576 ))
RESERVE_KB=$(( RESERVE_GB * 1048576 ))
NEEDED_KB=$(( BUDGET_KB + RESERVE_KB ))
log "Free disk: $(gb "$START_KB") GB · budget ${BUDGET_GB} GB · reserve ${RESERVE_GB} GB · ABI ${ABI}"
if (( START_KB < NEEDED_KB )); then
  die "Need $(gb "$NEEDED_KB") GB free, have $(gb "$START_KB") GB. Free space first (see docs/ANDROID_BUILD.md)."
fi

mkdir -p "$BUILD_DIR"
rm -f "$ABORT_FLAG"

# ------------------------------------------------------------ disk guard ---
disk_guard() {
  local peak=0 now used
  while sleep 5; do
    now="$(free_kb)"
    used=$(( START_KB - now ))
    (( used > peak )) && peak=$used
    echo "$(date +%T) used=$(gb "$used")GB free=$(gb "$now")GB peak=$(gb "$peak")GB" > "$GUARD_LOG"
    if (( used > BUDGET_KB || now < RESERVE_KB )); then
      echo "used=$(gb "$used")GB free=$(gb "$now")GB" > "$ABORT_FLAG"
      kill -TERM -- "-$BUILD_PGID" 2>/dev/null || true
      return 0
    fi
  done
}

# ------------------------------------------------------------- the build ---
build() {
  rustup target add "${TRIPLES[@]}" >/dev/null
  for t in "${TRIPLES[@]}"; do
    log "Rust core → $t"
    cargo ndk -t "$t" -o android/rust-build build --release -p mycena-ffi
    # Each ABI's .so is now in android/rust-build; its build tree is not needed.
    [[ "$KEEP_CACHE" -eq 1 ]] || rm -rf "$CARGO_TARGET_DIR/$t"
  done
  "$ROOT/scripts/android-ffi-stamp.sh" write
  "$ROOT/scripts/prune-android-native-libs.sh"
  log "Kotlin bindings"
  "$ROOT/scripts/regenerate-uniffi-kotlin.sh"
  [[ "$KEEP_CACHE" -eq 1 ]] || rm -rf "$CARGO_TARGET_DIR/debug"
  log "Gradle assembleDebug"
  (cd android && ./gradlew --no-daemon assembleDebug)
}

[[ "$BUMP" -eq 1 ]] && "$ROOT/scripts/bump-android-version.sh"

build &
BUILD_PGID=$!
disk_guard &
GUARD_PID=$!

set +e
wait "$BUILD_PGID"
BUILD_STATUS=$?
set -e
kill "$GUARD_PID" 2>/dev/null || true

if [[ -f "$ABORT_FLAG" ]]; then
  die "Stopped by the disk guard ($(cat "$ABORT_FLAG")). Nothing was left behind."
fi
(( BUILD_STATUS == 0 )) || die "Build failed (exit $BUILD_STATUS)."

APK_SRC="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$APK_SRC" ]] || die "APK not found at $APK_SRC"
VERSION="$(grep -E '^versionName=' android/version.properties | cut -d= -f2)"
mkdir -p "$ROOT/artifacts/android"
APK="$ROOT/artifacts/android/mycena-${VERSION}-${ABI}-debug.apk"
cp "$APK_SRC" "$APK"
log "Peak disk use: $(tail -1 "$GUARD_LOG" 2>/dev/null | sed 's/.*peak=//')"
log "APK: ${APK#$ROOT/}"
log "Install: adb install -r \"$APK\""
