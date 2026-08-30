# Technical Beta GitHub Release — architecture design

**Date:** 2026-08-30
**Type:** Release-engineering design. **Not** an implementation. **Not** a gate pass.
**Does not replace:** [RELEASE_FREEZE_PLAN.md](./RELEASE_FREEZE_PLAN.md), [RELEASE_FREEZE_BACKLOG.md](./RELEASE_FREEZE_BACKLOG.md), [P0_IMPLEMENTATION_QUEUE.md](./P0_IMPLEMENTATION_QUEUE.md), [RELEASING.md](../RELEASING.md), [RELEASE_INTEGRITY.md](./RELEASE_INTEGRITY.md), [TECHNICAL_BETA_ARTIFACT.md](./TECHNICAL_BETA_ARTIFACT.md).

## Official status (unchanged by this file)

| Item | Status |
|------|--------|
| Feature freeze | **ACTIVE** |
| Release freeze | **NOT APPROVED** |
| Public release | **NO-GO** |
| Hardware validation | **pending** (matrix unrun) |
| G9 | **FAIL** |
| G10 | **NOT APPROVED** |

This document does **not** approve a tag, a GitHub Release, or public-release readiness.

---

## 1. Current state

Three Android artifacts exist as **product kinds**. Only one of them is the freeze / phone-to-phone binary.

| Kind | How it is produced today | Signing | Where it lands | Phone-to-phone |
|------|--------------------------|---------|----------------|----------------|
| **Development APK** | `make android-apk` / `./android/build.sh` / CI `android-ci.yml` / `android-debug-apk.yml` / `scripts/reproducible-build.sh` | Android **debug** keystore | Local `app-debug.apk`; CI artifact `mycelium-debug-apk`; tag workflow copies it to `mycelium-${TAG}-android.apk` | **Refused** (`ShareApkArtifact`) |
| **Technical Beta APK** | `make android-technical-beta` → `./scripts/build-technical-beta-apk.sh` | Local Technical Beta JKS (`CN=Mycelium Technical Beta`) | gitignored `artifacts/technical-beta/` | **Required** |
| **Play AAB** | `android-release-play.yml` `bundleRelease` | Play upload key (repo secrets) | Play Console; CI artifacts `release-aab` | **Refused** (split install) |

Facts from the tree:

- There is **no** GitHub-hosted Technical Beta `assembleRelease` APK.
- There is **no** published `v*.*.*` tag on this freeze line ([RELEASE_CANDIDATE.md](./RELEASE_CANDIDATE.md), RFB-REL-02 **OPEN**).
- [`.github/workflows/release.yml`](../.github/workflows/release.yml) **does** create a GitHub Release on tag `v*.*.*`, via `softprops/action-gh-release@v3`. That path is unused until a tag is pushed. The Android file it would attach is a **renamed debug APK**.
- [`.github/workflows/android-ci.yml`](../.github/workflows/android-ci.yml) and [`android-debug-apk.yml`](../.github/workflows/android-debug-apk.yml) upload **workflow artifacts only**. They do not create GitHub Releases.
- [`android-release-play.yml`](../.github/workflows/android-release-play.yml) uploads an AAB to Play. It does not create a GitHub Release.
- [`desktop-release.yml`](../.github/workflows/desktop-release.yml) uploads desktop bundles as artifacts. It does not create a GitHub Release.
- Hardware testers are instructed to build locally ([HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md) §2.1). `make android-install` installs the **debug** APK.

P0 implementation-entry work is closed. Hardware validation does **not** wait on this design. Tagging is **out** of [P0_IMPLEMENTATION_QUEUE.md](./P0_IMPLEMENTATION_QUEUE.md).

---

## 2. Desired state

After **G10 approval** (not now), testers should use:

```text
Git tag vX.Y.Z
   ↓
GitHub Actions (single GitHub Release publisher)
   ↓
Technical Beta assembleRelease APK (universal, not debug, not Play)
   ↓
GitHub Release assets + checksums + cert fingerprint
   ↓
make android-install-latest-release
   ↓
verify SHA-256 (and cert if practical)
   ↓
adb (one device, or explicit selector)
   ↓
Android phone
```

Until G10, the same pipeline may be **implemented and dry-run** (workflow on a private tag, unpublished draft) but **must not** be used to declare G9/G10 pass or public release.

Local hardware work stays:

```text
make android-technical-beta
   ↓
adb install -r artifacts/technical-beta/mycelium-*-technical-beta-android.apk
```

Those two paths are different artifacts (see §8).

---

## 3. Signing model

### 3.1 How the local script signs today

[`scripts/build-technical-beta-apk.sh`](../scripts/build-technical-beta-apk.sh):

| Item | Value |
|------|--------|
| Keystore path | `artifacts/technical-beta/mycelium-technical-beta.jks` |
| Alias | `mycelium-technical-beta` |
| DN | `CN=Mycelium Technical Beta, OU=Mycelium, O=the maintainer, L=Unknown, ST=Unknown, C=DE` |
| Password | `MYCELIUM_TECHNICAL_BETA_STORE_PASS` if set; otherwise a **local default** in the script |
| Gradle | `-Pandroid.injected.signing.store.file=…` (store + key passwords, alias) |
| Fail-closed | `packageRelease` depends on `assertTechnicalBetaSigning`; unsigned release APKs are not produced |

`ensure_keystore`:

- If the JKS **exists**, it is reused (same trust root → `adb install -r` can keep app data).
- If it **does not exist**, `keytool -genkeypair` creates one (RSA 2048, 3650 days).

Gradle: [android/app/build.gradle.kts](../android/app/build.gradle.kts) `release` build type sets `BuildConfig.TECHNICAL_BETA=true` and uses the injected signing config when present.

### 3.2 Where the JKS lives

| Question | Answer |
|----------|--------|
| In git? | **No.** `.gitignore` has `artifacts/` and `*.jks`. |
| Generated or supplied? | **Generated on first local run**; thereafter **supplied by the file on disk**. |
| Same key as Play? | **No.** Play uses `ANDROID_KEYSTORE_*` secrets and `bundleRelease`. |
| Same key as Android debug? | **No.** Script refuses `CN=Android Debug`. |
| Recorded cert SHA-256 (local APK) | `536d0992d3f13825b5fdeaf8ef3a7193a1d8045fd3754218a76f609eba6eeb9e` ([TECHNICAL_BETA_ARTIFACT_VERIFICATION.md](./TECHNICAL_BETA_ARTIFACT_VERIFICATION.md)) — **public fingerprint only**, not the private key |

This document does not contain keystore passwords or private key material.

### 3.3 Can CI reproduce the same signing identity?

**Only if the existing JKS is provided to CI as a secret.** Generating a new keystore in the workflow would produce a **new certificate**. That would:

- break `adb install -r` from a previous Technical Beta install (different trust root);
- break phone-to-phone “same app” updates across GitHub vs local builds;
- make the published cert fingerprint diverge from the hardware-test APK already hashed locally.

CI **must not** call `keytool -genkeypair` on every run.

### 3.4 Local-only vs GitHub secret

| Option | Meaning | When to use |
|--------|---------|-------------|
| **Keep local-only** | Hardware testers build with `make android-technical-beta`. No GitHub Technical Beta APK. | **Now** (hardware validation). Matches the P0 queue. |
| **Promote one JKS to GitHub secrets** | Base64 JKS + store/key passwords + alias, analogous to Play’s `ANDROID_KEYSTORE_*` but a **distinct** Technical Beta key. Publish **cert SHA-256** in the Release. | **Before the first public Technical Beta GitHub Release** (G9 artifact). |
| **Mint a new CI-only Technical Beta key** | New trust root; document that GitHub APKs cannot `-r` over the current local JKS APK. | Only if the local JKS is treated as disposable (dirty-tree tester key). Worse for testers who already installed the local APK. |

**Recommendation:** For a future public Technical Beta Release, use **one dedicated Technical Beta keystore stored as GitHub secrets**, not the Play upload key, not the Android debug key, not per-job generation.

- If hardware testers must `adb install -r` from local APK onto the published APK (or vice versa), **export the existing local JKS into secrets** (password rotated at import if the script default was used).
- If G9 is allowed to be a **new** trust root, mint once, store in secrets, publish the new cert SHA-256, and tell testers to uninstall first.

The script’s **default local password** is a developer convenience. CI must use secrets only. Do not log passwords. Do not commit the JKS.

### 3.5 What signing is *not*

- A SHA-256 of the APK proves **bit identity** of that file. It does not prove the bytes are benign.
- A certificate fingerprint proves **which key signed** the APK. It does not prove the key holder is honest.
- [RELEASE_INTEGRITY.md](./RELEASE_INTEGRITY.md) **M-of-N official** co-sign is a **different** product label. A Technical Beta GitHub Release is **not** official under that document unless ≥3 of 5 signers attest the same hash **and** docs say so. Today M-of-N is **not** produced for Technical Beta.

---

## 4. Workflow recommendation

**Choice: C** — minimal reorganization of the existing tag workflow; do **not** add a second GitHub Release publisher; do **not** leave debug APKs on the public Release page.

### 4.1 Options

| | Action | Tradeoff |
|--|--------|----------|
| **A** | Change `release.yml` Android job in place to Technical Beta `assembleRelease` and keep everything else | Matches the desired tester pipeline. Risk: silently redefines what [RELEASING.md](../RELEASING.md) and [RELEASE_INTEGRITY.md](./RELEASE_INTEGRITY.md) call the “reproducible” APK (today: **debug** via `reproducible-build.sh`). Easy to mix “official M-of-N” copy with a tester-key APK. |
| **B** | Leave `release.yml` as reproducible-debug Releases; add a new Technical Beta workflow | Clean code split. Risk: **two** tag-triggered publishers, or testers downloading the **debug** asset from the default GitHub Release. `make android-install-latest-release` would have to guess which Release/asset is correct. |
| **C** (recommended) | One GitHub Release on `v*.*.*`. Android **Release asset** = Technical Beta `assembleRelease` only. Debug stays as **Actions artifacts** (`android-ci.yml` / `android-debug-apk.yml`). Play stays `android-release-play.yml`. `reproducible-build.sh` remains a **documented later M-of-N / recipe** path, not the sideload Release APK. | One place testers look. Harder to install the wrong APK. Requires honest RELEASING.md edits when implemented. Does not implement Play or official M-of-N in this workflow. |

### 4.2 What C means in files (not implemented here)

When implementation is allowed:

1. **`release.yml` Android job:** stop calling `reproducible-build.sh` for the Release APK. Build Technical Beta (`assembleRelease` + injected Technical Beta secrets). Fail closed if secrets missing, if `CN=Android Debug`, if filename contains `debug`, if ABIs missing, if `application-debuggable`.
2. **Release notes / asset names:** `mycelium-${versionName}-technical-beta-android.apk` (never `app-debug.apk`, never a renamed debug file).
3. **`android-ci.yml` / `android-debug-apk.yml`:** unchanged role — Development APK **workflow artifacts**. Not GitHub Release assets.
4. **`android-release-play.yml`:** unchanged. Never attach `.aab` to the Technical Beta GitHub Release.
5. **Do not** add a second `on: push: tags: v*.*.*` workflow that also runs `action-gh-release`.
6. **Desktop / SBOM jobs** in `release.yml` may stay; they are not APKs. Desktop remains experimental and not freeze-certified.

### 4.3 Why not A or B as stated

- **A without doc split** would keep calling the GitHub APK “reproducible debug-signed sideload” (current `release.yml` body) while shipping a different key — that is the misuse mode this design exists to prevent.
- **B** makes `gh release list` / “latest release” ambiguous unless every Technical Beta Release is a pre-release with a unique title **and** `android-install-latest-release` ignores non-matching assets. That is more moving parts than C.

---

## 5. GitHub Release artifact specification

A Technical Beta GitHub Release is a **sideload tester package**, not Play, not M-of-N official unless separately attested.

### 5.1 Required assets

| Asset | Role |
|-------|------|
| `mycelium-<versionName>-technical-beta-android.apk` | Universal `assembleRelease` APK (`armeabi-v7a`, `arm64-v8a`, `x86_64`). Filename **must** contain `technical-beta` and **must not** contain `debug`. |
| `SHA256SUMS.txt` | SHA-256 of **every** attached file (existing `release.yml` pattern). |
| `TECHNICAL_BETA_CERT_SHA256.txt` (or a line in a small `android-signing.txt`) | Signer certificate SHA-256 (Android signing identity). |
| `VERIFICATION.txt` or equivalent | versionName, versionCode, commit SHA, `apksigner verify` result, ABI list, “not Play / not official”. |

### 5.2 Recommended assets (already supported in `release.yml`)

| Asset | Role |
|-------|------|
| `mycelium-sbom-*.cdx.json` + `sbom-manifest.json` | Dependency inventory ([DEPENDENCY_RISK.md](./DEPENDENCY_RISK.md)). Not a security certification. |
| `release-integrity-*.json` | **Optional** for Technical Beta. If attached **unsigned**, label it **not official** ([RELEASE_INTEGRITY.md](./RELEASE_INTEGRITY.md) §7). Do not imply M-of-N pass. |
| Desktop bundles | Optional; not a freeze hardware target. Must not replace the APK as the primary sideload asset. |

### 5.3 Release metadata (GitHub UI + notes)

| Field | Content |
|-------|---------|
| Tag | `vMAJOR.MINOR.PATCH` matching `android/version.properties` versionName (process decision at G9; not done). |
| Target commit | Clean tagged SHA (no dirty-tree APK as the published bytes). |
| Title | Include **Technical Beta** and **not Play**. |
| Notes | AGPL reminder (existing `release.yml` body); expect bugs; no security audit; hardware matrix pointer; **G9/G10 status at publish time**. |
| Prerelease flag | **Yes** until G10. Reduces accidental “latest stable” interpretation. |

### 5.4 Three layers testers must not collapse

| Layer | What it answers | What it does not answer |
|-------|-----------------|-------------------------|
| **Artifact metadata** | versionName / versionCode, commit SHA, ABI list, filename | Trustworthiness |
| **Cryptographic integrity** | SHA-256 / BLAKE3 match the downloaded bytes | That the bytes are free of malice; that CI was uncompromised |
| **Android signing identity** | This APK was signed by the Technical Beta cert (fingerprint) | That the operator of that key is honest; that Play reviewed it |

`make android-install-latest-release` must check integrity (SHA-256 vs `SHA256SUMS.txt`) and should check signing identity (cert SHA-256 vs published fingerprint). It must still tell the user this is an **unofficial technical beta**.

---

## 6. `make android-install-latest-release` specification

**Not implemented.** Intended behavior when later added.

### 6.1 Preconditions

- `gh` authenticated to GitHub.
- `adb` on `PATH`.
- Optional: `apksigner` / Android build-tools for cert check.
- Repo default: `the maintainer/Mycelium` (overridable).

### 6.2 Steps

1. Resolve the **latest GitHub Release** that is a Technical Beta sideload (see §6.3). Fail if none.
2. Download the APK whose name matches `*-technical-beta-android.apk` and `SHA256SUMS.txt` (and cert file if present).
3. Verify SHA-256 of the APK against `SHA256SUMS.txt`. **Fail closed** on mismatch or missing checksum line.
4. If cert metadata is present and `apksigner` is available: verify `apksigner verify` and that cert SHA-256 matches the published fingerprint. If `apksigner` is missing: warn and refuse unless `--skip-cert-check` is an **explicit** opt-in (default: require cert check when the Release published a fingerprint).
5. Refuse the file if: path/name looks like debug; `CN=Android Debug`; `.aab`; split/base APK naming; workflow artifact API (`actions/download-artifact`) instead of a Release asset.
6. `adb devices` — print the list.
7. If more than one `device` state is present and `ANDROID_SERIAL` / `DEVICE=` is unset: **exit 1**. Do not pick arbitrarily.
8. `adb -s <serial> install -r <apk>` (or without `-r` only if documented; default `-r` for same trust root).
9. Print: installed `versionName` / `versionCode` (from `dumpsys package` or `aapt`), APK SHA-256, cert SHA-256, tag, commit from notes if available.

### 6.3 What “latest appropriate” means

Do **not** use “latest GitHub Release” if that Release’s Android asset is still a debug APK (today’s `release.yml`). After C is implemented:

- Prefer the newest Release whose assets include `*-technical-beta-android.apk`.
- Ignore Releases that only have `*-android-debug.apk` / `app-debug.apk`.
- Prefer **prerelease** Technical Beta over an older non-matching “latest”.

Until C is implemented, this make target **must not ship**: it would install the wrong APK or find nothing honest to install.

### 6.4 Explicit refusals

Never silently install:

- a debug APK;
- a Play AAB;
- a split/base APK;
- an arbitrary Actions artifact (`mycelium-debug-apk`);
- a download whose SHA-256 was not checked.

---

## 7. Version-specific installation specification

Companion (also not implemented):

```text
make android-install-release RELEASE=v0.1.76
```

Same verification and `adb` rules as §6, but:

- `gh release download "$RELEASE"` (tag name);
- fail if that tag has no Technical Beta APK asset;
- do not fall back to “latest” or to Actions artifacts.

`RELEASE` is a **git tag**, not a workflow run ID.

---

## 8. Local build vs GitHub artifact

| Command | Means |
|---------|--------|
| `make android-technical-beta` | Build **this working tree** (possibly dirty). Signing = **local** JKS. Output gitignored. Correct for **hardware validation now**. |
| `make android-install-latest-release` | Install the **published** Technical Beta APK for a tag. Bytes must match the Release checksums. Correct for **public testers after G9**. |

Hardware matrix rows must record **which** of the two was used (git SHA + APK SHA-256). Mixing them without recording the cert fingerprint makes HV-16 (`install -r`) uninterpretable.

`make android-install` / `make android-install-github` remain **Development APK** paths. They must not be aliases of Technical Beta install.

---

## 9. Security / integrity considerations

- **Do not** commit the JKS. **Do not** generate a new CI key every run.
- **Do not** reuse the Play upload key for sideload APKs (different distribution, different compromise story).
- Secret-scan ([`.github/workflows/secret-scan.yml`](../.github/workflows/secret-scan.yml)) exists; RFB-SEC-04 still **UNVERIFIED** (no job URL on the freeze SHA). A Technical Beta keystore secret must stay out of logs and out of git.
- `check-dependencies.sh` / RFB-SC-01: **UNVERIFIED** on the freeze SHA. G9 wants a recorded run, not this design.
- Phone-to-phone share already refuses debug and splits. A GitHub debug APK on a Release page would still be installable via browser/`adb` — that is why C removes debug APKs from Release assets.
- Hash verification is **necessary and insufficient**. Document that in Release notes.

---

## 10. Freeze-gate impact

| Question | Answer |
|----------|--------|
| Required **before** starting hardware validation? | **No.** [P0_IMPLEMENTATION_QUEUE.md](./P0_IMPLEMENTATION_QUEUE.md) closed A–C without CI Technical Beta. Runbook uses the **local** APK. RFB-REL-02 (tag) is **not** a hardware-entry item. |
| Useful before G9? | **Yes, as implementation of the publisher**, so G9 can attach a Technical Beta APK instead of a debug APK. **Publishing** the Release is G9 itself and still needs install smoke (HV-01). |
| Required for G10? | GitHub public technical beta is **in scope** for G10 ([RELEASE_FREEZE_PLAN.md](./RELEASE_FREEZE_PLAN.md) §2.3). That is **after** hardware + G9, not instead of them. |
| Does this design change G9/G10 verdicts? | **No.** G9 remains **FAIL**. G10 remains **NOT APPROVED**. |

Freeze-allowed work later: CI/build scripts, docs honesty (RFB-DOC-03 follow-up), secrets **configuration** (not in git). Freeze-forbidden now: tagging, publishing, claiming hardware PASS, claiming official M-of-N.

---

## 11. Implementation checklist (future; not done)

Do not execute this list in the design-only task. Order is for **after** hardware validation has produced G9-relevant evidence (at least HV-01 install smoke on the intended artifact family), unless a maintainer explicitly schedules CI work in parallel **without** a public tag.

### Before the first Technical Beta GitHub Release

| Item | Current proof in repo |
|------|------------------------|
| Signing-key decision (promote local JKS vs mint once for CI) | **Not decided.** Local JKS exists only on builder machines. |
| CI implementation (C: `release.yml` Android job) | **Not done.** Still `reproducible-build.sh` → debug APK. |
| Secret configuration (Technical Beta JKS, not Play key) | **Not done.** Do not create secrets in this task. |
| Build verification in CI (`apksigner`, ABI, not debug) | Local script has it; **CI Release job does not** use that script. |
| Gitleaks / secret-scan job URL on tagged SHA | RFB-SEC-04 **UNVERIFIED**. |
| `check-dependencies.sh` recorded | RFB-SC-01 **UNVERIFIED**. |
| Hardware validation | Matrix **unrun**. HV-01 **UNVERIFIED**. |
| Clean tagged SHA | RFB-REL-02 **OPEN**. Working tree has been dirty vs `fb32b1e`. |
| Release artifact verification (`SHA256SUMS`, cert fingerprint) | Local verification file only; **not** a GitHub Release. |
| Release notes (Technical Beta, not Play, not official, AGPL) | `release.yml` body still describes **debug-signed** sideload. |
| Documentation (`RELEASING.md`, README install table, runbook) | Runbook points at **local** Technical Beta. RELEASING.md still describes debug reproducible APK. |
| `make android-install-latest-release` + `android-install-release` | **Not implemented.** |
| G9 approval | **FAIL.** |
| G10 approval | **NOT APPROVED.** |
| Public release | **NO-GO.** |

### Suggested implementation sequence (after hardware, still freeze-allowed CI)

1. Written signing decision (same JKS vs new CI key).
2. Store Technical Beta keystore as GitHub secrets (operations; not a git commit).
3. Change `release.yml` Android job per §4.2; fail closed; update Release notes text.
4. Add `scripts/install-technical-beta-release.sh` + Makefile targets; refuse debug/AAB/Actions artifacts.
5. Dry-run on a **draft** or unpublished tag if needed; **do not** push `v*.*.*` until G9 evidence exists.
6. Update [RELEASING.md](../RELEASING.md), [TECHNICAL_BETA_ARTIFACT.md](./TECHNICAL_BETA_ARTIFACT.md), hardware runbook “install from GitHub” as an **optional** path after G9.
7. Push tag only with: green CI on that SHA, recorded hashes, HV-01 on **that** APK (or documented exception), Gitleaks/deps evidence as required by G9.

---

## 12. Explicit non-goals

- Publishing any APK or GitHub Release **now**.
- Creating or rotating secrets in this task.
- Changing application code, workflows, or the Makefile **in this task**.
- Passing or rewriting G9/G10.
- Marking hardware rows PASS.
- Making Technical Beta **official** M-of-N.
- Switching Play Store on.
- Converting `android-ci.yml` from debug to release (Development APK stays debug).
- Byte-identical zip reproducibility of the Technical Beta APK (not a freeze requirement; [RELEASE_FREEZE_BACKLOG.md](./RELEASE_FREEZE_BACKLOG.md) out-of-scope list).
- Using `make android-install-github` as the Technical Beta installer (it targets `mycelium-debug-apk`).

---

## Related

- [TECHNICAL_BETA_ARTIFACT.md](./TECHNICAL_BETA_ARTIFACT.md) — local artifact contract
- [TECHNICAL_BETA_ARTIFACT_VERIFICATION.md](./TECHNICAL_BETA_ARTIFACT_VERIFICATION.md) — last local hashes (not G9)
- [HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md) — current tester path
- [OFFLINE_DISTRIBUTION.md](./OFFLINE_DISTRIBUTION.md) — keep vs wipe / keys
- [P0_IMPLEMENTATION_QUEUE.md](./P0_IMPLEMENTATION_QUEUE.md) — implementation phase ended; hardware only
