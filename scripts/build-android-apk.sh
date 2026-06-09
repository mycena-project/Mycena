#!/usr/bin/env bash
# Build a debug APK.
#
# Default (no flag): mingc/android-build-box via mingc-android-build.sh
#   (make android-apk / make android-deploy — no local rustup).
#
# Host build (--local):
#   ./scripts/build-android-apk.sh --local           # bump; Rust if FFI stale
#   ./scripts/build-android-apk.sh --local --no-bump
#   ./scripts/build-android-apk.sh --local --full    # always Rust NDK + UniFFI + Gradle
#   ./scripts/build-android-apk.sh --local --gradle-only
#
# Docker:
#   ./scripts/build-android-apk.sh              # same as make android-apk
#   ./scripts/build-android-apk.sh --no-bump
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUMP=1
MODE="gradle"
USE_DOCKER=1
MIN_FREE_KB=$((5 * 1024 * 1024)) # 5 GiB

for arg in "$@"; do
  case "$arg" in
    --no-bump) BUMP=0 ;;
    --local) USE_DOCKER=0 ;;
    --docker) USE_DOCKER=1 ;;
    --full) MODE="full"; USE_DOCKER=0 ;;
    --gradle-only) BUMP=0; MODE="gradle"; USE_DOCKER=0 ;;
    -h|--help)
      sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

if [[ "$USE_DOCKER" -eq 1 ]]; then
  docker_args=()
  [[ "$BUMP" -eq 0 ]] && docker_args+=(--no-bump)
  exec "$ROOT/scripts/mingc-android-build.sh" "${docker_args[@]}"
fi

avail_kb="$(df -k "$ROOT" | awk 'NR==2 {print $4}')"
if [[ "$avail_kb" -lt "$MIN_FREE_KB" ]]; then
  echo "ERROR: less than 5 GiB free on $(df -h "$ROOT" | awk 'NR==2 {print $1}') — packageDebug often fails when disk is full." >&2
  echo "Free some space, then retry. Current free: $(df -h "$ROOT" | awk 'NR==2 {print $4}')" >&2
  exit 1
fi

if [[ "$BUMP" -eq 1 ]]; then
  "$ROOT/scripts/bump-android-version.sh"
fi

version_name="$(grep -E '^versionName=' "$ROOT/android/version.properties" | cut -d= -f2)"
version_code="$(grep -E '^versionCode=' "$ROOT/android/version.properties" | cut -d= -f2)"
echo "Building Mycelium Android ${version_name} (${version_code})…"

if [[ "$MODE" == "full" ]]; then
  bash "$ROOT/android/build.sh"
else
  if bash "$ROOT/scripts/android-ffi-stale.sh"; then
    echo "Android native libs missing or out of date — running full Rust + UniFFI build." >&2
    bash "$ROOT/android/build.sh"
  else
    "$ROOT/scripts/prune-android-native-libs.sh"
    "$ROOT/scripts/regenerate-uniffi-kotlin.sh"
    export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || true)}"
    cd "$ROOT/android"
    if ! ./gradlew --no-daemon --max-workers=1 \
      -Dorg.gradle.parallel=false \
      -Dorg.gradle.jvmargs='-Xmx1024m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' \
      :app:assembleDebug --console=plain; then
      echo "" >&2
      echo "assembleDebug failed. If you saw :app:packageDebug fail, free disk space and run:" >&2
      echo "  cd android && ./gradlew clean :app:assembleDebug" >&2
      exit 1
    fi
  fi
fi

APK="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK" ]]; then
  echo "APK not found at $APK" >&2
  echo "The build did not finish — do not install an older APK from a previous run." >&2
  exit 1
fi

echo ""
echo "APK: $APK"
echo "Install: ./scripts/install-android-apk.sh"
if command -v unzip >/dev/null 2>&1; then
  echo "Native libs:"
  unzip -l "$APK" | grep -E 'lib/.*/lib(jnidispatch|uniffi_mycelium)\.so' || true
fi
