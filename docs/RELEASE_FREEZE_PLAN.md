# Mycena Public Technical Beta / Development Freeze Candidate

**Status:** Planning document (G0). No gate below is claimed complete by this file.
**Created:** 2026-08-29
**Milestone name:** Mycena Public Technical Beta / Development Freeze Candidate
**Public status today:** **Public Technical Beta — Feature Development Frozen, Release Validation Ongoing.** This file names a **candidate** G10 end state. It does **not** mean G10 passed or that public release is approved.
**Does not replace:** [WORK_REMAINING.md](./WORK_REMAINING.md), [ROADMAP.md](./ROADMAP.md), [LEVEL0_ROADMAP.md](./LEVEL0_ROADMAP.md), [PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md), [SECURITY_ROADMAP.md](./SECURITY_ROADMAP.md)

This plan is a **stabilization and release-readiness overlay**. It does not invent a new product roadmap. Work items still live in the documents listed above; this file defines the **gates, evidence, and freeze rules** under which active feature development may pause.

---

## 1. Objective

Bring Mycena to a **credible, testable, publicly publishable technical-beta state** and then **pause active feature development**.

The intended end state is:

> **Mycena Public Technical Beta / Development Freeze Candidate**

That means:

- the **Android-first core** (startup, onboarding, mesh, messaging, phone-to-phone distribution) works reliably on **documented supported devices**;
- known **critical** issues are resolved or have an explicit written disposition;
- remaining limitations are **honest and consistent** with public claims;
- the **repository is safe to make public** (no secrets, no misleading docs, CI/release path documented);
- **security, dependency, and supply-chain** concerns are reviewed and either mitigated or accepted in writing;
- **hardware validation** can be performed systematically against a recorded matrix;
- a **release candidate** exists that a third party can build, install, and verify.

This milestone is **not**:

- a claim of perfection;
- a formal security certification or audit completion;
- a claim of hardware compatibility beyond devices actually tested;
- Play Store listing (that is a **separate** gate — see [§2.3](#23-play-store-vs-this-freeze) and [PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md));
- a Signal replacement, emergency-services product, or nation-state-resistant messenger ([KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md), [BETA_SECURITY_ASSESSMENT.md](./BETA_SECURITY_ASSESSMENT.md)).

Public positioning for this freeze remains the README’s **early / technical beta** language: expect bugs; APIs and wire formats may still change **after** the freeze only via the [§7 Freeze Rules](#7-freeze-rules). During the freeze, they must not change without a documented release-blocker justification.

---

## 2. Scope

### 2.1 Must be stable (in-scope for this freeze)

Derived from existing product docs ([README.md](../README.md), [android/README.md](../android/README.md), [OFFLINE_DISTRIBUTION.md](./OFFLINE_DISTRIBUTION.md), [CONNECTIVITY_MODES.md](./CONNECTIVITY_MODES.md), [LEVEL0_ROADMAP.md](./LEVEL0_ROADMAP.md) Phase A–B, [mvp-smoke-test.md](./mvp-smoke-test.md), [RELEASING.md](../RELEASING.md), [RELEASE_INTEGRITY.md](./RELEASE_INTEGRITY.md)) — not a new feature list.

| Area | What “stable” means for this freeze |
|------|-------------------------------------|
| **Android application startup** | Fresh install of a release (or documented debug) APK launches without a reproducible crash; `MeshService` starts as a foreground service; Rust node initializes. |
| **Onboarding** | First-launch flow completes (`OnboardingActivity` + [ui-copy/onboarding](../ui-copy/onboarding/README.md)); sideload path can skip via `OfflineBootstrap` when a bootstrap sidecar is present. |
| **Core application flow** | Bottom navigation reaches Connect / Chat / Peers / Settings / Share; pending inbound contacts, QR v3 invite, and Settings connectivity toggles behave as [CONNECTIVITY_MODES.md](./CONNECTIVITY_MODES.md) describes. |
| **Mesh networking** | Two devices on the same LAN discover via mDNS (or QR) and exchange traffic without a working Internet relay ([LEVEL0_ROADMAP.md](./LEVEL0_ROADMAP.md) Phase B; [mvp-smoke-test.md](./mvp-smoke-test.md) Phase A/B). |
| **BLE transport** | Android BLE F1–F3 as documented in [ADR 002](./adr/002-ble-mesh-transport.md) and [DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md) **on devices in the hardware matrix**. Desktop BLE status is **contested in docs** — see [§11](#11-unresolved-documentation-contradictions); freeze must record a single honest claim before G10. |
| **Wi-Fi transport** | Same-subnet Wi-Fi / mDNS path; hotspot and Wi-Fi Direct fallbacks used by phone-to-phone share ([OFFLINE_DISTRIBUTION.md](./OFFLINE_DISTRIBUTION.md), [ADR 007](./adr/007-wifi-direct-bulk-bearer.md)). Field validation of Wi-Fi Aware / WebRTC is **not** a freeze blocker ([WORK_REMAINING.md](./WORK_REMAINING.md) Category 2). |
| **Messaging** | Encrypted 1:1 chat when keys are known; send **fail-closed** without peer enc key (SD-081); reconnect after force-quit without re-scan ([mvp-smoke-test.md](./mvp-smoke-test.md) A2). |
| **Phone-to-phone distribution** | Device A **Share app** → Device B installs APK without Play Store or desktop ([OFFLINE_DISTRIBUTION.md](./OFFLINE_DISTRIBUTION.md)). |
| **Offline APK acquisition / install flow** | Hotspot or same-LAN HTTP `:8765` / `/mycena.apk` + bootstrap sidecar; unknown-sources / install-permission handling on supported Android versions; first launch joins the sharer’s mesh. |
| **Persistence** | Contacts, enc keys, and chat history survive app restart (sled + vault; [LEVEL0_ROADMAP.md](./LEVEL0_ROADMAP.md) B2). |
| **Recovery / restart behavior** | Force-quit both apps; reopen; chat still encrypts without re-scan (A2 #5). Process death / OEM kill of `MeshService` must be characterized on the hardware matrix (especially Samsung — G4). |
| **Panic / wipe behavior** | Settings panic wipe (and CLI `mycena panic`) deletes local messages, identity key, and credit ledger on **this device**; UI discloses mesh copies elsewhere persist ([PLAY_CONSOLE_SUBMIT_CHECKLIST.md](./PLAY_CONSOLE_SUBMIT_CHECKLIST.md), [THREAT_MODEL.md](./THREAT_MODEL.md) §2.1). |
| **Update / reinstall behavior** | Reinstall over an existing install (`adb install -r` or in-app update path if shipped); identity/data disposition is documented and tested (keep vs wipe). |
| **Release artifact integrity** | Tag `v*.*.*` produces APK + `SHA256SUMS.txt` + integrity manifest per [RELEASING.md](../RELEASING.md) / [RELEASE_INTEGRITY.md](./RELEASE_INTEGRITY.md); a third party can hash-verify. M-of-N “official” co-sign is required for labeling a build **official**; an unsigned CI artifact may still be a freeze **candidate** if the gap is documented. |

Supported OS floor for Android remains **API 26+** ([README.md](../README.md)). The freeze does **not** claim every OEM at API 26; it claims the **recorded hardware matrix** plus documented exceptions.

### 2.2 Explicitly out of scope for this freeze

Do **not** start these to “make the freeze nicer.” They remain in existing roadmaps as post-freeze / Category 1–2 / later-phase work.

| Out of scope | Where already tracked |
|--------------|------------------------|
| New product features | [ROADMAP.md](./ROADMAP.md) Optional; [LEVEL0_ROADMAP.md](./LEVEL0_ROADMAP.md) later vision |
| Major architectural redesigns (sled migration, identity-key split, WASM-only mini-apps) | [DEPENDENCY_RISK.md](./DEPENDENCY_RISK.md), [ARCHITECTURE_RISKS.md](./ARCHITECTURE_RISKS.md), [SECURITY_ROADMAP.md](./SECURITY_ROADMAP.md) Phases 2–5 |
| Speculative optimizations | — |
| Additional transports as freeze requirements (Wi-Fi Aware, WebRTC data channel, NFC, Tor/Nym) | [WORK_REMAINING.md](./WORK_REMAINING.md) Category 2; ADR 009/010; SD-180 |
| New economic / token features; MeshCoin as money | [MESHCOIN_POSITIONING.md](./MESHCOIN_POSITIONING.md); SD-011 / SD-160 remain preview-only |
| Non-critical UX polish | — |
| iOS shipping host / App Store | [MOBILE_PLATFORMS.md](./MOBILE_PLATFORMS.md), [ios/README.md](../ios/README.md) |
| Hardware-bound identity keys, root/jailbreak refuse, user-auth gate | [SECURE_HARDWARE_STATUS.md](./SECURE_HARDWARE_STATUS.md); [WORK_REMAINING.md](./WORK_REMAINING.md) Category 1 |
| MLS / group PSK replacement | SD-101 |
| Auto GPS geohash | [WORK_REMAINING.md](./WORK_REMAINING.md); [AR-06_REMAINING_GAPS.md](./AR-06_REMAINING_GAPS.md) |
| City-wide multi-hop / 100k festival mesh | [DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md), [LEVEL0_ROADMAP.md](./LEVEL0_ROADMAP.md) |
| Formal external security **certification** | [EXTERNAL_SECURITY_REVIEW.md](./EXTERNAL_SECURITY_REVIEW.md) (engagement pack exists; review **not commissioned**) |
| Activist / journalist outreach | [BETA_SECURITY_ASSESSMENT.md](./BETA_SECURITY_ASSESSMENT.md); [PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md) |
| 10–50 node field drill playbook as a freeze blocker | [DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md) 4.1; Category 2 |

Experimental / preview surfaces (**MeshCoin, mini-app store, WASM, Shade padding, bulletin as unmoderated public scope**) may ship **as labeled preview**. They must not contradict [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md). They are **not** freeze pass criteria except: no P0 crash in those screens on the default path, and MeshCoin copy stays preview-only (SD-011).

### 2.3 Play Store vs this freeze

Existing docs mix **two different public-beta notions**:

| Track | Source | This freeze |
|-------|--------|-------------|
| **GitHub public technical beta** (sideload APK, public repo, CI full tier) | [CI_PUBLIC_TRANSITION.md](./CI_PUBLIC_TRANSITION.md), [README.md](../README.md) GitHub Releases | **In scope** (G8–G10) |
| **Google Play public / closed beta** | [PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md), [PLAY_CONSOLE_SUBMIT_CHECKLIST.md](./PLAY_CONSOLE_SUBMIT_CHECKLIST.md), [BETA_SECURITY_ASSESSMENT.md](./BETA_SECURITY_ASSESSMENT.md) | **Not required** for G10 unless product later promotes it into P1. Treat as P1-unless-accepted. |

[BETA_SECURITY_ASSESSMENT.md](./BETA_SECURITY_ASSESSMENT.md) already distinguishes **closed technical preview (OK)** from **public Play beta (conditional OK)**. This freeze aligns with the former plus **public repository + GitHub Release APK**.

---

## 3. Release gates

Gates are sequential in **intent** (do not skip evidence), but engineering work inside a gate may overlap. A later gate cannot be marked **pass** while an earlier gate is **fail** unless a written exception names the skipped evidence and residual risk.

**This document completing G0 does not pass any later gate.**

### G0 — Documentation / planning

| | |
|--|--|
| **Objective** | A single freeze plan exists; it maps to existing trackers and records known contradictions instead of silently resolving them. |
| **Required evidence** | This file in `docs/RELEASE_FREEZE_PLAN.md`; date; pointer from §10 to [WORK_REMAINING.md](./WORK_REMAINING.md) / [PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md). |
| **Pass criteria** | Maintainers agree this is the freeze overlay; contradictions in §11 are listed, not hidden. |
| **Possible blockers** | Competing unpublished plans; product decision that Play Store **is** in-scope for G10 (would require amending §2.3). |

### G1 — Repository and architecture audit

| | |
|--|--|
| **Objective** | Code, docs, and claims describe the **same** product. Produce a dated inventory of crates, Android host, CI, secrets surface, and leftover WIP. |
| **Required evidence** | Written audit note (commit SHA, date) covering: crate map vs [architecture.md](./architecture.md); Android entrypoints (startup, onboarding, share, panic, BLE, Wi-Fi); CI workflows vs [CI_PUBLIC_TRANSITION.md](./CI_PUBLIC_TRANSITION.md); presence/absence of secrets in tree (`secret-scan.yml` result); disposition of [WIP-ANDROID-SHARE-STASH.md](./WIP-ANDROID-SHARE-STASH.md); resolution **or explicit keep** of each §11 contradiction. |
| **Pass criteria** | Every §11 item has a disposition (fix doc, fix code, or accept with owner). No undocumented second copy of the mesh protocol. Layer boundaries still match [ADR 005](./adr/005-layer-cargo-boundaries.md). |
| **Possible blockers** | Stashed share work that is required for G3 but not on `main`; identity/BLE claims that cannot be made consistent without a product decision; secrets or private credentials in git history. |

### G2 — Critical Android stabilization

| | |
|--|--|
| **Objective** | No known **reproducible P0 crash** on the in-scope Android flows (startup, onboarding, core nav, mesh start, permissions denial paths, panic). Permissions for BLE / nearby / notifications / camera / install packages are requested and explained; denial does not crash. |
| **Required evidence** | Device logcat (or tombstone) for any crash investigated — [CRASH_REPORTING.md](./CRASH_REPORTING.md). Pass/fail table: [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md) HV-01–05, HV-18, HV-20 on at least one API 26–adjacent and one current-API device (models recorded). Procedure: [HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md). Android unit tests: `./gradlew testDebugUnitTest` output. Instrumentation today is thin (`MeshServiceInstrumentationTest` only) — G5 expands coverage; G2 does not pretend connected tests are a full suite. |
| **Pass criteria** | Fresh install → onboarding → mesh running, twice in a row, on recorded devices. Panic wipe leaves the app launchable and without previous identity. No send of plaintext chat (SD-081) on the happy path. |
| **Possible blockers** | Reproducible crash on a matrix device; permission flow that leaves BLE/share silently broken without UI; `MeshService` never starts on a supported OEM. |

### G3 — Phone-to-phone distribution

| | |
|--|--|
| **Objective** | Offline APK share/install/join works as [OFFLINE_DISTRIBUTION.md](./OFFLINE_DISTRIBUTION.md) describes, **without** Play Store, desktop, or Internet. |
| **Required evidence** | Two-device record in [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md) HV-10/HV-11: models, OS versions, share mode used (hotspot / Wi-Fi Direct / same LAN / system Quick Share), whether Device B installed and auto-onboarded, hashes of the APK served vs installed. Procedure: [HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md) + [PHONE_TO_PHONE_MANUAL_TEST.md](./PHONE_TO_PHONE_MANUAL_TEST.md). Screenshot or QR capture optional but not sufficient alone. Known OEM failures logged as limitations, not “should work.” |
| **Pass criteria** | At least one successful end-to-end share on two physical phones. Fallback path documented when hotspot fails (`WifiPauseHelper` / Wi-Fi Direct / Quick Share). Bootstrap sidecar does not execute code (doc + spot-check). |
| **Possible blockers** | Share path only exists in a git stash ([WIP-ANDROID-SHARE-STASH.md](./WIP-ANDROID-SHARE-STASH.md)); hotspot fails on all test OEMs with no working fallback; install blocked with no user-facing permission path; malicious-APK warning missing so public claims would be dishonest ([OFFLINE_DISTRIBUTION.md](./OFFLINE_DISTRIBUTION.md) § Security). |

### G4 — Samsung / OEM robustness

| | |
|--|--|
| **Objective** | Characterize (and fix P0 issues on) **Samsung / One UI** and other OEM behaviors that commonly break mesh apps: battery optimization / sleep, foreground service kills, Wi-Fi hotspot / Direct, BLE scan, unknown-app install. Static audit and code mitigations: [SAMSUNG_STABILITY_AUDIT.md](./SAMSUNG_STABILITY_AUDIT.md). **G4 still requires a named device or the published Samsung-untested exception** — static hardening is not a G4 pass. |
| **Required evidence** | Named Samsung model(s) + One UI / Android version in [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md) §1.2; results for: cold start after hours idle (HV-12), share hotspot (HV-10), BLE airplane-mode chat if in matrix (HV-06/HV-09), notification permission (HV-04/HV-20), install-unknown-apps (HV-11). Procedure: [HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md) + OEM-MT in [SAMSUNG_STABILITY_AUDIT.md](./SAMSUNG_STABILITY_AUDIT.md). If no Samsung device is available, G4 **cannot pass** — it must be an **explicit exception** in the hardware matrix (P1), not a silent skip. |
| **Pass criteria** | Either (a) P0 flows pass on at least one current Samsung phone, or (b) written exception: “Samsung untested; public docs must not imply Samsung support.” |
| **Possible blockers** | One UI kills `MeshService` with no recovery; hotspot error codes with no fallback; BLE scan illegal without location on that OEM and UI does not ask. |

### G5 — Automated regression coverage

| | |
|--|--|
| **Objective** | CI and local automated tests cover freeze-critical Rust and Android **without** claiming they replace hardware. |
| **Required evidence** | Green (or waived-with-reason) GitHub Actions on the freeze candidate SHA: `ci.yml` (Rust), `android-ci.yml` (build + `testDebugUnitTest`), `fuzz-pr.yml` smoke, `secret-scan.yml`. Commands and job URLs recorded. MycenaLab CI gates as already defined in [MYCENALAB.md](./MYCENALAB.md) when `MYCENA_CI_TIER` allows. Do **not** claim `cargo test --workspace` passed unless the log is attached. |
| **Pass criteria** | Freeze-candidate commit has recorded CI. New P0 Android crash from G2/G3 has a regression test **or** a documented reason it is device-only. Unwrap/expect production gate scripts in [EXTERNAL_SECURITY_REVIEW.md](./EXTERNAL_SECURITY_REVIEW.md) still run in CI or are recorded as run. |
| **Possible blockers** | Android CI skipped on `main` because of path filters while freeze claims Android is tested ([android-ci.yml](../.github/workflows/android-ci.yml) is path-filtered unless `MYCENA_CI_TIER=full`); flaky tests masked by retries without a ticket; fuzz PR smoke failing. |

### G6 — Hardware validation

| | |
|--|--|
| **Objective** | Systematic physical-device evidence for the in-scope flows. Fill [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md) using [HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md). Existing **Level 0** scripts: [mvp-smoke-test.md](./mvp-smoke-test.md) Phase A (Mac + Android) and Phase B (LAN). [LEVEL0_ROADMAP.md](./LEVEL0_ROADMAP.md) A2 is **“Done (doc) — manual sign-off pending.”** [WORK_REMAINING.md](./WORK_REMAINING.md) still lists that matrix and BLE airplane-mode drill as outstanding. Battery evidence exists only for **Pixel 6** ([BATTERY_PROFILING_RESULTS.md](./BATTERY_PROFILING_RESULTS.md)) — that is **not** a full freeze matrix. |
| **Required evidence** | Filled [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md) (HV-01–20 plus §3 G6 summary). Procedure: [HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md). Minimum scenarios still include A1–A5 and B1–B3 from [mvp-smoke-test.md](./mvp-smoke-test.md); G3 share; panic; reinstall. BLE-only airplane drill ([DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md) 2.4–2.5) is **P1** unless accepted as limitation (“BLE pocket mesh unverified on this freeze”). |
| **Pass criteria** | Every in-scope scenario is pass, fail-with-limitation, or skipped-with-disposition. No scenario left as “should work.” |
| **Possible blockers** | No second phone; Mac+Android pairing never signed off; BLE-only never demonstrated while README/KNOWN_LIMITATIONS still imply pocket mesh. |

### G7 — Security / dependency / supply-chain review

| | |
|--|--|
| **Objective** | Internal review against existing security corpus. **Not** a certification. External review remains uncommissioned ([EXTERNAL_SECURITY_REVIEW.md](./EXTERNAL_SECURITY_REVIEW.md)). |
| **Required evidence** | Dated snapshot of [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) (beta-blocker rows: only SD-011 / SD-061 / SD-160 style **if MXC treated as money** — preview-only keeps them non-blocking). Confirm P0 network mitigations have not regressed (SD-030, SD-031, SD-080, SD-081, SD-171, SD-041). Run `./scripts/check-dependencies.sh` (or CI equivalent); record allowlist expiry ([DEPENDENCY_RISK.md](./DEPENDENCY_RISK.md) — several RUSTSEC rows expire **2026-09-30**). SBOM attached on release per [RELEASING.md](../RELEASING.md). [ACCEPTED_SECURITY_RESIDUALS.md](./ACCEPTED_SECURITY_RESIDUALS.md) either **signed** or explicitly deferred with owner (GitHub-only: unsigned accepted 2026-08-29). Secret scan clean on the freeze SHA. Internal review page: [SECURITY_RELEASE_REVIEW.md](./SECURITY_RELEASE_REVIEW.md) (not certification; TM checklist is **§9**). |
| **Pass criteria** | No **unaddressed** Critical debt for the **preview** product. Residuals R1–R10 either signed or listed as freeze-accepted with the same honesty as [THREAT_MODEL.md](./THREAT_MODEL.md) §5. Public claims still refuse Signal-level security. |
| **Possible blockers** | New P0 vulnerability without fix or residual entry; expired advisory allowlist with no review; secrets in repo; MeshCoin UI implying real value; unsigned residuals **and** Play Store treated as in-scope (Play path requires sign-off per [PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md)). |

### G8 — Public repository hygiene

| | |
|--|--|
| **Objective** | Safe to set GitHub visibility to **Public** and to leave the tree as the public source of truth. |
| **Required evidence** | [CI_PUBLIC_TRANSITION.md](./CI_PUBLIC_TRANSITION.md) day-of checklist filled (or items waived with reason). Clone URLs and README match visibility. No private credentials, `.env` secrets, or internal-only operator keys. License AGPL-3.0-or-later present. Security contact [SECURITY.md](../SECURITY.md). Privacy policy path documented even if Pages not live (Pages live is P1 for Play; for GitHub-only freeze, in-app policy + `docs/legal/` must exist and match). `MYCENA_CI_TIER=full` planned or enabled when public (budget tier is OK only while private — document the choice). |
| **Pass criteria** | A stranger can clone, read limitations, report vulns privately, and build from [android/README.md](../android/README.md) / [RELEASING.md](../RELEASING.md) without asking for missing private files. |
| **Possible blockers** | Secrets in history; CONTRIBUTING still implying a private-only workflow without CI_PUBLIC_TRANSITION; Dependabot still disabled with SD-150 text claiming it will re-enable at public launch — must do it or update the debt row. |

### G9 — Release candidate

| | |
|--|--|
| **Objective** | A tagged or tag-ready build that is the freeze artifact. |
| **Required evidence** | Version, git tag or freeze commit SHA, APK (and optional desktop bundles), `SHA256SUMS.txt`, integrity JSON if produced, SBOM if produced. Install smoke on one matrix Android device. [RELEASING.md](../RELEASING.md) steps 1–5 followed or deviations listed. **No root `CHANGELOG.md` exists today** (only `third-party/libp2p-gossipsub/CHANGELOG.md`) while RELEASING step 2 says to update `CHANGELOG.md` — G9 must create a changelog **or** amend RELEASING (doc fix allowed). |
| **Pass criteria** | Artifact installs; hash recorded; source tag/commit recorded; integrity story matches [RELEASE_INTEGRITY.md](./RELEASE_INTEGRITY.md) §7 honesty (desktop/Play AAB not fully reproducible). |
| **Possible blockers** | Reproducible-build container fails; unsigned “official” wording in README; tag without CI green. |

### G10 — Final public-release gate

| | |
|--|--|
| **Objective** | Maintainers may state: the project is a **public technical beta** and **development may pause** under [§8](#8-exit-criteria). |
| **Required evidence** | G0–G9 pass records (or named exceptions). Public docs (README, KNOWN_LIMITATIONS, BETA_SECURITY_ASSESSMENT) match the candidate. Post-freeze backlog extracted from remaining WORK_REMAINING / SECURITY_ROADMAP items. |
| **Pass criteria** | All of [§8 Exit criteria](#8-exit-criteria). |
| **Possible blockers** | Any open P0 without disposition; docs still contradicting BLE/desktop/share; hardware matrix empty; residuals unsigned **if** public copy implies they were accepted by named roles. |

---

## 4. Definition of Done

Checkboxes are **not** pre-ticked. Tick only with evidence per [§5](#5-evidence-rules).

### 4.1 P0 — Mandatory (must be true before public freeze)

- [ ] No known **reproducible P0 crash** on in-scope Android flows (G2).
- [ ] Phone-to-phone distribution works on at least one recorded two-phone pair (G3).
- [ ] Permissions (notifications, BLE/nearby, camera for QR, install-unknown-apps as required by OS) are requested; denial is non-fatal and explained.
- [ ] A release artifact **installs** on each **supported** device in the matrix (supported = listed without exception).
- [ ] No known **critical** security issue remains unaddressed: either fixed, or recorded as an accepted residual with impact understood ([ACCEPTED_SECURITY_RESIDUALS.md](./ACCEPTED_SECURITY_RESIDUALS.md) / [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md)). Preview MeshCoin is **not** positioned as money (SD-011).
- [ ] No secrets / private credentials in the published tree; secret-scan evidence on the freeze SHA.
- [ ] Build and release process documented ([RELEASING.md](../RELEASING.md), [android/README.md](../android/README.md), [RELEASE_INTEGRITY.md](./RELEASE_INTEGRITY.md)) and matches what CI actually does.
- [ ] Known limitations documented and **not contradicted** by README / store-like copy ([KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md), [BETA_SECURITY_ASSESSMENT.md](./BETA_SECURITY_ASSESSMENT.md)).
- [ ] Panic wipe deletes local identity and message DB on device; limitation “mesh copies remain” is user-visible.
- [ ] Encrypted chat fail-closed without peer key (SD-081) — do not regress.
- [ ] Unsigned wire `Data` still dropped (SD-030) — do not regress.
- [ ] `android:allowBackup="false"` remains (SD-171) — do not regress.
- [ ] Security reporting path is `github.com/mycena-project/Mycena/security/advisories/new` / GitHub private advisory ([SECURITY.md](../SECURITY.md)).

### 4.2 P1 — Required before release unless explicitly accepted

Each P1 item needs either **evidence of done** or a **written acceptance** (owner, date, residual, user-visible doc update).

- [ ] Hardware test matrix completed ([HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md) / [§5.1](#51-hardware-matrix-template)) — **or** exceptions listed in KNOWN_LIMITATIONS.
- [ ] Samsung / OEM validation (G4) — **or** “Samsung untested” in public limitations.
- [ ] Update / reinstall tested (keep vs wipe documented).
- [ ] Dependency / license review (`check-dependencies.sh`, allowlist dates, SBOM on RC).
- [ ] Internal security review (G7) against threat model §8 checklist — **not** the same as external sign-off.
- [ ] [ACCEPTED_SECURITY_RESIDUALS.md](./ACCEPTED_SECURITY_RESIDUALS.md) signed **or** explicitly accepted as unsigned for GitHub-only beta (must not be described as a “release certificate”).
- [ ] Release integrity verification (`release-verify.sh` or documented hash check) on the RC APK.
- [ ] Documentation consistency: §11 contradictions resolved or accepted.
- [ ] LEVEL0 A2 **manual sign-off** (Mac + Pixel or updated device pair) — currently pending in [LEVEL0_ROADMAP.md](./LEVEL0_ROADMAP.md).
- [ ] BLE-only two-phone drill **or** pocket-mesh claim removed/softened in user docs.
- [ ] Privacy policy GitHub Pages URL live — required for **Play**; for GitHub freeze, accept in-app + `docs/legal/` if Pages is not enabled ([PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md) still says **DO NOW** for Play).
- [ ] Play Console Data Safety — **accept-as-deferred** unless Play is added to G10.
- [ ] External security review **scheduled or explicitly deferred** ([PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md): scheduled, not necessarily complete, for closed beta).
- [ ] `CHANGELOG.md` exists for the RC **or** RELEASING.md no longer requires it.
- [ ] Desktop BLE public claim matches implementation (see §11).
- [ ] Dependabot / `cargo vet` disposition vs SD-150 (SBOM + reproducible Android path already exist; debt row still lists them as missing).

### 4.3 P2 — Explicitly deferred (do not block freeze unless new evidence)

- iOS app shell / TestFlight / App Store.
- Tor / Nym / pluggable transports (SD-180).
- MLS / group epoch rotation (SD-101, SD-242).
- Hardware-bound identity keys, StrongBox-only master, biometric unlock, root detect (SD-120).
- Metadata padding / Shade in production (SD-070 — experimental, off by default).
- Multi-operator signed bootstrap as a **must-ship** (SD-022 / SD-220 remain documented residuals R6/R8).
- Formal mini-app red-team beyond [MINIAPP_REDTEAM_RESULTS.md](./MINIAPP_REDTEAM_RESULTS.md) (WORK_REMAINING still lists red-team as Category 1).
- 520-node live libp2p soak; 10–50 node neighborhood drill.
- Wi-Fi Aware / WebRTC / NFC **field** validation.
- Auto GPS geohash.
- MeshCoin BFT, faucet removal as money-layer work, hardware wallets.
- Full byte-identical APK zip across Gradle hosts ([RELEASE_INTEGRITY.md](./RELEASE_INTEGRITY.md) §7).
- Desktop Tauri reproducible builds.
- Play Store listing and closed-beta device fleet.
- OSS-Fuzz submission (blocked on public repo; listed in CI_PUBLIC_TRANSITION).
- Non-critical i18n coverage beyond existing locales.
- Native proximity UI (Proximity mini-app is the supported UX — [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md) deferred table).

If later evidence shows a P2 item is actually a **reproducible P0** (e.g. BLE never works on any phone while marketing says it does), promote it to P0.

---

## 5. Evidence rules

**Every completed gate needs evidence.** The following are **not** evidence:

- “Looks good”
- “Should work”
- “Works on my machine” without model / OS / build SHA
- A single screenshot of a home screen with no scenario
- Unticked checklists copied from this file

### Acceptable evidence (examples)

| Kind | Use for |
|------|---------|
| CI job URL + SHA | G5, G9 |
| Command output (test, clippy, `check-dependencies.sh`, `release-verify.sh`) | G5, G7, G9 |
| Device record: manufacturer, model, Android version, One UI version if Samsung, build fingerprint optional | G2–G4, G6 |
| Logcat / crash tombstone / ANR traces | G2, G4 |
| APK SHA256 / BLAKE3 | G3, G9 |
| Signed or dated review note (even 1 page) | G1, G7 |
| Manual scenario table with pass/fail and timestamp | G3, G6 |
| Screenshot **plus** scenario ID (supporting, not standalone) | Onboarding, share QR, panic confirm |
| Git stash / commit disposition | G1, G3 ([WIP-ANDROID-SHARE-STASH.md](./WIP-ANDROID-SHARE-STASH.md)) |

Store evidence where the project already stores artifacts when possible (`artifacts/hardware-runs/` for **raw logs**, gitignored — [CRASH_REPORTING.md](./CRASH_REPORTING.md)). Summary rows go in [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md). A `docs/freeze-evidence/` note may be added when recording a freeze SHA — **without** log bodies.

### 5.1 Hardware matrix template

**Live template (fill this, do not invent devices):** [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md)
**How to run every HV-* row:** [HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md)

§3 of the matrix file repeats the G6 summary table (A2-1, A2-2, A2-5, B1, G3, G4, Panic, Reinstall, BLE-air). Copy results there after the HV session. Do not fill with hypothetical devices.

**Already-known hardware datapoint (not a matrix):** Pixel 6 battery sessions in [BATTERY_PROFILING_RESULTS.md](./BATTERY_PROFILING_RESULTS.md). Reuse the model if still available; do not generalize to “all Pixels” or “all Android.”

---

## 6. Known limitations policy

A known limitation is **acceptable** for this freeze if **all** of the following hold:

1. **Accurate** — it matches current code and test evidence, not an old roadmap status.
2. **Non-contradictory** — README, beta website claims, in-app copy, and [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md) do not say the opposite.
3. **Impact understood** — security/reliability effect is stated (who is hurt, how badly), preferably with an `SD-*` or residual `R*` id.
4. **Disposition** — one of: *accept for beta*, *fix before G10*, *defer to post-freeze backlog* (named doc).

Dispositions live in [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md), [ACCEPTED_SECURITY_RESIDUALS.md](./ACCEPTED_SECURITY_RESIDUALS.md), and [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md). This freeze plan does **not** duplicate those lists. If two docs disagree, that is a **G1 defect** until §11 is cleared.

User-facing refusals that must remain ([KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md)):

- Not Signal-level security; not untraceable; MXC is not a bank; sideload is not app-store safe.

---

## 7. Freeze rules

After the **release-freeze candidate** is reached (G9 passed, G10 in progress or passed):

### Allowed without extra process (still need review / CI)

- P0 crash fixes
- Security fixes
- Release blockers (install broken, integrity broken, secret leaked)
- Reproducibility / build / CI fixes
- Documentation corrections (including limitation honesty)

### Not allowed without explicit written justification

Justification must name: why it is a freeze exception, risk of not doing it, and that it is not a new product bet.

- New features
- Major refactors (including sled migration)
- New protocols or transports
- Product expansion (iOS ship, Play listing, MeshCoin “payments”, new mini-app platforms)

Direct pushes to `main` remain allowed per [CONTRIBUTING.md](../CONTRIBUTING.md) for a small team, but freeze exceptions should still be **described in the PR or commit body** so the pause is auditable.

---

## 8. Exit criteria

Maintainers may say **“Active development may be paused”** only when **all** of the following are true:

1. **Release candidate exists** — G9 evidence: tag or pinned SHA, APK, hashes.
2. **Hardware matrix** has passed **or** every failure/skip has an explicit documented exception (P1).
3. **Known blockers** have dispositions (fix, accept, or backlog) — none left in “unknown.”
4. **Public repository audit passed** — G8 (or repo already public with the same checklist).
5. **Documentation reflects reality** — §11 contradictions closed or accepted; README still says technical beta, not certification.
6. **Release artifacts are reproducible/verifiable** to the degree [RELEASE_INTEGRITY.md](./RELEASE_INTEGRITY.md) actually claims (Android container hash; desktop/Play caveats preserved).
7. **Remaining work is on a post-freeze backlog** — not deleted: keep [WORK_REMAINING.md](./WORK_REMAINING.md) Category 1–2, [SECURITY_ROADMAP.md](./SECURITY_ROADMAP.md) Phases 1–5, [DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md) 4.x, [PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md) Play items if deferred.

Optional but recommended: set GitHub topics/description to **public technical beta / development paused** so visitors do not assume a dead project (see §9).

---

## 9. Post-freeze state

Conceptual state name:

> **STABILIZED / PUBLIC TECHNICAL BETA / DEVELOPMENT PAUSED**

This is **not abandonment**. Development is **intentionally paused** so that:

- users can test a **known** artifact;
- potential contributors can inspect the code (AGPL);
- potential customers can evaluate fit against honest limitations;
- potential investors can evaluate the **technical foundation** without a moving target;
- real-world feedback can accumulate against a frozen protocol/UI baseline.

During pause: security and P0 fixes still ship under §7. A **un-freeze** is a conscious decision (new roadmap increment), not silent feature creep.

The relay repo (`mycena-relay`, see [ECOSYSTEM.md](./ECOSYSTEM.md)) has its own deploy cadence. Client freeze does not automatically freeze relay ops; relay changes that **require** a client protocol bump **are** freeze exceptions.

---

## 10. Tracking

**Do not create a second product roadmap.** Ownership:

| Document | Role after this plan exists |
|----------|------------------------------|
| **This file** | Freeze **gates**, DoD, evidence rules, freeze/unfreeze policy |
| [WORK_REMAINING.md](./WORK_REMAINING.md) | Canonical **work tracker** (Category 1 human/legal, Category 2 hardware, Category 3 software). 2026-07-05 audit: Category 3 software backlog through Iteration E is **marked complete**; remaining work is Cat 1–2. Freeze engineering (G2–G3) is **stabilization**, not a new Cat 3 feature list — add rows there only for freeze defects. |
| [ROADMAP.md](./ROADMAP.md) | Product phase status (Level 0 pointer + short/mid/later). |
| [LEVEL0_ROADMAP.md](./LEVEL0_ROADMAP.md) | Mesh capability phases A–E; A2 manual sign-off is a **G6 P1**. |
| [DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md) | Pocket mesh / DTN / drills; 2.4–2.5 and 4.1 are freeze P1/P2 as above. |
| [PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md) | Play/compliance/external-review **human** gaps. |
| [SECURITY_ROADMAP.md](./SECURITY_ROADMAP.md) / [SECURITY_REMEDIATION_ROADMAP.md](./SECURITY_REMEDIATION_ROADMAP.md) | Security phases; P0 code items largely **done**; do not re-open as freeze features. |
| [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) | Canonical `SD-*` debt. |
| [CI_PUBLIC_TRANSITION.md](./CI_PUBLIC_TRANSITION.md) | Making the **repo** public (G8). |
| [PUBLIC_REPOSITORY_AUDIT.md](./PUBLIC_REPOSITORY_AUDIT.md) | Secrets / license / docs / build hygiene; publication verdict. |
| [mvp-smoke-test.md](./mvp-smoke-test.md) | CLI + Level 0 Phase A/B scripts; G6 still uses A2/B1 rows. |
| [HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md) | How to execute freeze HV-01–20 (Android DoD). |
| [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md) | Empty Pixel + Samsung identity + HV result tables. |
| [CRASH_REPORTING.md](./CRASH_REPORTING.md) | adb logcat / dropbox; logs stay out of git. |

When a freeze gate completes, record evidence and **optionally** add a one-line pointer in WORK_REMAINING (e.g. “Freeze G3: see commit …”) rather than forking a new tracker.

---

## 11. Unresolved documentation contradictions

Discovered while writing this plan. **G1 must dispose each item.** This file does not pick a winner.

| # | Topic | Doc A | Doc B | Why it matters for freeze |
|---|--------|-------|-------|---------------------------|
| C1 | Desktop / Mac BLE | [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md): “Mac/laptop BLE not active yet” | [DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md) baseline: Desktop BLE **Active on macOS/Linux**; [CONNECTIVITY_MODES.md](./CONNECTIVITY_MODES.md) platform table: BLE F1–F3 **Shipped** on desktop | Public honesty; G6 Mac pairing |
| C2 | Desktop BLE stub vs shipped | [CONNECTIVITY_MODES.md](./CONNECTIVITY_MODES.md) Settings table: “Desktop stub logs intent until btleplug GATT ships” | Same file, platform parity: “Shipped (macOS/Linux blew host)”; [LEVEL0_ROADMAP.md](./LEVEL0_ROADMAP.md) B6 **Done** | Internal inconsistency in one connectivity doc |
| C3 | Disaster mesh intro vs table | [DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md) § What works: “Laptops without BLE transport do not extend the mesh yet” | Same doc: Desktop BLE Active | Same |
| C4 | Resource map UI | [DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md) baseline: resource map “UI pending” | Phase 2.5.6: Android/Desktop resource map UI **Done** | Crisis-mode claims |
| C5 | LEVEL0 A2 | [LEVEL0_ROADMAP.md](./LEVEL0_ROADMAP.md): A2 “Done (doc) — **manual sign-off pending**” | [WORK_REMAINING.md](./WORK_REMAINING.md) Cat 1: Manual Phase A smoke matrix still outstanding | G6 cannot be assumed done |
| C6 | Software complete vs share stash | [WORK_REMAINING.md](./WORK_REMAINING.md): Category 3 software “*(none)*” remaining | [WIP-ANDROID-SHARE-STASH.md](./WIP-ANDROID-SHARE-STASH.md): incomplete Android share work **stashed, not committed** | G3 may require unstash or rewrite |
| C7 | Offline share remaining gaps | [OFFLINE_DISTRIBUTION.md](./OFFLINE_DISTRIBUTION.md) describes E2E flow as existing | Same doc “Remaining gaps”: foreground service for long hotspot share; hotspot before node ready | G3 pass criteria vs known gaps |
| C8 | StrongBox | [architecture.md](./architecture.md): “Android Keystore/**StrongBox-backed** master when available” | [SECURE_HARDWARE_STATUS.md](./SECURE_HARDWARE_STATUS.md): StrongBox **not** used for identity; EncryptedSharedPreferences only | Security claims |
| C9 | SD-150 supply chain | [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) SD-150 **missing**: cargo vet, SBOM, reproducible builds, signed releases | [RELEASING.md](../RELEASING.md) / [RELEASE_INTEGRITY.md](./RELEASE_INTEGRITY.md) / [DEPENDENCY_RISK.md](./DEPENDENCY_RISK.md): SBOM, reproducible Android container, M-of-N signing **exist**; cargo-deny in CI | G7 evidence vs stale debt row |
| C10 | SD-060 replay | [SECURITY_ROADMAP.md](./SECURITY_ROADMAP.md): SD-060 **Done** | [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) SD-060 body still lists missing replay window | Register hygiene |
| C11 | AR-02 amplification | [ARCHITECTURE_RISKS.md](./ARCHITECTURE_RISKS.md) (last updated **May 2026**): AR-02 residual “High until SD-031 fixed” | SD-031 **mitigated** (Jul/Aug 2026 notes) | Stale architecture ledger |
| C12 | CHANGELOG | [RELEASING.md](../RELEASING.md) step 2: update `CHANGELOG.md` | No root `CHANGELOG.md` in the repository | G9 process |
| C13 | Fuzz PR duration | [CI_PUBLIC_TRANSITION.md](./CI_PUBLIC_TRANSITION.md): PR smoke **45s × 4** | [FUZZING.md](./FUZZING.md): **90s** | Docs only; use workflow YAML as authority |
| C14 | Threat model version | [THREAT_MODEL.md](./THREAT_MODEL.md) **v1.1** | [EXTERNAL_SECURITY_REVIEW.md](./EXTERNAL_SECURITY_REVIEW.md): pack cites **v1.0** | Reviewer package drift |
| C15 | Play vs GitHub beta | [PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md) / [BETA_SECURITY_ASSESSMENT.md](./BETA_SECURITY_ASSESSMENT.md) Play **conditional OK** with Pages + Data Safety + signed residuals | This freeze (§2.3): GitHub public technical beta **without** requiring Play | Product decision — record in G10 notes |
| C16 | Residuals unsigned | [ACCEPTED_SECURITY_RESIDUALS.md](./ACCEPTED_SECURITY_RESIDUALS.md): empty sign-off; “not a release certificate” | [PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md) Play gate requires residuals **signed** | Play vs GitHub |
| C17 | MeshCoin Phase 0 | [SECURITY_ROADMAP.md](./SECURITY_ROADMAP.md) Phase 0: MeshCoin UX + settlement caps listed without **done** | [SECURITY_REMEDIATION_ROADMAP.md](./SECURITY_REMEDIATION_ROADMAP.md): MeshCoin positioning **Done** (wallet banner) | Preview-only enforcement |
| C18 | Privacy policy template | [PRIVACY_ROADMAP.md](./PRIVACY_ROADMAP.md): `privacy_policy.html` “must be completed before Play Store (contact, address)” | [PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md) / [docs/legal/README.md](./legal/README.md): Pages URL **DO NOW**; in-app policy exists | Play P1 |
| C19 | WORK_REMAINING vs disaster BLE demo | Cat 2: “Mac ↔ Android BLE F1 pairing demo” outstanding | LEVEL0 B6 / CONNECTIVITY desktop BLE **Done/Shipped** | Hardware vs software status |

---

## 12. Proposed next implementation task (do not start in the G0 change)

**Next task: G1 — Repository and architecture audit** (still no feature work): walk the Android host and share/onboarding/panic/BLE paths against this plan, dispose §11 (especially C1–C8, C6 stash vs G3), and produce a dated audit note + WORK_REMAINING freeze rows for any **defects** found.

After G1: **G2 + G3** (Android P0 stability and phone-to-phone distribution on `main`), then G4–G6 hardware, then G7–G10.

Do not implement those in the same change as this document.
