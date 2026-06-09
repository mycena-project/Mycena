#!/usr/bin/env bash
# Build debug APK in mingc/android-build-box (your working Docker flow).
#
#   make android-apk
#   make android-deploy
#   ./scripts/mingc-android-build.sh
#   ./scripts/mingc-android-build.sh --no-bump
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE="${MYCELIUM_ANDROID_IMAGE:-mingc/android-build-box:latest}"
PLATFORM="${MYCELIUM_DOCKER_PLATFORM:-linux/amd64}"
BUMP=1

for arg in "$@"; do
  case "$arg" in
    --no-bump) BUMP=0 ;;
    -h|--help)
      sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'
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
echo "Building Mycelium Android ${version_name} (${version_code}) in ${IMAGE}…"

docker run --rm \
  --platform "$PLATFORM" \
  -v "$ROOT:/project" \
  -v mycelium-mingc-cargo:/root/.cargo \
  -v mycelium-mingc-rustup:/root/.rustup \
  -v mycelium-mingc-target:/project/target \
  -w /project \
  "$IMAGE" \
  bash -c '
set -euo pipefail
if [[ ! -f "$HOME/.cargo/env" ]]; then
  curl --proto "=https" --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
fi
# shellcheck disable=SC1091
source "$HOME/.cargo/env"
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
if ! command -v cargo-ndk >/dev/null 2>&1; then
  cargo install cargo-ndk --version "^3" --force
fi
cargo ndk \
  --target armeabi-v7a \
  --target arm64-v8a \
  --target x86_64 \
  --output-dir android/rust-build \
  build --release -p mycelium-ffi
echo "--- clearing old bindings ---"
rm -rf android/uniffi-bindings/src/main/kotlin/uniffi/
mkdir -p android/uniffi-bindings/src/main/kotlin/uniffi/mycelium/
echo "--- regenerating kotlin bindings ---"
cargo run -p mycelium-ffi --bin uniffi-bindgen -- generate \
  crates/mycelium-ffi/src/mycelium.udl \
  --language kotlin \
  --out-dir android/uniffi-bindings/src/main/kotlin/uniffi/mycelium/
echo "--- verifying kotlin bindings ---"
find android/uniffi-bindings -name "*.kt"
./scripts/android-ffi-stamp.sh write
cd android
chmod +x gradlew
./gradlew clean assembleDebug
'

APK="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
if [[ -f "$APK" ]]; then
  echo ""
  echo "APK: $APK"
  echo "Install: make android-install   or   adb install -r \"$APK\""
else
  echo "APK not found at $APK" >&2
  exit 1
fi
