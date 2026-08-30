#!/usr/bin/env bash
# Build or verify the Mycelium Technical Beta APK (offline / phone-to-phone).
#
# This is NOT the Play Store artifact. This is NOT an official M-of-N release.
# Do not publish or tag from this script.
#
#   ./scripts/build-technical-beta-apk.sh              # native + assembleRelease + verify
#   ./scripts/build-technical-beta-apk.sh --skip-native  # Gradle only (UniFFI + .so already present)
#   ./scripts/build-technical-beta-apk.sh --verify-only [APK]
#
# Fail-closed: missing APK, debug path, unsigned, debuggable, missing ABI → exit 1.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/artifacts/technical-beta"
KEYSTORE="$OUT_DIR/mycelium-technical-beta.jks"
KEY_ALIAS="mycelium-technical-beta"
# Local tester key only — not a production/Play secret. Gitignored with artifacts/.
STORE_PASS="${MYCELIUM_TECHNICAL_BETA_STORE_PASS:-mycelium-technical-beta-local}"
DNAME="CN=Mycelium Technical Beta, OU=Mycelium, O=the maintainer, L=Unknown, ST=Unknown, C=DE"
REQUIRED_ABIS=(armeabi-v7a arm64-v8a x86_64)

SKIP_NATIVE=0
VERIFY_ONLY=0
VERIFY_APK=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-native) SKIP_NATIVE=1; shift ;;
    --verify-only)
      VERIFY_ONLY=1
      shift
      if [[ $# -gt 0 && "$1" != --* ]]; then
        VERIFY_APK="$1"
        shift
      fi
      ;;
    -h|--help)
      sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
done

die() { echo "FAIL: $*" >&2; exit 1; }

version_name="$(grep -E '^versionName=' "$ROOT/android/version.properties" | cut -d= -f2)"
version_code="$(grep -E '^versionCode=' "$ROOT/android/version.properties" | cut -d= -f2)"
[[ -n "$version_name" && -n "$version_code" ]] || die "android/version.properties missing versionName/versionCode"

artifact_name="mycelium-${version_name}-technical-beta-android.apk"

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
  if command -v apksigner >/dev/null 2>&1; then
    command -v apksigner
    return 0
  fi
  return 1
}

find_aapt() {
  local root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -z "$root" && -f "$ROOT/android/local.properties" ]]; then
    root="$(grep -E '^sdk.dir=' "$ROOT/android/local.properties" | cut -d= -f2- | tr -d '\r')"
  fi
  if [[ -n "$root" ]]; then
    local latest
    latest="$(ls -1d "$root"/build-tools/*/aapt 2>/dev/null | sort -V | tail -1 || true)"
    if [[ -n "$latest" && -x "$latest" ]]; then
      echo "$latest"
      return 0
    fi
  fi
  command -v aapt 2>/dev/null || true
}

ensure_keystore() {
  mkdir -p "$OUT_DIR"
  if [[ -f "$KEYSTORE" ]]; then
    echo "Using existing Technical Beta keystore: $KEYSTORE"
    return 0
  fi
  command -v keytool >/dev/null 2>&1 || die "keytool not found (need a JDK)"
  echo "Generating local Technical Beta keystore (not Play, not official)…"
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 3650 \
    -dname "$DNAME" \
    -storepass "$STORE_PASS" \
    -keypass "$STORE_PASS"
}

verify_apk() {
  local apk="$1"
  [[ -f "$apk" ]] || die "artifact missing: $apk"
  local base
  base="$(basename "$apk")"
  case "$apk" in
    */debug/*|*/app-debug.apk)
      die "wrong build type: refusing debug APK path $apk"
      ;;
  esac
  if [[ "$base" == *debug* ]]; then
    die "wrong build type: filename looks like a debug APK ($base)"
  fi

  local apksigner
  apksigner="$(find_apksigner)" || die "apksigner not found (ANDROID_HOME / build-tools)"
  "$apksigner" verify --verbose "$apk" >/tmp/mycelium-apksigner-verify.txt 2>&1 \
    || die "apksigner verify failed (unsigned or corrupt). See /tmp/mycelium-apksigner-verify.txt"
  if grep -qi "does not verify" /tmp/mycelium-apksigner-verify.txt; then
    die "apksigner: APK does not verify"
  fi

  local certs
  certs="$("$apksigner" verify --print-certs "$apk" 2>/dev/null || true)"
  echo "$certs" | grep -q "Signer #1" || die "no signer identity (signing absent)"
  if echo "$certs" | grep -qi "CN=Android Debug"; then
    die "APK is signed with the Android debug certificate — not a Technical Beta artifact"
  fi

  local aapt
  aapt="$(find_aapt)"
  if [[ -n "$aapt" ]]; then
    local badging
    badging="$("$aapt" dump badging "$apk" 2>/dev/null || true)"
    echo "$badging" | grep -q "application-debuggable" && die "APK is application-debuggable"
    echo "$badging" | grep -q "package: name='network.mycelium.app'" \
      || die "unexpected package name"
    echo "$badging" | grep -q "versionName='${version_name}'" \
      || die "versionName mismatch (expected ${version_name})"
    echo "$badging" | grep -q "versionCode='${version_code}'" \
      || die "versionCode mismatch (expected ${version_code})"
  else
    echo "WARN: aapt not found; skipped badging checks"
  fi

  local listing
  listing="$(unzip -Z1 "$apk" 2>/dev/null || unzip -l "$apk")"
  local abi
  for abi in "${REQUIRED_ABIS[@]}"; do
    echo "$listing" | grep -q "lib/${abi}/" || die "incomplete APK: missing native ABI lib/${abi}/"
  done

  echo "VERIFY_OK $apk"
}

write_report() {
  local apk="$1"
  local sha blake3 cert_sha dn size
  sha="$(shasum -a 256 "$apk" | awk '{print $1}')"
  size="$(wc -c < "$apk" | tr -d ' ')"
  blake3=""
  if command -v cargo >/dev/null 2>&1; then
    blake3="$(cargo run -q -p mycelium-cli -- release hash "$apk" 2>/dev/null || true)"
  fi
  local apksigner
  apksigner="$(find_apksigner)"
  local certs
  certs="$("$apksigner" verify --print-certs "$apk" 2>/dev/null || true)"
  cert_sha="$(echo "$certs" | awk -F': ' '/SHA-256 digest/{print $2; exit}' | tr -d ' ')"
  dn="$(echo "$certs" | awk -F': ' '/Signer #1 certificate DN/{print $2; exit}')"

  local report="$OUT_DIR/VERIFICATION.txt"
  cat > "$report" <<EOF
Mycelium Technical Beta APK — local verification
================================================
This artifact is for technical-beta/offline testing and is not the Play Store artifact.
It is not official, not tagged, and not published.

versionName:     ${version_name}
versionCode:     ${version_code}
applicationId:   network.mycelium.app
build type:      Gradle release (minify enabled)
ABIs:            armeabi-v7a, arm64-v8a, x86_64 (universal APK)
file:            ${apk}
bytes:           ${size}
SHA-256:         ${sha}
BLAKE3:          ${blake3:-UNVERIFIED (cargo mycelium-cli release hash failed)}
apksigner:       verifies
signer DN:       ${dn:-UNVERIFIED}
cert SHA-256:    ${cert_sha:-UNVERIFIED}
keystore:        ${KEYSTORE} (gitignored; local tester key)
hardware install: NOT PERFORMED

Development APK remains: ./android/build.sh → assembleDebug
Play artifact remains:   .github/workflows/android-release-play.yml (AAB; unchanged)
EOF
  echo "$sha" > "$OUT_DIR/SHA256SUMS.txt"
  echo "$sha  $artifact_name" > "$OUT_DIR/SHA256SUMS.txt"
  echo "Wrote $report"
  echo "SHA-256 $sha"
  [[ -n "$blake3" ]] && echo "BLAKE3  $blake3"
}

if [[ "$VERIFY_ONLY" -eq 1 ]]; then
  apk="${VERIFY_APK:-$OUT_DIR/$artifact_name}"
  if [[ ! -f "$apk" && -f "$ROOT/artifacts/release-candidate/mycelium-0.1.75-rc.1-android.apk" ]]; then
    apk="$ROOT/artifacts/release-candidate/mycelium-0.1.75-rc.1-android.apk"
  fi
  verify_apk "$apk"
  mkdir -p "$OUT_DIR"
  write_report "$apk"
  exit 0
fi

ensure_keystore
[[ -f "$KEYSTORE" ]] || die "signing is absent: keystore not created"

if [[ "$SKIP_NATIVE" -eq 0 ]]; then
  MYCELIUM_SKIP_GRADLE=1 bash "$ROOT/android/build.sh"
else
  [[ -d "$ROOT/android/uniffi-bindings/src/main/kotlin/uniffi/mycelium" ]] \
    || die "UniFFI Kotlin missing; run without --skip-native on a clean checkout"
fi

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home 2>/dev/null || true)}"
cd "$ROOT/android"
./gradlew --no-daemon :app:assembleRelease \
  -Pandroid.injected.signing.store.file="$KEYSTORE" \
  -Pandroid.injected.signing.store.password="$STORE_PASS" \
  -Pandroid.injected.signing.key.alias="$KEY_ALIAS" \
  -Pandroid.injected.signing.key.password="$STORE_PASS"

SRC="$ROOT/android/app/build/outputs/apk/release/app-release.apk"
[[ -f "$SRC" ]] || die "artifact missing after assembleRelease: $SRC"
case "$SRC" in
  */debug/*) die "wrong build type: Gradle wrote a debug path" ;;
esac

mkdir -p "$OUT_DIR"
cp "$SRC" "$OUT_DIR/$artifact_name"
verify_apk "$OUT_DIR/$artifact_name"
write_report "$OUT_DIR/$artifact_name"

echo ""
echo "Technical Beta APK (not Play, not official):"
echo "  $OUT_DIR/$artifact_name"
echo "Install (hardware later): adb install -r \"$OUT_DIR/$artifact_name\""
