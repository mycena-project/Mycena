# Technical Beta APK artifact

**This artifact is for technical-beta / offline testing and is not the Play Store artifact.**
It is **not** official, **not** tagged, and **not** published.

Canonical build: `./scripts/build-technical-beta-apk.sh`
Local output (gitignored): `artifacts/technical-beta/`
Phone-to-phone: [OFFLINE_DISTRIBUTION.md](./OFFLINE_DISTRIBUTION.md)
G9 notes: [RELEASE_CANDIDATE.md](./RELEASE_CANDIDATE.md)

---

## Three artifacts (do not mix)

| Kind | Build | Signing | Share / freeze hardware |
|------|-------|---------|-------------------------|
| **Development APK** | `./android/build.sh` → `assembleDebug` | Android debug keystore | **Refused** by phone-to-phone |
| **Technical Beta APK** | `./scripts/build-technical-beta-apk.sh` → `assembleRelease` | Local Technical Beta JKS (`CN=Mycena Technical Beta`, gitignored) | **Required** |
| **Play artifact** | `.github/workflows/android-release-play.yml` → `bundleRelease` AAB | Play upload key (repo secrets) | **Out of freeze.** Split installs **refused** |

GitHub `scripts/reproducible-build.sh` still copies `app-debug.apk` (Development). That is **not** this Technical Beta APK.

---

## Requirements (fail-closed)

The script exits 1 if:

- the APK file is missing;
- the path or name looks like a **debug** APK;
- `apksigner verify` fails or there is **no signer** (unsigned);
- the signer DN is `CN=Android Debug`;
- `aapt dump badging` shows `application-debuggable`;
- versionName / versionCode do not match `android/version.properties`;
- native libs for `armeabi-v7a`, `arm64-v8a`, or `x86_64` are missing.

Gradle `packageRelease` also fails if `android.injected.signing.store.file` is absent.

---

## Clean checkout

UniFFI Kotlin (`android/uniffi-bindings/src/main/kotlin/uniffi/mycena/mycena.kt`) is **gitignored**. A clean checkout must generate it first: `./scripts/regenerate-uniffi-kotlin.sh` or `./scripts/android-test.sh`. Full APK: `./android/build.sh` then this script (or omit `--skip-native`). See [android/README.md](../android/README.md) (clean checkout).

Needs: JDK 17, Android SDK + NDK, Rust, `cargo-ndk`, `keytool`.

---

## Signing identity

| Field | Value |
|-------|--------|
| Keystore | `artifacts/technical-beta/mycena-technical-beta.jks` (gitignored; generated on first run) |
| Alias | `mycena-technical-beta` |
| DN | `CN=Mycena Technical Beta, OU=Mycena, O=Mycena Project, L=Unknown, ST=Unknown, C=DE` |
| Play / upload key | **Not used** |
| Android debug key | **Not used** |
| M-of-N official co-sign | **Not produced** |

Re-using the same JKS across rebuilds lets `adb install -r` keep data. A new JKS is a new trust root.

---

## Version

Taken from `android/version.properties` (currently `versionName=0.1.76`, `versionCode=77`). The Technical Beta script does **not** bump the version; `make android-apk` bumps it only with `--bump`.

---

## Local verification report

After a successful build the script writes `artifacts/technical-beta/VERIFICATION.txt` (gitignored) with SHA-256, BLAKE3 (`mycena-cli release hash`), `apksigner` result, and cert fingerprint.

**Hardware install:** not performed by this process. Do not treat hashes as G9/G10 pass.

Fill the table below from that file after you run the script on this machine:

| Check | Result |
|-------|--------|
| SHA-256 | `276e653ee6c128445a83b133bf98c9b3b83a306fc461859f4fbb6a9dacf11678` (this machine, 2026-08-29 post-PERS-01; see [TECHNICAL_BETA_ARTIFACT_VERIFICATION.md](./TECHNICAL_BETA_ARTIFACT_VERIFICATION.md)) |
| BLAKE3 | `1fc9246508f73fb44d698018c1d4b36264d440421e8efd033264328bf7953ac0` |
| `apksigner verify` | PASS |
| ABIs | armeabi-v7a, arm64-v8a, x86_64 |
| Install on device | **UNVERIFIED** |

---

## Phone-to-phone

`ShareApkArtifact.rejectReason` refuses debug, non-technical-beta, split, and incomplete packages. There is no fallback to the debug APK, a Play base APK, or localhost.
