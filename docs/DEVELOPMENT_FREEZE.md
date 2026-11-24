# Feature freeze (policy lock)

**Freeze date:** 2026-08-29
**Status:** **Public Technical Beta — Feature Development Frozen, Release Validation Ongoing** — **with exceptions**
**Canonical policy:** this file
**Evidence (what was not device-proven):** [FINAL_DEVELOPMENT_FREEZE.md](./FINAL_DEVELOPMENT_FREEZE.md)
**Candidate:** [RELEASE_CANDIDATE.md](./RELEASE_CANDIDATE.md) (`0.1.75-rc.1`)

This is a **feature-development policy freeze**: stop feature expansion so the tree does not keep moving. P0 stabilization continues. Hardware validation is **pending**. It is **not** G10, **not** public-release approval, **not** a claim that G2–G9 P0 device/CI evidence passed, **not** a security certification, and **not** a hardware compatibility certificate.

---

## Identity

| Field | Value |
|-------|--------|
| **Freeze date** | 2026-08-29 |
| **versionName / versionCode** | `0.1.75` / `76` |
| **Release candidate** | `0.1.75-rc.1` (local; **not** a git tag; **not** published) |
| **HEAD commit** | `fb32b1e5c89b318e14b1adc2a76b7dcefac6b3ec` |
| **Working tree** | Dirty freeze work may still be uncommitted; the RC APK was built from that dirty tree |
| **Project status** | Early **public technical beta**; **feature development paused**; **release validation ongoing**; G9 **FAIL**; G10 **not approved** |

---

## What is considered stable

**Stable** here means: in-tree, intended for testers, and **not** a target for new product work during the freeze — **not** “proven on phones.”

| Area | Freeze meaning |
|------|----------------|
| Android host + Rust mesh (libp2p, gossip, E2E when keys exist) | In scope; **not** hardware-matrix verified |
| Fail-closed chat without enc key (SD-081), unsigned `Data` drop (SD-030), `allowBackup=false` (SD-171) | Implemented; some unit-tested; **not** independently audited |
| Phone-to-phone share **code** | Present; **not** two-phone verified |
| MeshCoin, mini-apps, WASM, Shade | **Preview / experimental** — no new work |
| Desktop Tauri | Experimental; not a freeze hardware target |
| iOS | Not shipping |

**Verified devices for this freeze:** **Pixel 6 partial** (see [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md)). All other rows remain `unrun`. Do not list “API 26+” or other models as freeze-tested.

---

## Known limitations

Read [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md) and [BETA_SECURITY_ASSESSMENT.md](./BETA_SECURITY_ASSESSMENT.md). In short:

- Not Signal; not anonymous; not a whistleblower or emergency-services product.
- Metadata and relay IPs are visible on default paths.
- MeshCoin is not money.
- BLE pocket mesh is **not hardware-verified**.
- Samsung / One UI is **not hardware-verified**.
- Sideload APKs are untrusted; GitHub CI still produces **debug-signed** APKs; the local RC is a different **release** key.
- No external security audit.

---

## Accepted exceptions (why freeze anyway)

Maintainer lock 2026-08-29: pause **features** even though the technical G10 table was **not** a clean P0 pass. Exceptions (risk accepted for a **policy** freeze only):

| Exception | Risk |
|-----------|------|
| Empty hardware matrix; RC install **UNVERIFIED** | Unknown P0 crashes on real devices |
| No two-phone share record | Offline-distribution claims unproven |
| No Gitleaks job URL; no CI URLs on a tagged SHA | History/CI evidence incomplete |
| Dirty tree vs HEAD; no `v*.*.*` tag | Third parties cannot pin “the freeze APK” to a public tag |
| CI `assembleDebug` vs local `assembleRelease` RC | Mixed signing keys |
| §11 / BLE copy still imperfect | Residual overclaim until docs catch up |
| Panic wipe unproven on device | Seizure/wipe story incomplete |
| External review not commissioned | Unknown classes of vulns |
| Residuals R1–R10 unsigned (GitHub-only) | Not a Play certificate |
| Play Store / Pages out of scope | Separate track |

These exceptions **do not** make unverified hardware “supported.”

---

## Remaining backlog

| Queue | Role |
|-------|------|
| [RELEASE_FREEZE_BACKLOG.md](./RELEASE_FREEZE_BACKLOG.md) | Device/CI/honesty P0s still open — **allowed freeze work** (crashes, security, release-breakers, docs) |
| [POST_FREEZE_BACKLOG.md](./POST_FREEZE_BACKLOG.md) | P2 / Play / iOS / Tor / MLS — **deferred** |
| [WORK_REMAINING.md](./WORK_REMAINING.md) | Human/legal/hardware product tracker (not a feature dump) |

---

## Allowed post-freeze changes

### Allowed (still need review / CI)

- Critical **security** fixes
- **P0 crashes** (reproducible process death / data loss on in-scope flows)
- **Release-breaking** defects (cannot install, integrity broken, secret leaked)
- **Build / reproducibility / CI** defects
- **Documentation corrections** (honesty, limitations, freeze policy)

### Requires explicit written justification

Name: why a freeze exception, risk of not doing it, that it is not a new product bet.

- Dependency upgrades
- Refactors
- API / wire-format changes

### Deferred (do not start)

- New features
- New transports
- Major architectural changes
- Experimental functionality (new MeshCoin product, new mini-app platforms, Shade-on-by-default, etc.)

Un-freeze is a **conscious** roadmap increment, not silent `feat:` commits.

---

## Related

- [RELEASE_FREEZE_PLAN.md](./RELEASE_FREEZE_PLAN.md) §7
- [SECURITY.md](../SECURITY.md) — `github.com/mycena-project/Mycena/security/advisories/new`
- [CONTRIBUTING.md](../CONTRIBUTING.md)
