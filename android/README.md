# Android Build & Test Guide

This guide explains how to build and test the Android app with embedded Rust (`mycena-ffi`) and UniFFI bindings.

**Clean checkout:** generated UniFFI Kotlin is **not** in git. `./gradlew assembleDebug` or `./gradlew testDebugUnitTest` **alone will fail**. That is intentional. Follow [Clean checkout](#clean-checkout) below — do not treat a Gradle-only command as a complete workflow.

---

## Prerequisites

| For | Need |
|-----|------|
| UniFFI Kotlin + JVM unit tests | Rust (`cargo` / rustup), JDK **17**, Android SDK, checked-in `android/gradlew` |
| Development or Technical Beta **APK** | All of the above **plus** Android NDK and `cargo-ndk` (`android/build.sh` installs `cargo-ndk` if missing) |

Optional: Android Studio (latest stable).

Regenerate the Gradle wrapper with Docker only if `android/gradlew` is missing:

```bash
make gradle-wrapper
```

SDK path: Gradle reads `android/local.properties` (`sdk.dir=…`). `./android/build.sh` writes that file from `ANDROID_HOME` / `ANDROID_SDK_ROOT` when set.

---

## Clean checkout

Order matters. Bindings stay **untracked** (`.gitignore`: `android/uniffi-bindings/src/main/kotlin/uniffi/mycena/mycena.kt`). Do not commit them unless the project later decides to track generated code.

Generation uses the **existing** binder only: `scripts/regenerate-uniffi-kotlin.sh` → `cargo run -p mycena-ffi --bin uniffi-bindgen` on `crates/mycena-ffi/src/mycena.udl`. Output is deterministic for a given `Cargo.lock` / UniFFI version. It **requires host Rust**; it does **not** require the NDK.

### 1. Prepare

Install the prerequisite row above for the work you need (tests vs APK).

### 2. Generate UniFFI bindings

From the **repository root** (not `android/`):

```bash
./scripts/regenerate-uniffi-kotlin.sh
```

Expect exactly one `android/uniffi-bindings/src/main/kotlin/uniffi/mycena/mycena.kt`. If Gradle is run first, `:uniffi-bindings:requireUniffiKotlin` fails with these same commands.

### 3. Build the Android app (Development APK)

Needs NDK + `cargo-ndk`. This also regenerates UniFFI and builds native `.so` files:

```bash
./android/build.sh
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk` (debug-signed **Development** APK).

**Technical Beta APK** (release-signed, not Play): `./scripts/build-technical-beta-apk.sh` — [docs/TECHNICAL_BETA_ARTIFACT.md](../docs/TECHNICAL_BETA_ARTIFACT.md).

### 4. Run deterministic Android tests (JVM)

Does **not** need NDK. Regenerates UniFFI if you use the wrapper:

```bash
./scripts/android-test.sh
```

Equivalent after step 2:

```bash
cd android
./gradlew testDebugUnitTest
```

Instrumentation (device/emulator; not required for the freeze JVM suite):

```bash
cd android
./gradlew connectedDebugAndroidTest
```

CI (`.github/workflows/android-ci.yml`) already runs `android/build.sh` then `testDebugUnitTest` on a clean runner checkout. Do not skip generation in CI.

---

## 1d) Technical Beta APK (offline / phone-to-phone)

This artifact is for **technical-beta / offline testing** and is **not** the Play Store artifact. It is not official, not tagged, and not published.

```bash
./scripts/build-technical-beta-apk.sh
# output: artifacts/technical-beta/mycena-<versionName>-technical-beta-android.apk
```

That is Gradle **`assembleRelease`** (minify on) with an explicit local Technical Beta keystore (`artifacts/technical-beta/*.jks`, gitignored). `assembleRelease` without that keystore **fails closed** (unsigned release APKs are not produced).

Phone-to-phone share **refuses** debug APKs, Play split installs, and incomplete packages. Install this Technical Beta APK on the sharing phone first.

See [docs/OFFLINE_DISTRIBUTION.md](../docs/OFFLINE_DISTRIBUTION.md) and [docs/TECHNICAL_BETA_ARTIFACT.md](../docs/TECHNICAL_BETA_ARTIFACT.md).

**Play Store AAB** remains `.github/workflows/android-release-play.yml` (unchanged; out of this freeze path).

## 1b) Build inside Docker (no local Rust / Android SDK)

**Toolchain image only** (~3–4 GiB, mostly NDK — not the whole repo):

```bash
make docker-android-ci
```

**APK on host** (mounts repo; Rust/Gradle caches stay in Docker volumes):

```bash
./scripts/docker-android-build.sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Do **not** use `docker build -f docker/android-ci/Dockerfile .` from repo root — that old pattern copied the project into the image and inflated it to 10+ GiB. The slim Dockerfile lives in `docker/android-ci/` with context `docker/android-ci` only.

## 1c) Manual GitHub Action → Google Play

Workflow file: `.github/workflows/android-release-play.yml` (workflow dispatch only).

Configure the repository secrets listed in the header of that workflow, then run **Actions → Android Release → Google Play → Run workflow**. The job cross-compiles **arm64-v8a**, **armeabi-v7a**, and **x86_64**, builds a signed **AAB**, and uploads to the chosen Play track together with R8 **mapping.txt** and **whatsnew** (en-US).

## Manual Gradle (only after UniFFI exists)

These **fail** on a clean tree until step 2 (or `android/build.sh`) has run.

Generate Kotlin only (same as `./scripts/regenerate-uniffi-kotlin.sh`):

```bash
./scripts/regenerate-uniffi-kotlin.sh
```

Native `.so` files only (needs NDK):

```bash
cargo ndk \
  -t aarch64-linux-android \
  -t armv7-linux-androideabi \
  -t x86_64-linux-android \
  -o android/rust-build \
  build --release -p mycena-ffi
```

Then:

```bash
cd android
./gradlew assembleDebug
```

## Run on emulator/device

1. Start an emulator (API 26+), or connect a device.
2. Install app:

```bash
cd android
./gradlew installDebug
```

Then launch the app: `MeshService` starts as a foreground service and initializes the Rust node.

**Release freeze hardware validation** (Definition of Done, not this README’s 2-emulator smoke): [HARDWARE_TEST_RUNBOOK.md](../docs/HARDWARE_TEST_RUNBOOK.md). Record results in [HARDWARE_TEST_MATRIX.md](../docs/HARDWARE_TEST_MATRIX.md). Device identity / filtered logcat: `make android-hw-info` and `./scripts/android/capture-freeze-evidence.sh` ([CRASH_REPORTING.md](../docs/CRASH_REPORTING.md)). Do not commit `artifacts/hardware-runs/`.

## Basic E2E smoke test (2 emulators)

1. Start AVD A and AVD B.
2. Launch app on both.
3. In each app, open Peers screen.
4. Use QR flow or manual bootstrap add:
   - Scan QR from other device, or
   - Paste `/ip4/<ip>/tcp/7761/p2p/<peer_id>` and tap `Add`.
5. Verify peer appears in list.
6. Send chat message from A to B and verify receipt notification.

## Troubleshooting

- **`Generated UniFFI Kotlin is missing` / `requireUniffiKotlin`**: run `./scripts/regenerate-uniffi-kotlin.sh` from the repo root, or `./scripts/android-test.sh` / `./android/build.sh`.
- **`gradlew` missing**: generate wrapper in Android Studio or run `make gradle-wrapper`.
- **No `.so` loaded at runtime**: `android/rust-build` must contain ABI folders and `libuniffi_mycena.so` — use `./android/build.sh`, not Gradle-only.
- **Binding mismatch**: re-run UniFFI generation after UDL changes.
- **Camera/QR issues**: ensure camera permission granted on device.
- **Peer discovery on hotspot**: use QR/manual bootstrap multiaddr flow (`addBootstrapPeer`).
