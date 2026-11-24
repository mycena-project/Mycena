#!/usr/bin/env bash
set -euo pipefail

if [[ ! -f /src/android/build.sh ]]; then
  echo "ERROR: mount the Mycena repo at /src" >&2
  exit 1
fi

# Deterministic build metadata (reproducible-build recipe).
export SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-1700000000}"
export RUSTFLAGS="${RUSTFLAGS:--C debuginfo=0}"
export CARGO_INCREMENTAL=0
export CARGO_NET_GIT_FETCH_WITH_CLI=true
export GRADLE_OPTS="${GRADLE_OPTS:--Dorg.gradle.daemon=false -Dorg.gradle.caching=false}"
export MYCENA_REPRODUCIBLE_BUILD=1
export MYCENA_REPRO_INSIDE=1

mkdir -p /src/android
echo "sdk.dir=${ANDROID_SDK_ROOT}" > /src/android/local.properties
chmod +x /src/android/gradlew /src/android/build.sh 2>/dev/null || true

cd /src
exec "$@"
