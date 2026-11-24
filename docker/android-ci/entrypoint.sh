#!/usr/bin/env bash
set -euo pipefail

if [[ ! -f /src/android/build.sh ]]; then
  echo "ERROR: mount the Mycena repo at /src (e.g. -v \"\$PWD:/src\")" >&2
  exit 1
fi

mkdir -p /src/android
echo "sdk.dir=${ANDROID_SDK_ROOT}" > /src/android/local.properties
chmod +x /src/android/gradlew /src/android/build.sh 2>/dev/null || true

cd /src
exec "$@"
