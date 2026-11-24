# Final development freeze gate (G10)

**Date (UTC):** 2026-08-29
**HEAD:** `fb32b1e5c89b318e14b1adc2a76b7dcefac6b3ec`
**Working tree:** dirty (uncommitted freeze work). Not a tagged SHA.
**Plan:** [RELEASE_FREEZE_PLAN.md](./RELEASE_FREEZE_PLAN.md) §4, §8
**Does not replace:** [RELEASE_FREEZE_BACKLOG.md](./RELEASE_FREEZE_BACKLOG.md), [WORK_REMAINING.md](./WORK_REMAINING.md)

Evidence vocabulary: `PASS` · `FAIL` · `UNVERIFIED`. `UNVERIFIED` is **not** PASS ([§5](./RELEASE_FREEZE_PLAN.md#5-evidence-rules)).

No application code was changed for this gate.

---

## 1. Final status

**Technical evaluation (same day, tables below):** P0 device/CI/honesty items were `FAIL` or `UNVERIFIED`. Under the plan’s “every P0 must PASS” rule, that evaluation was **FREEZE NOT APPROVED**.

**Maintainer policy lock (2026-08-29):** **FREEZE APPROVED WITH EXCEPTIONS.** Feature development is **paused**. Exceptions and risks are listed in [DEVELOPMENT_FREEZE.md](./DEVELOPMENT_FREEZE.md). This lock **does not** rewrite the evidence tables into PASS and **does not** certify hardware or security.

Public label: **Public Technical Beta — Feature Development Frozen, Release Validation Ongoing** — with the exceptions in DEVELOPMENT_FREEZE.md (no verified devices; RC not tagged/published). That label is **not** G10 and **not** public-release approval.

---

## 2. Evidence summary

| Area | Status | Evidence |
|------|--------|----------|
| **Automated tests** | `UNVERIFIED` on freeze SHA / this APK | G7 session ran selected Rust tests (SD-030, SD-081, miniapp sideload) on the dirty tree. No CI job URLs (RFB-TEST-01). `./gradlew testDebugUnitTest` not recorded this gate. Full `check-dependencies.sh` not recorded. |
| **Hardware tests** | `FAIL` | [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md): **all rows `unrun`**. Pixel 6 battery notes are not this matrix. |
| **Samsung tests** | `FAIL` as G4 pass; **P1 exception text exists** | No named Galaxy/One UI row. [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md) states Samsung is not hardware-verified. Static audit only ([SAMSUNG_STABILITY_AUDIT.md](./SAMSUNG_STABILITY_AUDIT.md)). |
| **Phone-to-phone tests** | `FAIL` | No two-phone record. Share **code** is `in_progress` (RFB-P2P-*). G3 requires a physical pair. |
| **Security review** | Internal `PASS` as a dated note; gate `FAIL` | [SECURITY_RELEASE_REVIEW.md](./SECURITY_RELEASE_REVIEW.md): not a certification; no external audit. Secret-scan job URL **missing** (RFB-SEC-04, plan P0). |
| **Repository audit** | `PARTIAL` | [PUBLIC_REPOSITORY_AUDIT.md](./PUBLIC_REPOSITORY_AUDIT.md): **CONDITIONALLY READY** — no tracked secrets found; G8 not complete (Dependabot setting, CI tier, scan URL). |
| **Release build** | Artifact `PASS`; G9 `FAIL` | `0.1.75-rc.1` `assembleRelease`, not debug-signed. Dirty tree. Not tagged. Not published. |
| **Artifact verification** | Hash `PASS`; install `UNVERIFIED`; M-of-N `UNVERIFIED` | SHA-256 `413c35287607f71ee55458266cc1a38557764ba4376d05adedef5aa6bb1a396d`. `apksigner` v2 verified. `adb` had no devices. `release-verify.sh` not run. CI still builds **debug** APKs. |

### Gates G0–G10

| Gate | Status |
|------|--------|
| G0 Plan | `PASS` (document exists) |
| G1 Architecture / §11 | `FAIL` — RFB-DOC-01 still `open` |
| G2 Android stability | `FAIL` — no device cold-start table |
| G3 Phone-to-phone | `FAIL` |
| G4 Samsung | Not passed; public “untested” sentence exists (P1 path b) — **does not waive G2/G3 P0** |
| G5 CI | `UNVERIFIED` |
| G6 Hardware matrix | `FAIL` |
| G7 Security / supply chain | `FAIL` as a gate (internal review page exists; scan + full deny/audit missing) |
| G8 Public repo | `FAIL` / conditional only |
| G9 RC | `FAIL` ([RELEASE_CANDIDATE.md](./RELEASE_CANDIDATE.md)) |
| G10 This gate | Technical tables: **`FAIL`**. Policy lock: **APPROVED WITH EXCEPTIONS** — [DEVELOPMENT_FREEZE.md](./DEVELOPMENT_FREEZE.md) |

### Plan §4.1 P0 (all must PASS)

| P0 requirement | Status | Why |
|----------------|--------|-----|
| No known reproducible P0 crash on in-scope Android flows | `UNVERIFIED` | No logcat / matrix. Absence of reports is not evidence. |
| Phone-to-phone on one recorded pair | `FAIL` | No pair recorded. |
| Permissions requested; denial non-fatal | `UNVERIFIED` | Code `in_progress`; no device deny/grant record. |
| Release artifact installs on each **supported** device | `FAIL` | No supported device (none listed without exception). Install of RC **UNVERIFIED**. |
| Critical security unaddressed (preview) | `PARTIAL` | G7: no new preview Critical. Secret-scan evidence still required by the next P0. MXC stays preview. |
| No secrets in tree; **secret-scan on freeze SHA** | `FAIL` | Tracked files looked clean; **no Gitleaks job URL**. |
| Build/release docs match what CI does | `FAIL` | Local RC is release-signed; `android/build.sh` / `reproducible-build.sh` / GitHub still **debug**. |
| Limitations not contradicted by README / copy | `FAIL` | RFB-DOC-01 open. Example: KNOWN_LIMITATIONS still calls **Android BLE pocket mesh** a “good fit” while BLE-air is `unrun` and RFB-DOC-05 is `in_progress`. |
| Panic wipe + mesh-copies user-visible | `UNVERIFIED` / `FAIL` | RFB-PERS-01 `open`; no device panic row. |
| SD-081 fail-closed | `PASS` (code + unit tests in G7) | Device send path **UNVERIFIED**. |
| SD-030 unsigned `Data` dropped | `PASS` (code + unit tests in G7) | |
| SD-171 `allowBackup=false` | `PASS` (manifest inspection) | |
| SECURITY.md reporting path | `PASS` | `github.com/mycena-project/Mycena/security/advisories/new`; GitHub advisory if enabled. |

**P0 rule (plan):** freeze is **not** a clean P0 pass. **Policy freeze** proceeds only with the exceptions in [DEVELOPMENT_FREEZE.md](./DEVELOPMENT_FREEZE.md).

### Plan §4.2 P1 (PASS or written exception)

| P1 | Status |
|----|--------|
| Hardware matrix complete **or** exceptions in KNOWN_LIMITATIONS | **Not accepted as a full-matrix skip.** Empty matrix with no per-scenario skip-with-disposition. Samsung-untested sentence does **not** cover Pixel/LAN/G3. |
| Samsung G4 | Exception **documented** (untested). Does not unlock freeze. |
| Update / reinstall tested | `UNVERIFIED` — no exception with device disposition |
| `check-dependencies.sh` + allowlist + SBOM | SBOM on RC `PASS`; allowlist dates checked earlier; **full script UNVERIFIED**. No written “skip deny/audit” exception. |
| Internal security review | Dated page exists. Plan checklist numbering: TM **§9**. |
| Residuals unsigned | **Exception documented** 2026-08-29 (GitHub-only, not a certificate). |
| `release-verify` / hash on RC APK | SHA-256/BLAKE3 recorded. M-of-N **UNVERIFIED**. Hash check is the documented substitute for this local APK only. |
| §11 contradictions | `FAIL` (RFB-DOC-01) |
| LEVEL0 A2 sign-off | `FAIL` / pending |
| BLE-air **or** softened claims | Softening **incomplete** (KNOWN_LIMITATIONS “good fit” vs unverified) |
| Privacy Pages URL | Play-only; GitHub freeze may accept in-app + `docs/legal/` — **acceptable exception for Play**, not a G10 pass |
| Play Data Safety | **Accept-as-deferred** (Play out of G10) |
| External review | **Explicitly deferred** 2026-08-29 |
| CHANGELOG vs RELEASING | `PASS` (RFB-DOC-04: no root CHANGELOG required) |
| Desktop BLE claim vs code | Tied to RFB-DOC-01 |
| Dependabot / cargo vet vs SD-150 | File exists; GitHub enablement **UNVERIFIED**; cargo vet still missing (documented) |

P1 is **not** fully PASS-or-excepted. Even if it were, P0 failure still blocks freeze.

---

## 3. Remaining known issues

| ID | Issue | Severity | Impact | Workaround | Blocks normal evaluation? | Post-freeze priority |
|----|--------|----------|--------|------------|---------------------------|----------------------|
| B1 | Hardware matrix empty; no install of RC | P0 | Cannot claim testable beta on devices | Run [HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md) | **Yes** | Freeze-blocking (not post-freeze) |
| B2 | No two-phone share proof | P0 | Offline distribution claim unproven | P2P-MT on two phones | **Yes** | Freeze-blocking |
| B3 | No Gitleaks URL on freeze SHA | P0 | History leak unknown | Green `secret-scan.yml` | **Yes** (public freeze) | Freeze-blocking |
| B4 | No CI job URLs for candidate SHA | P0 | Cannot claim tests passed | RFB-TEST-01 | **Yes** for G5/G9 | Freeze-blocking |
| B5 | Dirty tree; RC ≠ tagged commit | P0/process | Third party cannot rebuild “the” freeze SHA | Commit, tag, rebuild | **Yes** for G9 | Freeze-blocking |
| B6 | CI debug APK vs local release APK | P0 honesty | Testers mix keys; updates fail | Align scripts **or** document GitHub=debug until REL-01 done; RFB-DOC-03 `open` | **Yes** if GitHub is the public artifact | Freeze-blocking |
| B7 | §11 / BLE “good fit” vs unverified | P0 docs | Overclaim | Finish RFB-DOC-01 / DOC-05 | **Yes** for honest public freeze | Freeze-blocking |
| B8 | Panic copy / device wipe unproven | P0 | Seizure/wipe story incomplete | RFB-PERS-01 + HV-18 | **Yes** | Freeze-blocking |
| B9 | R8 minify never smoke-tested on device | High | Possible release-only crash | Install RC; fix if needed | **Yes** for this APK | Freeze-blocking until smoke |
| B10 | Full `check-dependencies.sh` not logged | P1 | Supply-chain gate unrecorded | Run script on freeze SHA | Partial | Freeze P1 then done |
| B11 | LEVEL0 A2 unsigned | P1 | LAN chat unproven | Two-device A2 | **Yes** for “mesh works” | Freeze-blocking as P1 without exception |
| B12 | Play / Pages / external audit | P1 deferred | Not GitHub-freeze blockers if Play stays out | Keep deferred | No for GitHub-only | Post-freeze / Play track |

---

## 4. Accepted limitations

These are **product/threat-model limits**, not freeze P0 bugs, **if** public copy stays honest:

- Not Signal-class metadata privacy; not Tor; not a whistleblower tool ([THREAT_MODEL.md](./THREAT_MODEL.md) §2.1).
- Relay sees IP; mesh copies not centrally erasable (residuals R2, R5, R6).
- MeshCoin is preview utility, not money (R3; SD-011).
- WebView is not a security boundary (R4).
- Group PSK (R9); physical/root device access (TM §5.3).
- Samsung **untested** (limitation, not a test pass).
- Play Store not in this freeze (§2.3).
- Desktop Tauri / Play AAB not fully reproducible ([RELEASE_INTEGRITY.md](./RELEASE_INTEGRITY.md) §7).
- iOS not shipping.

**Not** accepted as “limitations” that waive P0: empty hardware matrix, unproven phone-to-phone, missing secret-scan, unpublished/uninstalled RC, contradictory BLE pocket-mesh copy.

---

## 5. Security statement

Mycena has an **internal** freeze security note ([SECURITY_RELEASE_REVIEW.md](./SECURITY_RELEASE_REVIEW.md)) and a threat model. **No external security audit has been commissioned or completed.**

The project **does not** claim: formal audit, production-grade security, military-grade protection, guaranteed anonymity, or that compromise is impossible.

What is in evidence: selected mitigations exist in code (unsigned `Data` drop, fail-closed chat without enc key, sideload bundle signature, `allowBackup=false`) with **unit tests for some** and **no independent review**. Metadata, relay IP, Sybil MeshCoin, and WebView residuals are **accepted for preview**, not solved.

Report vulnerabilities privately: **github.com/mycena-project/Mycena/security/advisories/new** ([SECURITY.md](../SECURITY.md)).

---

## 6. Public-release readiness

| Item | Ready to freeze-public? | Notes |
|------|-------------------------|--------|
| README | **No** for freeze banner | Honest early-beta language exists. Still points at GitHub Releases / `assembleDebug`. No “development paused.” Freeze overlay not the primary pointer. |
| Documentation | **No** | §11 / DOC-01 open; BLE pocket-mesh “good fit” vs empty matrix. |
| License | **Yes** for clone | AGPL-3.0-or-later `LICENSE`. |
| SECURITY.md | **Yes** for reporting | Email path accurate; GitHub advisories if enabled. |
| CONTRIBUTING.md | **Partial** | Small-team direct-to-main; freeze rules **must not** be announced as in force. |
| Issue reporting | **Yes** | GitHub issues for bugs; security not public. |
| Release artifacts | **No** | Local gitignored APK only; no GitHub Release; CI debug vs RC release. |
| Known limitations | **Partial** | File exists; some rows contradict unverified hardware. |
| Build instructions | **Yes** to attempt a build | [android/README.md](../android/README.md); UniFFI must be regenerated. |

G8 “stranger can clone, read limitations, report vulns, build” is **closer** than G10. Freeze still **not** approved.

---

## 7. Development freeze rules

**These rules are in effect as a policy freeze with exceptions** ([DEVELOPMENT_FREEZE.md](./DEVELOPMENT_FREEZE.md)). They do **not** mean G2–G9 P0 evidence passed.

Copied from [RELEASE_FREEZE_PLAN.md](./RELEASE_FREEZE_PLAN.md) §7:

**Allowed:** critical security fixes; P0 crash fixes; release-breaking fixes (install, integrity, leaked secret); build/CI/reproducibility fixes; documentation corrections.

**Deferred / not allowed without written justification:** new features; architectural redesigns; speculative optimizations; major UX; new protocols/transports; product expansion (iOS ship, Play listing, MeshCoin as money).

Until G10 **policy** lock: freeze **feature** work is paused; **device/CI honesty P0s** remain allowed (see DEVELOPMENT_FREEZE.md). Do not treat this file as permission to add features.

---

## 8. Post-freeze backlog

Non-blocking (P2 and Play-track) work: [POST_FREEZE_BACKLOG.md](./POST_FREEZE_BACKLOG.md).

**Freeze-blocking work stays** in [RELEASE_FREEZE_BACKLOG.md](./RELEASE_FREEZE_BACKLOG.md) until those P0s PASS or are explicitly accepted under the plan. It is **not** moved off the freeze queue by this gate.

---

## 9. Recommended public messaging

State **Public Technical Beta — Feature Development Frozen, Release Validation Ongoing** together with: expect bugs; **no verified freeze devices**; not certified; not Signal; hardware validation pending; G9 **FAIL**; G10 **not approved**. Pointer: [DEVELOPMENT_FREEZE.md](./DEVELOPMENT_FREEZE.md).

Do **not** say hardware was validated, G9 published, or that P0 device gates passed.

### Release page

**Do not publish** a GitHub Release that implies G9 passed. Private APK: `0.1.75-rc.1`, hashes in RELEASE_CANDIDATE.md, not Play-signed, not M-of-N official, not device-validated.

### Technical beta description

Mycena is an **early public technical beta**. Feature work is **paused**. LAN and nearby-device code exists. **No freeze hardware-matrix pass is recorded.** Sideload is untrusted-by-default. MeshCoin is not money. Security is **not** independently audited.

---

## Technical blockers that remain (policy freeze does not clear them)

1. Plan §4.1: several P0s are still not PASS (G2/G3/G6, secret-scan URL, docs/CI honesty, panic device, empty supported-device list).
2. Plan §8 hardware/G8/G9/G1 still not passed as evidence.
3. [RELEASE_CANDIDATE.md](./RELEASE_CANDIDATE.md): G9 still **FAIL** (no install, no tag, dirty tree).

Closing those is **allowed freeze work** (crashes, security, release-breakers, docs, device evidence). It is **not** a reason to add features.

---

## Policy vs plan P0 rule

The plan said P1 exceptions cannot skip P0. The 2026-08-29 **policy lock** is **FREEZE APPROVED WITH EXCEPTIONS** anyway: pause features while those P0s stay open and listed. That is documented risk, not a rewrite of the tables above into PASS.
