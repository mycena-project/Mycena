#!/usr/bin/env bash
# Build a debug APK (optionally bump version, optionally full Rust+Gradle via build.sh).
#
# Usage:
#   ./scripts/build-android-apk.sh           # bump version + assembleDebug
#   ./scripts/build-android-apk.sh --no-bump
#   ./scripts/build-android-apk.sh --full    # Rust NDK + UniFFI + Gradle (heavy)
#   ./scripts/build-android-apk.sh --gradle-only  # skip bump, Gradle only
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUMP=1
MODE="gradle"
MIN_FREE_KB=$((5 * 1024 * 1024)) # 5 GiB

for arg in "$@"; do
  case "$arg" in
    --no-bump) BUMP=0 ;;
    --full) MODE="full" ;;
    --gradle-only) BUMP=0; MODE="gradle" ;;
    -h|--help)
      sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

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

has_rust_libs() {
  find "$ROOT/android/rust-build" -name 'libuniffi_mycelium.so' 2>/dev/null | grep -q .
}

if [[ "$MODE" == "full" ]]; then
  bash "$ROOT/android/build.sh"
else
  if ! has_rust_libs; then
    echo "No libuniffi_mycelium.so in android/rust-build — running full build." >&2
    bash "$ROOT/android/build.sh"
  else
    "$ROOT/scripts/prune-android-native-libs.sh"
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
if [[ -f "$APK" ]]; then
  echo ""
  echo "APK: $APK"
  echo "Install: ./scripts/install-android-apk.sh"
  if command -v unzip >/dev/null 2>&1; then
    echo "Native libs:"
    unzip -l "$APK" | grep -E 'lib/.*/lib(jnidispatch|uniffi_mycelium)\.so' || true
  fi
else
  echo "APK not found at $APK" >&2
  exit 1
fi
