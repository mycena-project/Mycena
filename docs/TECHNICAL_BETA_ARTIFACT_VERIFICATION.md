# Technical Beta APK — local verification report

**Date (UTC):** 2026-08-29
**Published:** no
**Tagged:** no
**Hardware install:** **NOT PERFORMED**

**This artifact is for technical-beta / offline testing and is not the Play Store artifact.**
It is **not** official.

Source of this table: `./scripts/build-technical-beta-apk.sh` (native `MYCELIUM_SKIP_GRADLE=1 ./android/build.sh` + `:app:assembleRelease`) after RFB-PERS-01 / RFB-DOC-05 / RFB-DOC-06. Reused existing local JKS.

Gitignored binary: `artifacts/technical-beta/mycelium-0.1.75-technical-beta-android.apk`

| Field | Result |
|-------|--------|
| versionName / versionCode | `0.1.75` / `76` |
| applicationId | `network.mycelium.app` |
| Gradle build type | **release** (`isMinifyEnabled = true`, `BuildConfig.TECHNICAL_BETA = true`) |
| HEAD commit | `fb32b1e5c89b318e14b1adc2a76b7dcefac6b3ec` (working tree **dirty**; APK includes uncommitted P0 implementation) |
| Size | 96458300 bytes |
| SHA-256 | `276e653ee6c128445a83b133bf98c9b3b83a306fc461859f4fbb6a9dacf11678` |
| BLAKE3 (`mycelium-cli release hash`) | `1fc9246508f73fb44d698018c1d4b36264d440421e8efd033264328bf7953ac0` |
| `apksigner verify` | **PASS** |
| Signer DN | `CN=Mycelium Technical Beta, OU=Mycelium, O=the maintainer, L=Unknown, ST=Unknown, C=DE` |
| Cert SHA-256 | `536d0992d3f13825b5fdeaf8ef3a7193a1d8045fd3754218a76f609eba6eeb9e` |
| Android debug cert | **Not used** |
| Play signing | **Not used** |
| ABIs in APK | armeabi-v7a, arm64-v8a, x86_64 (native `lib/` present) |
| Debuggable | **not** `application-debuggable` |
| Device install | **UNVERIFIED** |
| Git tag | **none** |

This cert fingerprint matches the previous Technical Beta JKS (`536d0992…`). SHA-256/BLAKE3 **differ** from the pre-PERS-01 APK (`ca6dcab4…` / `f7bfc5ef…`) because panic-wipe and docs landed in this package.

**G9:** still **FAIL** (no install smoke, no tag). **G10:** still **NOT APPROVED**. Do not treat these hashes as a release pass.

JVM tests: `./gradlew testDebugUnitTest` **BUILD SUCCESSFUL** (includes `LocalDataWiperTest` and `network.mycelium.app.bootstrap.*`).
