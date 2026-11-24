#!/usr/bin/env bash
# Build debug APK inside minimal toolchain container (sources stay on host).
#
#   make android-apk                               # preferred entry point
#   ./scripts/docker-android-build.sh              # full Rust + Gradle in container
#   ./scripts/docker-android-build.sh --no-bump
#
# Image: mycena-android-ci (override with MYCENA_ANDROID_IMAGE).
# First time: builds image (~3–6 GiB, mostly NDK) if missing.
# Rebuild image: make docker-android-ci
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE="${MYCENA_ANDROID_IMAGE:-mycena-android-ci}"
PLATFORM="${MYCENA_DOCKER_PLATFORM:-linux/amd64}"
BUMP=1

for arg in "$@"; do
  case "$arg" in
    --no-bump) BUMP=0 ;;
    -h|--help)
      sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  echo "Image $IMAGE not found — building toolchain image (one-time)…" >&2
  docker build --platform "$PLATFORM" -t "$IMAGE" "$ROOT/docker/android-ci"
fi

if [[ "$BUMP" -eq 1 ]]; then
  "$ROOT/scripts/bump-android-version.sh"
fi

# Named volumes: keep Rust/Gradle caches off the host disk.
docker run --rm \
  --platform "$PLATFORM" \
  -v "$ROOT:/src" \
  -v mycena-android-cargo-registry:/opt/cargo/registry \
  -v mycena-android-cargo-git:/opt/cargo/git \
  -v mycena-android-target:/src/target \
  -v mycena-android-gradle:/root/.gradle \
  "$IMAGE" \
  bash -c './scripts/prune-android-native-libs.sh 2>/dev/null || true; ./android/build.sh'

APK="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
if [[ -f "$APK" ]]; then
  echo ""
  echo "APK: $APK"
  echo "Install: adb install -r \"$APK\""
else
  echo "APK not found at $APK" >&2
  exit 1
fi
