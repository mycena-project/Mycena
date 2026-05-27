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

if [[ "$BUMP" -eq 1 ]]; then
  "$ROOT/scripts/bump-android-version.sh"
fi

version_name="$(grep -E '^versionName=' "$ROOT/android/version.properties" | cut -d= -f2)"
version_code="$(grep -E '^versionCode=' "$ROOT/android/version.properties" | cut -d= -f2)"
echo "Building Mycelium Android ${version_name} (${version_code})…"

if [[ "$MODE" == "full" ]]; then
  bash "$ROOT/android/build.sh"
else
  if [[ ! -d "$ROOT/android/rust-build" ]] || [[ -z "$(find "$ROOT/android/rust-build" -name 'libmycelium_ffi.so' 2>/dev/null | head -1)" ]]; then
    echo "No Rust libs in android/rust-build — running full build (use --full explicitly next time)." >&2
    bash "$ROOT/android/build.sh"
  else
    export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || true)}"
    cd "$ROOT/android"
    ./gradlew --no-daemon --max-workers=1 \
      -Dorg.gradle.parallel=false \
      -Dorg.gradle.jvmargs='-Xmx1024m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' \
      :app:assembleDebug --console=plain
  fi
fi

APK="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
if [[ -f "$APK" ]]; then
  echo ""
  echo "APK: $APK"
  echo "Install: ./scripts/install-android-apk.sh"
  if command -v unzip >/dev/null 2>&1; then
    echo "Native libs:"
    unzip -l "$APK" | grep 'lib/.*/lib\(jnidispatch\|mycelium_ffi\)\.so' || true
  fi
else
  echo "APK not found at $APK" >&2
  exit 1
fi
