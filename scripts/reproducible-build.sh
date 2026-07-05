#!/usr/bin/env bash
# Reproducible Mycelium core build (Android APK) inside pinned toolchain container.
#
#   ./scripts/reproducible-build.sh
#   ./scripts/reproducible-build.sh --tag v0.1.62
#
# Outputs:
#   target/reproducible/<artifact>.apk
#   target/reproducible/build-report.json
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE="${MYCELIUM_REPRO_IMAGE:-mycelium-reproducible-build}"
PLATFORM="${MYCELIUM_DOCKER_PLATFORM:-linux/amd64}"
TAG="${TAG:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag)
      TAG="${2:?--tag requires value}"
      shift 2
      ;;
    --tag=*)
      TAG="${1#*=}"
      shift
      ;;
    -h|--help)
      sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
done

repro_build_inside() {
  cd "$ROOT"
  export SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-1700000000}"
  export RUSTFLAGS="${RUSTFLAGS:--C debuginfo=0}"
  export CARGO_INCREMENTAL=0
  export MYCELIUM_REPRODUCIBLE_BUILD=1
  export CARGO_HOME="${CARGO_HOME:-/opt/cargo}"

  bash ./android/build.sh

  OUT_DIR="$ROOT/target/reproducible"
  mkdir -p "$OUT_DIR"
  SRC_APK="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
  [[ -f "$SRC_APK" ]] || { echo "APK missing: $SRC_APK" >&2; exit 1; }

  local artifact_tag="${TAG:-}"
  if [[ -z "$artifact_tag" ]]; then
    artifact_tag="$(git describe --tags --always 2>/dev/null || echo local)"
  fi
  local artifact="mycelium-${artifact_tag}-android.apk"
  cp "$SRC_APK" "$OUT_DIR/$artifact"

  local hash recipe
  hash="$(cargo run -q -p mycelium-cli -- release hash "$OUT_DIR/$artifact")"
  recipe="$(cargo run -q -p mycelium-cli -- release recipe-digest "$ROOT")"

  cat > "$OUT_DIR/build-report.json" <<EOF
{
  "release_tag": "$artifact_tag",
  "artifact_name": "$artifact",
  "artifact_blake3_hex": "$hash",
  "build_recipe_digest_hex": "$recipe",
  "recipe_url": "https://github.com/mycena-project/Mycelium/blob/main/docs/RELEASE_INTEGRITY.md"
}
EOF
  echo "Reproducible build: $OUT_DIR/build-report.json"
  echo "  artifact_blake3: $hash"
  echo "  recipe_digest:   $recipe"
}

if [[ "${MYCELIUM_REPRO_INSIDE:-0}" == "1" ]]; then
  repro_build_inside
  exit 0
fi

if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  echo "Building reproducible toolchain image…" >&2
  docker build --platform "$PLATFORM" -t "$IMAGE" "$ROOT/docker/reproducible-build"
fi

docker run --rm \
  --platform "$PLATFORM" \
  -e MYCELIUM_REPRO_INSIDE=1 \
  -e SOURCE_DATE_EPOCH=1700000000 \
  -e TAG="${TAG}" \
  -v "$ROOT:/src" \
  -v mycelium-repro-cargo-registry:/opt/cargo/registry \
  -v mycelium-repro-cargo-git:/opt/cargo/git \
  -v mycelium-repro-target:/src/target \
  -v mycelium-repro-gradle:/root/.gradle \
  "$IMAGE" \
  bash -lc 'cd /src && ./scripts/reproducible-build.sh'
