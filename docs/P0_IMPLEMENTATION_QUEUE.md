# P0 implementation queue (before hardware validation)

**Date:** 2026-08-29
**Does not replace:** [RELEASE_FREEZE_BACKLOG.md](./RELEASE_FREEZE_BACKLOG.md) (full freeze inventory).
**Implementation record:** A–C closed 2026-08-29. Hardware validation (section D) has **not** been performed.

This queue was the work that had to be **implemented** (code, build, or tester-truth docs) before **hardware validation only**.

It is **not** a G9/G10 pass list. CI URLs, git tags, public launch, Samsung idle, two-phone share, and filling [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md) remain **out of this queue**.

---

## Official status (unchanged by this file)

| Item | Status |
|------|--------|
| Feature freeze | ACTIVE |
| Release freeze | NOT APPROVED |
| Public release | NO-GO |
| Hardware validation | NOT YET PERFORMED |
| G9 | FAIL |
| G10 | NOT APPROVED |

“Implementation complete” ≠ “release verified.” Desired transition:

```text
IMPLEMENTATION PHASE          ← ended 2026-08-29
        ↓
CODE/FIXES COMPLETE
        ↓
CLEAN RELEASE ARTIFACT        ← local Technical Beta APK recorded
        ↓
HARDWARE VALIDATION ONLY      ← begins (section D)
        ↓
G9 / G10                      ← still FAIL / NOT APPROVED
```

---

## A. Code fixes

Actual implementation still required in the Android tree.

### RFB-PERS-01 — Panic UI: mesh copies remain; fail if wipe incomplete

| Field | Content |
|-------|---------|
| **ID** | RFB-PERS-01 |
| **Current state** | `DONE_TESTED` (JVM); device **UNVERIFIED** |
| **Exact problem (was)** | Confirm copy omitted mesh copies on other devices. Incomplete wipe still relaunched. |
| **Files changed** | `strings.xml` + locale overlays; `LocalDataWiper.kt`; `SettingsScreen.kt` (`PanicWipeSection`); `LocalDataWiperTest.kt` |
| **Implementation status** | Confirm body states this device is wiped, cannot be undone, and copies on other mesh devices are not wiped. `wipeAllLocalData` returns false if `dbDir` remains. `performPanicWipe` does not relaunch on failure; Settings shows an incomplete-wipe dialog. |
| **Tests run** | `./gradlew testDebugUnitTest --tests io.github.mycena_project.app.LocalDataWiperTest` SUCCESS (missing dir; successful delete; incomplete delete is failure). Full `:app:testDebugUnitTest` SUCCESS. |
| **Tests not run** | HV-18 on a physical device |
| **Remaining hardware verification** | HV-18 panic row (section D) |

No other P0 **code** defect from the backlog is still unimplemented. Phone-to-phone, FileProvider, split-APK refuse, share permissions, `nodeFailed`, MeshEventBus `this@meshEvents`, and R8 Tink `-dontwarn` are already in source (`DONE_STATIC` / `DONE_TESTED`).

---

## B. Build/release fixes

Required to produce a **valid hardware-test artifact**. Not a git tag. GitHub `reproducible-build.sh` remaining on debug is **not** this slice.

### RFB-REL-01 — Hardware APK is `assembleRelease`, recorded, not `assembleDebug`

| Field | Content |
|-------|---------|
| **ID** | RFB-REL-01 (hardware-artifact slice) |
| **Current state** | `DONE_STATIC` (2026-08-29, rebuilt after PERS-01) |
| **Files changed** | `scripts/build-technical-beta-apk.sh`; `android/app/build.gradle.kts`; `android/build.sh`; `ShareApkArtifact.kt` + share UI/ViewModel; `WifiDirectSharer.kt` landing copy; docs listed in §C; `Makefile` `android-technical-beta` |
| **Implementation status** | Technical Beta = `assembleRelease` + local JKS. Development = `assembleDebug`. Play AAB unchanged. Share refuses debug/split/incomplete. |
| **Tests run** | `./gradlew testDebugUnitTest` SUCCESS. `./scripts/build-technical-beta-apk.sh` VERIFY_OK. Hashes in [TECHNICAL_BETA_ARTIFACT_VERIFICATION.md](./TECHNICAL_BETA_ARTIFACT_VERIFICATION.md). |
| **Artifact** | `artifacts/technical-beta/mycena-0.1.75-technical-beta-android.apk` — SHA-256 `276e653ee6c128445a83b133bf98c9b3b83a306fc461859f4fbb6a9dacf11678`; cert SHA-256 `536d0992d3f13825b5fdeaf8ef3a7193a1d8045fd3754218a76f609eba6eeb9e`; BLAKE3 `1fc9246508f73fb44d698018c1d4b36264d440421e8efd033264328bf7953ac0`. Not tagged. Not published. |
| **Tests not run** | Hardware install; two-phone share |
| **Remaining hardware verification** | `adb install` of the Technical Beta APK (HV-01); G3 share |

Switching GitHub `reproducible-build.sh` / CI to `assembleRelease` is **not** this slice. Those remain Development APKs until a later gate.

---

## C. Documentation corrections

Only corrections that affect **truthful release status** or **testability** (what testers install, what is in-scope). Full plan §11 C1–C19 (RFB-DOC-01) is **not** in this queue.

### RFB-DOC-05 — BLE pocket mesh must not read as proven

| Field | Content |
|-------|---------|
| **ID** | RFB-DOC-05 (remainder) |
| **Current state** | `DONE_STATIC` |
| **Exact problem (was)** | `docs/KNOWN_LIMITATIONS.md` listed **Android BLE pocket mesh** as a **Good fit** while BLE-air was empty. |
| **Files changed** | `docs/KNOWN_LIMITATIONS.md` (disaster/outage BLE bullet only; ADR 002 kept) |
| **Implementation status** | BLE pocket mesh is described as implemented, **not hardware-verified**, with HV-06 / BLE-air as future validation. LAN mesh remains the Good-fit line. |
| **Tests run** | Doc review vs empty HV-06 / BLE-air row |
| **Tests not run** | RFB-HW-03 BLE-air drill |
| **Remaining hardware verification** | HV-06 / BLE-air (section D) |

### RFB-DOC-03 — Testers must not install the debug APK as the freeze binary

| Field | Content |
|-------|---------|
| **ID** | RFB-DOC-03 (remainder) |
| **Current state** | `DONE_STATIC` (2026-08-29) |
| **Files changed** | `docs/HARDWARE_TEST_RUNBOOK.md` §2.1; `docs/OFFLINE_DISTRIBUTION.md`; `android/README.md`; `README.md`; `docs/TECHNICAL_BETA_ARTIFACT.md` |
| **Implementation status** | Runbook and share docs name the Technical Beta APK. `make android-apk` remains Development. |
| **Tests run** | Doc review |
| **Tests not run** | Hardware |
| **Remaining hardware verification** | Testers follow the runbook once |

### RFB-PERS-04 — Keep-vs-wipe / signing-key note (docs only)

| Field | Content |
|-------|---------|
| **ID** | RFB-PERS-04 (documentation slice only) |
| **Current state** | `DONE_STATIC` (docs; verified 2026-08-29, no rewrite); device UNVERIFIED |
| **Files changed** | none this pass — [OFFLINE_DISTRIBUTION.md](./OFFLINE_DISTRIBUTION.md) reinstall / signing keys table already covers same Technical Beta key keep, uninstall wipe, and Debug ↔ Technical Beta ↔ Play cannot update in place |
| **Implementation status** | Written disposition exists and matches current artifact policy. |
| **Tests run** | Doc review |
| **Tests not run** | HV-17 uninstall/reinstall |
| **Remaining hardware verification** | HV-17 (Pixel 6 HV-16 `pass` 2026-08-30; same Technical Beta key, identity kept) |

### RFB-DOC-06 — Do not tell testers share is uncommitted / software is complete

| Field | Content |
|-------|---------|
| **ID** | RFB-DOC-06 |
| **Current state** | `DONE_STATIC` |
| **Exact problem (was)** | WIP stash note said share was not committed. Category 3 said software backlog was complete. |
| **Files changed** | `docs/WIP-ANDROID-SHARE-STASH.md`; `docs/WORK_REMAINING.md` Category 3 footer + iteration-plan closer |
| **Implementation status** | Stash note: ShareApp is on `main`; stash may hold extra deltas — do not apply blindly. Category 3 historical completeness does not imply freeze P0 / hardware work is done. |
| **Tests run** | Doc review |
| **Tests not run** | Hardware |
| **Remaining hardware verification** | none for this item |

---

## D. Hardware-only items

Already implemented (or docs-only exceptions already published). **Do not implement these in the next coding pass.** Physical verification only.

| ID | Current state | What hardware does |
|----|---------------|-------------------|
| RFB-P2P-01 | DONE_STATIC | API 26 Direct fallback: no `NoSuchMethodError` |
| RFB-P2P-02 / PERM-01 | DONE_TESTED | Grant Nearby/location; share starts |
| RFB-P2P-03 | DONE_TESTED | Hotspot / blank SSID / fallback on device |
| RFB-P2P-04 | DONE_TESTED | Share URL is not localhost on the phone |
| RFB-P2P-05 | DONE_STATIC | Sidecar / join on recipient |
| RFB-P2P-06 | DONE_STATIC | Import `.myc` then onboard |
| RFB-P2P-07 | DONE_STATIC | Failures show UI, not a hung spinner |
| RFB-P2P-08 | DONE_TESTED | Hotspot fail → Direct or Quick Share |
| RFB-APK-01 | DONE_TESTED | Unknown apps / Auto Blocker / Play Protect |
| RFB-APK-02 | DONE_STATIC | Quick Share from cache-path |
| RFB-APK-03 | DONE_STATIC | Universal APK installs on matrix ABI |
| RFB-PERM-02 | UNVERIFIED | Deny paths, two API levels, no crash |
| RFB-NET-01 | DONE_STATIC | Init failure UI on a real start failure |
| RFB-NET-02 | UNVERIFIED | LAN/QR pairing |
| RFB-PERS-01 | DONE_TESTED (impl); HV UNVERIFIED | Panic row (identity gone, app launchable) |
| RFB-PERS-02 | UNVERIFIED | Restart persistence |
| RFB-PERS-03 | DONE_STATIC | Sidecar prefs survive first-run |
| RFB-PERS-04 | DONE_STATIC (docs); HV UNVERIFIED | `adb install -r` on matrix |
| RFB-OEM-01 | DONE_STATIC (exception b) | Optional named Galaxy (a); not required to *start* matrix |
| RFB-OEM-02 | UNVERIFIED | Idle / FGS kill |
| RFB-OEM-03 | UNVERIFIED | Remaining OEM-MT rows |
| RFB-HW-01 | UNVERIFIED | Fill HARDWARE_TEST_MATRIX |
| RFB-HW-02 | UNVERIFIED | LEVEL0 A2 sign-off |
| RFB-HW-03 | UNVERIFIED | BLE-air **or** keep DOC-05 wording |
| RFB-HW-04 | DONE_STATIC | Use runbook + capture script; do not re-author |

**Not in D and not in A–C (later gates, not hardware entry):** RFB-TEST-01, TEST-03, SEC-04, SC-01, HYG-01, REL-02 (tag), REL-03 (G10), RFB-DOC-01 (full C1–C19).

---

## Strict implementation sequence

As short as possible. Docs in steps 1–4 have no code dependency on each other and **may be one commit**. Order below is the merge order if split.

| Step | ID | Why this order |
|------|-----|----------------|
| 1 | RFB-DOC-05 | Stops BLE-air being treated as proven before anyone installs. |
| 2 | RFB-DOC-03 | Stops testers building/installing debug as the freeze APK. |
| 3 | RFB-DOC-06 | Stops “share not on main / software complete” confusion. |
| 4 | RFB-PERS-04 (docs) | Stops wrong expectations on reinstall rows. |
| 5 | RFB-PERS-01 | Only remaining P0 **code** before HV-18 is meaningful. |
| 6 | RFB-REL-01 (artifact) | Build `assembleRelease` from the tree after 1–5; record hashes. |

**Stop.** Do not start RFB-REL-02 (tag), CI debug conversion, or hardware matrix filling as *implementation*. After step 6, only section D.

---

## P0_IMPLEMENTATION_EXIT_CRITERIA

All of the following must be **true** before stopping code/docs/build work and moving **exclusively** to hardware validation:

1. **RFB-PERS-01** is no longer `OPEN` in source: panic confirm copy includes mesh copies on other devices; incomplete wipe is not presented as success.
2. **RFB-DOC-05** remainder: `KNOWN_LIMITATIONS.md` does not list Android BLE pocket mesh as a **Good fit** (or equivalent “works today”) while the BLE-air matrix row is empty.
3. **RFB-DOC-03** remainder: hardware runbook does **not** instruct `make android-apk` / debug as the freeze test binary; OFFLINE_DISTRIBUTION does not contradict the recorded `assembleRelease` hardware APK.
4. **RFB-DOC-06**: WIP stash note and WORK_REMAINING Category 3 no longer claim share is uncommitted or that software freeze work is empty.
5. **RFB-PERS-04** documentation slice: testers have a written keep-vs-wipe / debug-vs-release-key disposition.
6. **RFB-REL-01** hardware slice: one `assembleRelease` APK built from that tree exists; SHA-256 (and cert fingerprint) recorded; it is the named hardware artifact.
7. **No further application or build-script changes** except fixes proven necessary by hardware (those re-open a minimal coding pass). Filling the matrix, CI job URLs, Gitleaks URLs, and `v*.*.*` tags are **not** exit criteria for this phase.

When 1–7 hold (recorded **true** 2026-08-29 after A–C + rebuilt Technical Beta APK):

| Phase | Meaning |
|-------|---------|
| Implementation phase | **Ended** |
| Code/fixes complete | **Yes** for hardware entry (not for G9/G10) |
| Clean release artifact | **Yes** as a local recorded `assembleRelease` APK (not tagged, not G9) |
| Hardware validation only | **Begins** (section D) |
| G9 / G10 | Still **FAIL / NOT APPROVED** until hardware + remaining gates |

Do not mark release freeze approved because this queue is empty.
