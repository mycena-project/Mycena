# Mycena release candidate

**Candidate ID:** `0.1.75-rc.1`
**Gate:** [RELEASE_FREEZE_PLAN.md](./RELEASE_FREEZE_PLAN.md) **G9**
**Date (UTC):** 2026-08-29T15:02:46Z
**Published:** **no** (no git tag, no GitHub Release, no Play upload)

Status vocabulary: `PASS` · `FAIL` · `UNVERIFIED` · `NOT APPLICABLE`.
If a requirement was not executed with evidence, it is **`UNVERIFIED`**, not PASS.

This file is **not** a freeze pass, **not** an official M-of-N release, and **not** a claim that P0 hardware or CI gates are done.

---

## 1. Identity

| Field | Value |
|-------|--------|
| **versionName** | `0.1.75` (`android/version.properties`) |
| **versionCode** | `76` |
| **Candidate label** | `0.1.75-rc.1` (documentation only; not written into the APK) |
| **HEAD commit** | `fb32b1e5c89b318e14b1adc2a76b7dcefac6b3ec` |
| **Working tree** | **Dirty.** Freeze/share/security docs and Android changes are **uncommitted**. This APK was built from that dirty tree, not from a clean tag of `fb32b1e` alone. |
| **Git tag** | **None.** Not created (do not publish). |
| **Build date (UTC)** | 2026-08-29T15:02:46Z (hash/copy time; Gradle package ~16:59 CEST) |
| **Builder** | Local machine; `assembleRelease`; NDK `26.1.10909125`; not the reproducible Docker container |
| **applicationId** | `io.github.mycena_project.app` |
| **minSdk / targetSdk / compileSdk** | 26 / 34 / 34 |

---

## 2. Artifacts

Binaries live under **`artifacts/release-candidate/`** (gitignored). They are **not** in git and were **not** uploaded.

| Artifact | Role |
|----------|------|
| `mycena-0.1.75-rc.1-android.apk` | Universal release APK (armeabi-v7a, arm64-v8a, x86_64) |
| `SHA256SUMS.txt` | SHA-256 of APK + SBOM |
| `mycena-sbom-0.1.75-rc.1.cdx.json` | CycloneDX 1.5 for `mycena-node` |
| `sbom-manifest.json` | Generator metadata |
| `mapping.txt` | R8 mapping (crash deobfuscation; keep private) |
| `output-metadata.json` | Gradle listing |
| `mycena-rc-technical-beta.jks` | **Local RC keystore — do not commit, do not publish** |

### 2.1 Hashes

| File | SHA-256 |
|------|---------|
| `mycena-0.1.75-rc.1-android.apk` | `413c35287607f71ee55458266cc1a38557764ba4376d05adedef5aa6bb1a396d` |
| `mycena-sbom-0.1.75-rc.1.cdx.json` | `ec9555989bb16f37060fca977ef1e838d8bd48a6effa6c61154ffdc05c97560c` |

| File | BLAKE3 (mycena-cli `release hash`) |
|------|--------------------------------------|
| `mycena-0.1.75-rc.1-android.apk` | `9ca27eadf110a1c418562dcfd55b5061aeaa0f2c009d43db418bc0fd7d1fd7e8` |

APK size: **96453496** bytes (~92 MiB).

### 2.2 Signing (not debug)

| Check | Result |
|-------|--------|
| Build type | Gradle **`release`** (`isMinifyEnabled = true`) |
| `android:debuggable` | Not set in packaged manifest (release default **false**) |
| `apksigner verify` | **Verifies** (APK Signature Scheme **v2**) |
| Signer DN | `CN=Mycena Technical Beta RC, OU=Mycena, O=Mycena Project, L=Unknown, ST=Unknown, C=DE` |
| Cert SHA-256 | `fd5bf4aa5040d9b7d56c3ff0e53385c0145044045a87ee8a9ac66c71b5a070d2` |
| Android debug keystore | **Not used** |
| Play App Signing / upload key | **Not used** |
| M-of-N release integrity co-sign | **Not produced** (unsigned `release init` / `release-verify.sh` **UNVERIFIED**) |

This key is a **local technical-beta key** generated 2026-08-29 for this candidate. It is **not** a production or Play key. Installs signed with it will **not** upgrade over a debug-signed or Play-signed install of the same `applicationId`.

---

## 3. Supported platforms / devices

| Surface | Claim |
|---------|--------|
| **Intended** | Android API 26–34, ABIs `armeabi-v7a` / `arm64-v8a` / `x86_64` (native `libuniffi_mycena.so` present for all three) |
| **Hardware matrix** | [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md) — **all rows `unrun`** |
| **Samsung** | **UNVERIFIED** (G4 option b still required in public copy until a named device exists) |
| **Desktop Tauri** | **NOT APPLICABLE** for this candidate (Android APK only) |
| **iOS** | Out of freeze scope |

**Supported = listed without exception** in the freeze plan. **No device is supported** for this RC because none are recorded as tested.

---

## 4. Verified functionality

These were verified **in this session** with command output (not on a phone):

| Item | Evidence |
|------|----------|
| `assembleRelease` succeeds | Gradle `BUILD SUCCESSFUL` |
| APK contains three JNI ABIs | `unzip -l` `lib/*/libuniffi_mycena.so` |
| Release signing, not debug | `apksigner verify --print-certs` |
| Package identity | `aapt dump badging`: `io.github.mycena_project.app` `0.1.75` / `76` |
| SHA-256 / BLAKE3 recorded | this file |
| CycloneDX SBOM generated | `cargo cyclonedx` via `scripts/generate-sbom.sh 0.1.75-rc.1` |
| Advisory allowlist dates (earlier G7) | `check-advisory-allowlist.sh` OK as of 2026-08-29 — **not re-run on this APK** |
| Selected Rust security unit tests (G7) | SD-030 / SD-081 / sideload — **on the dirty tree earlier**, not re-run against this APK |

Build-only fixes required for this APK (not hardware):

- `MeshEventBus.kt`: `callbackFlow` used `this` as `Context`; release compile failed. Fixed to `this@meshEvents`.
- R8: Tink / errorprone `-dontwarn` added to `proguard-rules.pro`.

---

## 5. Unverified functionality

Mark **`UNVERIFIED`** — not PASS:

| Area | Status | Why |
|------|--------|-----|
| Install / launch on any phone | **UNVERIFIED** | `adb devices` empty |
| Onboarding, mesh start, chat | **UNVERIFIED** | No device |
| Phone-to-phone share/install | **UNVERIFIED** | Hardware matrix empty; G3 FAIL in freeze audit |
| Permissions deny paths | **UNVERIFIED** | No device |
| Panic wipe | **UNVERIFIED** | No device |
| Update / reinstall (`adb install -r`) | **UNVERIFIED** | No device |
| BLE / LAN / Samsung idle | **UNVERIFIED** | Matrix empty |
| R8 minify runtime (chat, UniFFI, WebView) | **UNVERIFIED** | Release+R8 never smoke-tested on device |
| Docker `reproducible-build.sh` for **this** APK | **UNVERIFIED** | That script still copies **`assembleDebug`** |
| Independent rebuild / bit-identical APK | **UNVERIFIED** | Local Gradle, not pinned container |
| `./scripts/check-dependencies.sh` (full deny+audit) | **UNVERIFIED** | Not run this session |
| `secret-scan.yml` on freeze SHA | **UNVERIFIED** | No job URL (RFB-SEC-04) |
| CI `ci.yml` / `android-ci.yml` / `fuzz-pr.yml` on this tree | **UNVERIFIED** | No job URLs (RFB-TEST-01) |
| `release-verify.sh` M-of-N | **UNVERIFIED** | No official manifest / co-signers |
| Desktop bundles | **UNVERIFIED** | Not built |
| GitHub private vulnerability reporting enabled | **UNVERIFIED** | Settings not checked |

---

## 6. Known limitations

See [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md), [BETA_SECURITY_ASSESSMENT.md](./BETA_SECURITY_ASSESSMENT.md), [THREAT_MODEL.md](./THREAT_MODEL.md) §2.1.

In particular for this candidate:

- Metadata is not confidential; relay sees IPs.
- MeshCoin is preview utility, not money.
- No Tor-class / whistleblower / forensic claims.
- Sideload APK trust is the **signer cert + hash**, not Play review.
- Group PSK, WebView TCB, unsigned residuals R1–R10 as in [ACCEPTED_SECURITY_RESIDUALS.md](./ACCEPTED_SECURITY_RESIDUALS.md) (GitHub-only unsigned freeze disposition 2026-08-29).

---

## 7. Known issues (this RC)

| ID | Issue | Severity |
|----|--------|----------|
| Dirty tree | APK ≠ clean `fb32b1e` | Process / integrity |
| Open P0 backlog | Hardware, CI URLs, secret-scan, G3/G2 device proof still not `done` | **Release-blocking for G9/G10** |
| CI still debug | `android/build.sh` / `reproducible-build.sh` still `assembleDebug` | Two artifact types; this RC is the **release-signed** one |
| Debug vs this key | Cannot update over debug-signed installs | Installer / testers |
| R8 | First release minify; runtime **UNVERIFIED** | Stability |
| No second ABI `x86` 32-bit | Only v7a / arm64 / x86_64 | Device coverage |
| `libjnidispatch.so` not stripped | Gradle warning | Size / hygiene |

The freeze backlog is **not** substantially complete. P0 items still `open` or `in_progress` include (non-exhaustive): RFB-DOC-01, RFB-DOC-03, RFB-P2P-01– (device remaining), RFB-APK-01/02/03, RFB-NET-01/02, RFB-PERS-01/02, RFB-TEST-01, RFB-HW-01, RFB-SEC-04, RFB-REL-01/02/03.

---

## 8. Security review status

| Item | Status |
|------|--------|
| Internal G7 review | [SECURITY_RELEASE_REVIEW.md](./SECURITY_RELEASE_REVIEW.md) dated 2026-08-29. **Not** a certification. |
| External audit | **Not commissioned.** Explicitly deferred for GitHub-only beta. |
| Residuals R1–R10 | Unsigned; GitHub-only freeze-accepted 2026-08-29 |
| Secret scan on this SHA | **UNVERIFIED** |
| Independent review of P0 crypto/network mitigations | **None** |

---

## 9. Test status

| Suite | Status |
|-------|--------|
| Hardware HV-01–20 | **UNVERIFIED** (`unrun`) |
| `./gradlew testDebugUnitTest` | **UNVERIFIED** this session |
| `./gradlew assembleRelease` | Built (compile PASS) |
| Rust SD-030 / SD-081 / miniapp sideload | Run in G7 session on dirty tree; **not** re-run as RC gate |
| Instrumentation | Thin; **UNVERIFIED** on device |
| CI job URLs | **UNVERIFIED** |

---

## 10. Installation instructions (not performed)

**Do not publish.** Local sideload only.

```bash
# Technical Beta APK (not Play, not official) — preferred for testers
./scripts/build-technical-beta-apk.sh
adb install -r artifacts/technical-beta/mycena-0.1.75-technical-beta-android.apk
```

A prior local candidate also exists at `artifacts/release-candidate/` if that directory is still on the machine; prefer the script output above for new installs.

Or copy the APK to a phone and use the system installer (unknown apps / Play Protect / Samsung Auto Blocker as in [OFFLINE_DISTRIBUTION.md](./OFFLINE_DISTRIBUTION.md)).

Verify hash before install:

```bash
shasum -a 256 artifacts/release-candidate/mycena-0.1.75-rc.1-android.apk
# expect 413c35287607f71ee55458266cc1a38557764ba4376d05adedef5aa6bb1a396d
```

This APK is the **offline distribution artifact** (complete universal package, not a Play split). Phone-to-phone HTTP share of **this** build is **UNVERIFIED**.

---

## 11. Reproducibility / SBOM

| Requirement | Status |
|-------------|--------|
| Pinned Docker reproducible recipe for **this** APK | **UNVERIFIED** (script still debug) |
| CycloneDX SBOM (`mycena-node`) | Generated (Rust graph only; **not** a full Gradle/npm SBOM) |
| License/advisory `check-dependencies.sh` | **UNVERIFIED** |
| Allowlist `review_by` | 2026-09-30 (must not expire without review) |

---

## 12. Release-candidate gate report (G9)

**Verdict: FAIL (candidate artifact exists; freeze G9 does not pass).**

| G9 requirement | Status | Notes |
|----------------|--------|--------|
| Version recorded | PASS | `0.1.75` / `76`; label `0.1.75-rc.1` |
| Commit recorded | PASS as HEAD; **FAIL** as unique tree | Dirty working tree |
| Build date recorded | PASS | UTC above |
| APK produced | PASS | release, not debug-signed |
| Artifact hashes | PASS | SHA-256 + BLAKE3 |
| Signing | PASS for local RC key | Not Play, not M-of-N official |
| Release configuration (`minify`) | PASS compile | Runtime **UNVERIFIED** |
| Complete APK (all freeze ABIs) | PASS | three `.so` |
| SBOM | PASS generate | Scope limited to `mycena-node` |
| Install smoke on matrix device | **UNVERIFIED** → G9 fail | No device |
| RELEASING.md steps 1–5 | **FAIL / skipped** | CI not evidenced; **no tag**; **not published** (requested) |
| CHANGELOG | **NOT APPLICABLE** | RELEASING no longer requires root CHANGELOG (RFB-DOC-04) |
| Integrity story honesty | PASS | Debug CI path still exists; this file states the split |
| No silent unresolved P0 | PASS **documentation** | P0s listed; **not** resolved |

### Plan §4.1 P0 freeze checkboxes (this RC)

| P0 | Status |
|----|--------|
| No known reproducible P0 crash on device | **UNVERIFIED** |
| Phone-to-phone on two phones | **UNVERIFIED** |
| Permissions requested; deny non-fatal | Code `in_progress`; device **UNVERIFIED** |
| Release artifact installs on supported devices | **UNVERIFIED** (no supported device) |
| Critical security unaddressed | Preview: no new Critical in G7; secret-scan **UNVERIFIED** |
| Secrets out of tree / scan on SHA | Tracked tree previously clean; Gitleaks **UNVERIFIED** |
| Build docs match CI | **FAIL** — CI debug vs this release APK |
| Limitations not contradicted | Docs work ongoing (RFB-DOC-01 still `open`) |
| Panic wipe + mesh-copies copy | Device **UNVERIFIED**; PERS-01 still `open` |
| SD-081 / SD-030 / SD-171 | Implemented; 171 not test-run; device **UNVERIFIED** |
| SECURITY.md reporting path | PASS (email) |

**G9 pass criteria** (“artifact installs; hash recorded; source tag/commit recorded”): hashes yes; **install UNVERIFIED**; **tag none**. **G9 = FAIL.**

**G10:** **not approved.** Do not declare release freeze, hardware certification, or that the public release gate passed. The allowed public label is **Public Technical Beta — Feature Development Frozen, Release Validation Ongoing** ([DEVELOPMENT_FREEZE.md](./DEVELOPMENT_FREEZE.md)).

**RFB-REL-01 decision:** freeze / phone-to-phone uses a **Technical Beta** universal `assembleRelease` APK with a **local tester key** (`./scripts/build-technical-beta-apk.sh`). That artifact is for technical-beta/offline testing and is **not** the Play Store artifact. `./android/build.sh` remains the **Development** debug APK. GitHub `reproducible-build.sh` still copies debug. **Do not mix keys.** See [TECHNICAL_BETA_ARTIFACT.md](./TECHNICAL_BETA_ARTIFACT.md).

**RFB-REL-02:** still `open` — no tag, no GitHub Release (explicit non-publish).
