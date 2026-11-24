# Mycena Release Freeze Audit (G1)

**Audit date:** 2026-08-29
**Repository SHA audited:** `fb32b1e5c89b318e14b1adc2a76b7dcefac6b3ec` (`fb32b1e Free disk on rust-budget so hosted runners can link tests.`)
**Plan:** [RELEASE_FREEZE_PLAN.md](./RELEASE_FREEZE_PLAN.md)
**Nature:** Audit only. No application changes. No gate is marked complete merely because a document claims the work shipped.
**This clone:** no `v*.*.*` git tags present; `git stash list` still contains `stash@{0}: On main: wip: android share`.

Status vocabulary: `PASS` · `PARTIAL` · `FAIL` · `UNVERIFIED` · `NOT APPLICABLE` · `DEFERRED`.

---

## How to read this file

- **PASS** means this auditor saw **in-repo evidence** (source, test, CI YAML, ignore rules) that the requirement is met. It does **not** mean a device was run.
- **UNVERIFIED** means implementation exists but freeze evidence rules require runtime/device/CI-job URLs that are **not** in this tree.
- **FAIL** means a missing required implementation, a known defect, or required evidence that cannot exist yet (empty hardware matrix, no RC).
- Documentation checkmarks (LEVEL0 “Done”, WORK_REMAINING Category 3 empty) are **not** treated as PASS.

---

## 1. Release gates

### AUD-G0 — Documentation / planning

| Field | Value |
|-------|--------|
| **Requirement** | Freeze overlay exists and maps to existing trackers. |
| **Status** | `PASS` |
| **Evidence** | `docs/RELEASE_FREEZE_PLAN.md` dated 2026-08-29; §10 points at WORK_REMAINING, PRE_RELEASE_GAPS, LEVEL0, etc.; §11 lists contradictions. |
| **Relevant files** | `docs/RELEASE_FREEZE_PLAN.md` |
| **Risk** | Low. Plan is not yet linked from README. |
| **Recommended action** | Add a README / WORK_REMAINING pointer (docs-only). |
| **Priority** | P2 |
| **Blocks freeze** | No |

### AUD-G1 — Repository and architecture audit

| Field | Value |
|-------|--------|
| **Requirement** | Dated inventory; §11 contradictions disposed or explicitly kept. |
| **Status** | `PARTIAL` |
| **Evidence** | This file is the audit note. Crate map matches [architecture.md](./architecture.md) (13 workspace crates under `crates/`; **no** `mycena-android` crate despite `android-ci.yml` path filter). Share **is on `main`** (see §4); stash `wip: android share` still exists — C6 is **not** “share only in stash.” §11 items are **not** disposed (no owner/decision). Layer-boundary script exists: `scripts/check-layer-boundaries.sh` (CI invokes it; this audit did not re-run it). |
| **Relevant files** | this file; `docs/architecture.md`; `.github/workflows/android-ci.yml`; `docs/WIP-ANDROID-SHARE-STASH.md` |
| **Risk** | Public docs still contradict BLE/share/StrongBox. |
| **Recommended action** | Dispose each §11 row (fix doc, fix code, or accept). Diff stash vs `main` without applying it as a feature dump. |
| **Priority** | P0 (honesty) |
| **Blocks freeze** | Yes, until §11 has dispositions (plan G1 pass criteria). |

### AUD-G2 — Critical Android stabilization

| Field | Value |
|-------|--------|
| **Requirement** | No known reproducible P0 crash on in-scope flows; permissions denial non-fatal; device logs. |
| **Status** | `FAIL` |
| **Evidence** | **No** device logcat, models, or pass/fail table in-repo. Instrumentation is only `MeshServiceInstrumentationTest` (manifest declaration). Identified **crash/hang risks** in §5 (API 26–32 Wi-Fi Direct `registerReceiver` flags overload; `MeshService` init failure leaves `nodeReady=false` forever). Android unit tests exist (`android/app/src/test/...`) but this audit **did not run** `./gradlew testDebugUnitTest`. |
| **Relevant files** | `android/app/src/main/java/io/github/mycena_project/app/MeshService.kt`; `MainActivity.kt`; `bootstrap/WifiDirectShareGroupHelper.kt`; `android/app/src/androidTest/.../MeshServiceInstrumentationTest.kt` |
| **Risk** | Shipping without device evidence; possible P0 crash on share fallback. |
| **Recommended action** | Fix identified crash paths; then record G2 device matrix. |
| **Priority** | P0 |
| **Blocks freeze** | Yes |

### AUD-G3 — Phone-to-phone distribution

| Field | Value |
|-------|--------|
| **Requirement** | Two-phone E2E share/install/join without Play/desktop/Internet; hashes recorded. |
| **Status** | `FAIL` |
| **Evidence** | Implementation **is committed** on `main` (not stash-only). **No** two-device record, models, or APK hashes. Several implementation defects make E2E **unlikely** on modern Android without further work (see §4: permissions, Downloads import, IP fallback, node-not-ready sidecar). Unit tests cover HTTP header strings and Wi-Fi QR escaping only (`BootstrapTests.kt`), not hotspot/server/install. |
| **Relevant files** | `ShareAppViewModel.kt`; `LocalHotspotHelper.kt`; `WifiDirectSharer.kt`; `WifiDirectShareGroupHelper.kt`; `OfflineBootstrap.kt`; `docs/OFFLINE_DISTRIBUTION.md` |
| **Risk** | Public “no Play Store needed” claim overstates a path that is unproven and partly broken. |
| **Recommended action** | Fix P2P defects in §4; then hardware-prove one pair. |
| **Priority** | P0 |
| **Blocks freeze** | Yes |

### AUD-G4 — Samsung / OEM robustness

| Field | Value |
|-------|--------|
| **Requirement** | Named Samsung results **or** written “Samsung untested” exception in public limitations. |
| **Status** | `FAIL` |
| **Evidence** | Zero Samsung/One UI strings in Kotlin. No battery-optimization / ignore-battery intents. `WifiPauseHelper` comments “some OEMs.” [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md) does **not** say Samsung untested. Pixel 6 battery notes are not Samsung. |
| **Relevant files** | `WifiPauseHelper.kt`; `docs/BATTERY_PROFILING_RESULTS.md` |
| **Risk** | One UI FGS kill / hotspot `INCOMPATIBLE_MODE` without documented fallback success. |
| **Recommended action** | Test one Samsung phone **or** add explicit public exception. |
| **Priority** | P1 (plan: blocks G4 pass; P1-unless-accepted) |
| **Blocks freeze** | Yes, unless accepted as P1 exception in KNOWN_LIMITATIONS |

### AUD-G5 — Automated regression coverage

| Field | Value |
|-------|--------|
| **Requirement** | Recorded CI on freeze SHA: Rust, Android unit tests, fuzz-pr, secret-scan; unwrap gates. |
| **Status** | `UNVERIFIED` |
| **Evidence** | Workflows **exist**: `.github/workflows/ci.yml` (fmt, unwrap scripts, `check-dependencies.sh`, clippy, nextest, MycenaLab `sim_ci_gates`, SD-030-related security tests on budget job), `android-ci.yml` (assemble via `android/build.sh` + `testDebugUnitTest`), `fuzz-pr.yml` (`FUZZ_SECONDS=45`, four targets — **YAML is authority**, not FUZZING.md’s 90s), `secret-scan.yml` (gitleaks). Android CI is **path-filtered** unless `MYCENA_CI_TIER=full`. **No** job URL or log for SHA `fb32b1e` is in this tree. This audit did **not** run tests. |
| **Relevant files** | `.github/workflows/ci.yml`; `android-ci.yml`; `fuzz-pr.yml`; `secret-scan.yml` |
| **Risk** | Docs-only PRs skip Rust/Android; freeze SHA might never have been Android-built. |
| **Recommended action** | Record CI URLs for the freeze candidate; set `MYCENA_CI_TIER=full` when public. |
| **Priority** | P0 (evidence) / P1 (full tier) |
| **Blocks freeze** | Yes until a freeze SHA has recorded CI |

### AUD-G6 — Hardware validation

| Field | Value |
|-------|--------|
| **Requirement** | Filled matrix (plan §5.1); A2 manual sign-off; BLE-air P1. |
| **Status** | `FAIL` |
| **Evidence** | Template in plan is empty. [LEVEL0_ROADMAP.md](./LEVEL0_ROADMAP.md) A2: “Done (doc) — **manual sign-off pending**.” [WORK_REMAINING.md](./WORK_REMAINING.md) still lists Mac+Pixel matrix and BLE airplane drill. Only hardware datapoint: Pixel 6 battery ([BATTERY_PROFILING_RESULTS.md](./BATTERY_PROFILING_RESULTS.md)) — not chat/share/panic. |
| **Relevant files** | `docs/mvp-smoke-test.md`; `docs/LEVEL0_ROADMAP.md`; `docs/WORK_REMAINING.md`; `docs/BATTERY_PROFILING_RESULTS.md` |
| **Risk** | Pocket-mesh and LAN claims remain marketing without evidence. |
| **Recommended action** | Execute matrix; do not tick LEVEL0 A2 until signed. |
| **Priority** | P0/P1 per plan |
| **Blocks freeze** | Yes |

### AUD-G7 — Security / dependency / supply-chain review

| Field | Value |
|-------|--------|
| **Requirement** | Internal review; P0 mitigations not regressed; dependencies; residuals signed or deferred; secret scan. |
| **Status** | `PARTIAL` |
| **Evidence** | Registers exist. SD-030 tests in `crates/mycena-node/tests/sd030_unsigned_drop.rs`. SD-081 fail-closed in `crates/mycena-app/src/node.rs` + Android `ChatViewModel.sendChat` uses `sendChatEncrypted`. SD-171: `android:allowBackup="false"` + `backup_rules.xml`. SD-041 mini-app tests exist (not re-run). [ACCEPTED_SECURITY_RESIDUALS.md](./ACCEPTED_SECURITY_RESIDUALS.md) **unsigned**. Advisory allowlist `review_by` **2026-09-30** (future as of audit date 2026-08-29 — not expired yet). `cargo vet` **absent**. Dependabot **absent**. SBOM/reproducible **scripts and release.yml exist**; SD-150 row still says they are missing. External review **not commissioned**. Secret-scan workflow exists; no log for this SHA. |
| **Relevant files** | `docs/SECURITY_DEBT_REGISTER.md`; `deny.toml`; `docs/DEPENDENCY_ADVISORY_ALLOWLIST.tsv`; `ACCEPTED_SECURITY_RESIDUALS.md` |
| **Risk** | Stale debt rows; unsigned residuals; supply-chain story inconsistent. |
| **Recommended action** | Snapshot register; run `check-dependencies.sh` and record output; sign or explicitly defer residuals for GitHub-only beta; refresh SD-150. |
| **Priority** | P1 |
| **Blocks freeze** | Yes for unaddressed Critical **preview** debt; residuals unsigned is P1-unless-accepted |

### AUD-G8 — Public repository hygiene

| Field | Value |
|-------|--------|
| **Requirement** | Safe to set Public; no secrets; license; security contact; CI_PUBLIC_TRANSITION checklist. |
| **Status** | `PARTIAL` |
| **Evidence** | `LICENSE` (AGPL) present. `SECURITY.md` has `github.com/mycena-project/Mycena/security/advisories/new`. `.gitignore` covers `.env`, `*.jks`, `android/local.properties`, `secrets/`. `android/local.properties` exists **locally** (SDK path) and is **not** git-tracked (`git ls-files` empty). Play workflow references **GitHub secrets names only** (not values). [CI_PUBLIC_TRANSITION.md](./CI_PUBLIC_TRANSITION.md) checkboxes **unchecked**. No `.github/dependabot.yml` despite SD-150 “re-enable at public launch.” Git stash with unpublished WIP is **local-only** (not in the published tree) but maintainers must not `stash apply` secrets. Fly relay hostname appears in README as **public** bootstrap (`mycena-relay.fly.dev`) — expected, not a credential. |
| **Relevant files** | `LICENSE`; `SECURITY.md`; `.gitignore`; `docs/CI_PUBLIC_TRANSITION.md` |
| **Risk** | Private CI budget left on after going public; Dependabot never enabled. |
| **Recommended action** | Fill CI_PUBLIC_TRANSITION on the day of; add or explicitly reject Dependabot; do not publish stash contents blindly. |
| **Priority** | P0 (secrets) / P1 (checklist) |
| **Blocks freeze** | Secrets would block; current tree looks clean of tracked secrets. Checklist incomplete blocks G8 **pass**. |

### AUD-G9 — Release candidate

| Field | Value |
|-------|--------|
| **Requirement** | Tagged/pinned RC; APK; hashes; changelog or RELEASING amended. |
| **Status** | `FAIL` |
| **Evidence** | This clone has **no** `v*.*.*` tags. `android/build.sh` and `scripts/reproducible-build.sh` copy **`app-debug.apk`**. `release.yml` body: “reproducible **debug-signed** sideload.” `version.properties`: `versionCode=76` `versionName=0.1.75`. **No** root `CHANGELOG.md`. Integrity JSON is produced **unsigned** in CI; official = M-of-N later. |
| **Relevant files** | `RELEASING.md`; `.github/workflows/release.yml`; `scripts/reproducible-build.sh`; `android/build.sh`; `android/version.properties` |
| **Risk** | Users install a debug-signed APK labeled as GitHub Release; Play signing is a different key. |
| **Recommended action** | Decide: debug-signed sideload is the freeze artifact (document loudly) **or** add a real release-signed universal APK. Add CHANGELOG or drop the RELEASING step. Cut a tag only after G2/G3. |
| **Priority** | P0 |
| **Blocks freeze** | Yes |

### AUD-G10 — Final public-release gate

| Field | Value |
|-------|--------|
| **Requirement** | G0–G9 pass or named exceptions; docs match reality. |
| **Status** | `FAIL` |
| **Evidence** | G0 PASS only. Exit criteria in plan §8 unmet. |
| **Relevant files** | `docs/RELEASE_FREEZE_PLAN.md` §8 |
| **Risk** | Premature “development paused” announcement. |
| **Recommended action** | Do not declare freeze. Follow §12 order. |
| **Priority** | P0 |
| **Blocks freeze** | Yes |

---

## 2. In-scope stability (plan §2.1)

### AUD-S01 — Android application startup

| Field | Value |
|-------|--------|
| **Requirement** | Fresh install launches; `MeshService` FGS; Rust node initializes. |
| **Status** | `UNVERIFIED` |
| **Evidence** | `MainActivity` starts FGS then waits on `MeshService.isNodeReady()`. `MeshService.onStartCommand` calls `startForeground` then `mycena.initNode(...)`. Init `catch` logs and returns `START_STICKY` **without** setting `nodeReady` → UI **spins forever** (hang, not crash). No device proof. |
| **Relevant files** | `MainActivity.kt`; `MeshService.kt` |
| **Risk** | Failed native init looks like a freeze. FGS without notification permission on API 33+ may still start; notification may be hidden. |
| **Recommended action** | Surface init failure in UI; record two successful cold starts on two API levels. |
| **Priority** | P0 |
| **Blocks freeze** | Yes (unverified + hang path) |

### AUD-S02 — Onboarding

| Field | Value |
|-------|--------|
| **Requirement** | First-launch flow; sideload skip via `OfflineBootstrap`. |
| **Status** | `PARTIAL` |
| **Evidence** | `OnboardingActivity` + `ui-copy/onboarding`. Completing onboarding calls `LocalDataWiper.wipeAllLocalData` then writes display name — **destroys** any prior settings. `OfflineBootstrap.applySideloadAutoOnboarding` sets `onboarding_complete` if Downloads import succeeds. Import is **likely broken on API 29+** (see AUD-P2P-17). Copy unit tests exist. |
| **Relevant files** | `OnboardingActivity.kt`; `OfflineBootstrap.kt`; `ui-copy/onboarding/` |
| **Risk** | Sideload recipients always hit manual onboarding; mesh join credentials never applied. |
| **Recommended action** | Fix sidecar delivery into app-accessible storage; do not wipe imported bootstrap on first run. |
| **Priority** | P0 for skip-path; P1 for copy polish |
| **Blocks freeze** | Sideload skip is part of G3 — yes if G3 required |

### AUD-S03 — Core application flow

| Field | Value |
|-------|--------|
| **Requirement** | Nav: Connect / Chat / Peers / Settings / Share; QR v3; connectivity toggles. |
| **Status** | `UNVERIFIED` |
| **Evidence** | `MainShell.kt` routes include `share_app`. Settings connectivity keys exist. No device matrix. |
| **Relevant files** | `ui/MainShell.kt`; `CONNECTIVITY_MODES.md` |
| **Risk** | Doc/code drift on BLE/desktop (C1–C3). |
| **Recommended action** | Manual A1–A4 on two phones. |
| **Priority** | P0 |
| **Blocks freeze** | Yes until verified |

### AUD-S04 — Mesh networking (LAN / QR)

| Field | Value |
|-------|--------|
| **Requirement** | Two devices LAN mDNS or QR; chat without relay. |
| **Status** | `UNVERIFIED` |
| **Evidence** | Rust node + mDNS in architecture; LEVEL0 A2 **unsigned**. CLI smoke documented in `mvp-smoke-test.md`, not executed here. |
| **Relevant files** | `docs/mvp-smoke-test.md`; `docs/LEVEL0_ROADMAP.md` |
| **Risk** | Core value proposition unproven in freeze evidence. |
| **Recommended action** | G6 A2-1 / B1. |
| **Priority** | P0 |
| **Blocks freeze** | Yes |

### AUD-S05 — BLE transport (Android F1–F3)

| Field | Value |
|-------|--------|
| **Requirement** | BLE mesh on hardware matrix devices. |
| **Status** | `UNVERIFIED` |
| **Evidence** | Substantial Kotlin: `BleBootstrapAdvertiser`, `BleBootstrapScanner` (null adapter → **return**, no crash), `BleInviteGattServer`, F2/F3 relay. Permissions requested from **Settings** BLE toggle, not at share/startup. Scanner unit tests for UUID. **No** two-phone BLE-air record. Desktop BLE **code exists** (`crates/mycena-desktop/src/ble/host.rs`, `blew` crate) — contradicts KNOWN_LIMITATIONS “Mac BLE not active” (C1). |
| **Relevant files** | `MeshService.kt` `updateBleBootstrap`; `docs/adr/002-ble-mesh-transport.md` |
| **Risk** | README implies pocket mesh; WORK_REMAINING still wants BLE drill. |
| **Recommended action** | Device drill **or** soften user-facing BLE claims. |
| **Priority** | P1 (plan) |
| **Blocks freeze** | P1-unless-accepted |

### AUD-S06 — Wi-Fi transport (LAN + share hotspot/Direct)

| Field | Value |
|-------|--------|
| **Requirement** | Same-subnet mDNS; hotspot / Wi-Fi Direct for share. |
| **Status** | `PARTIAL` |
| **Evidence** | Share stack implemented. Hotspot/Direct **unverified** on device. Wi-Fi Aware / WebRTC hosts exist (`WifiAwareDiscoveryHost`, `WebRtcDiscoveryHost`) — **DEFERRED** as freeze transports (plan §2.2) but still run from `MeshService` when BLE bootstrap is on (Aware) / internet on (WebRTC). Field validation of Aware/WebRTC is out of scope; they remain crash surface. |
| **Relevant files** | `LocalHotspotHelper.kt`; `WifiDirectShareGroupHelper.kt`; `WifiAwareDiscoveryHost.kt` |
| **Risk** | Extra transports increase OEM crash surface during freeze. |
| **Recommended action** | Prove hotspot + Direct on G3 devices; keep Aware/WebRTC experimental. |
| **Priority** | P0 for share Wi-Fi |
| **Blocks freeze** | Share path yes |

### AUD-S07 — Messaging (E2E fail-closed)

| Field | Value |
|-------|--------|
| **Requirement** | Encrypted 1:1 when keys known; no plaintext send; reconnect without re-scan. |
| **Status** | `PARTIAL` |
| **Evidence** | `sendChatEncrypted` only in `ChatViewModel`. Rust `enc_key_not_yet_exchanged` tests in `mycena-app`. Reconnect (A2 #5) **not** device-tested. |
| **Relevant files** | `ChatViewModel.kt`; `crates/mycena-app/src/node.rs` |
| **Risk** | Regression only caught if CI runs those tests on freeze SHA. |
| **Recommended action** | Confirm CI includes app crate tests; device A2-5. |
| **Priority** | P0 |
| **Blocks freeze** | Unverified reconnect yes; fail-closed code is not FAIL |

### AUD-S08 / AUD-S09 — Phone-to-phone and offline APK install

See **§4** (detailed). Overall: `FAIL` for freeze (no E2E evidence + blocking defects).

### AUD-S10 — Persistence

| Field | Value |
|-------|--------|
| **Requirement** | Contacts, keys, chat survive restart. |
| **Status** | `UNVERIFIED` |
| **Evidence** | sled path `filesDir/mycena`; `StorageKeyHelper` EncryptedSharedPreferences + `setRequestStrongBoxBacked(true)` with software fallback. Identity keys still file-based (`.secrets/*.enc`). No restart test record. |
| **Relevant files** | `StorageKeyHelper.kt`; `IdentityHelper.kt`; `docs/SECURE_HARDWARE_STATUS.md` |
| **Risk** | C8 docs vs code: StrongBox **is requested for MasterKey**, not identity keys. |
| **Recommended action** | Device restart test; align C8 docs. |
| **Priority** | P0 |
| **Blocks freeze** | Yes until verified |

### AUD-S11 — Recovery / restart / OEM kill

| Field | Value |
|-------|--------|
| **Requirement** | Force-quit recovery; OEM FGS kill characterized (esp. Samsung). |
| **Status** | `FAIL` |
| **Evidence** | `START_STICKY` set. No ignore-battery-optimizations UX. No Samsung data. |
| **Relevant files** | `MeshService.kt` |
| **Risk** | Mesh silently dead after OEM sleep. |
| **Recommended action** | G4 idle test; optional battery exemption UX (careful with Play policy). |
| **Priority** | P1 |
| **Blocks freeze** | G4/G6 |

### AUD-S12 — Panic / wipe

| Field | Value |
|-------|--------|
| **Requirement** | Local wipe of messages, identity, ledger; UI discloses mesh copies remain. |
| **Status** | `PARTIAL` |
| **Evidence** | `LocalDataWiper.wipeAllLocalData` stops node, deletes DB dir (retry 5×), clears prefs, `StorageKeyHelper.clear`. CLI `mycena panic` uses `wipe_node_directory`. Confirm dialog: “cannot be undone” — **does not** say copies on other devices persist. KNOWN_LIMITATIONS has that sentence; **Settings UI does not**. If delete fails after retries, code **logs and continues** (user may think wipe succeeded). |
| **Relevant files** | `LocalDataWiper.kt`; `SettingsScreen.kt` `PanicWipeSection`; `values/strings.xml` |
| **Risk** | False sense of remote erasure; incomplete wipe on file lock. |
| **Recommended action** | Add mesh-copies sentence to panic UI; fail the flow if DB still exists; device-test. |
| **Priority** | P0 (disclosure) |
| **Blocks freeze** | Yes (plan P0: limitation user-visible) |

### AUD-S13 — Update / reinstall

| Field | Value |
|-------|--------|
| **Requirement** | `adb install -r` / update; data keep vs wipe documented and tested. |
| **Status** | `UNVERIFIED` |
| **Evidence** | `IdentityHelper.hasExistingIdentity` + `WelcomeBackActivity` suggest reinstall-with-data is considered. No written keep/wipe matrix. `allowBackup=false` means uninstall loses data (expected). Same-key debug reinstall typically keeps data. |
| **Relevant files** | `IdentityHelper.kt`; `WelcomeBackActivity` (not fully re-read) |
| **Risk** | Signing-key change (debug vs Play) prevents update and looks like a new app. |
| **Recommended action** | Document debug vs Play keys; test `install -r`. |
| **Priority** | P1 |
| **Blocks freeze** | P1-unless-accepted |

### AUD-S14 — Release artifact integrity

| Field | Value |
|-------|--------|
| **Requirement** | Tag build + SUMS + integrity JSON; third-party hash verify. |
| **Status** | `PARTIAL` |
| **Evidence** | `release.yml` + `release_integrity.rs` + `scripts/release-verify.sh` **exist**. Artifact is **debug APK renamed**. M-of-N not in CI (by design). No tag in this clone. |
| **Relevant files** | `docs/RELEASE_INTEGRITY.md`; `.github/workflows/release.yml` |
| **Risk** | “Official” vs debug-signed confusion. |
| **Recommended action** | G9 decision on signing; never call debug APK official without M-of-N **and** honest notes. |
| **Priority** | P0 |
| **Blocks freeze** | Yes for RC |

---

## 3. Definition of Done (plan §4)

### P0 — Mandatory

| ID | Requirement | Status | Evidence | Blocks |
|----|-------------|--------|----------|--------|
| AUD-P0-01 | No known reproducible P0 crash | `FAIL` | Crash/hang paths in §5; no device sign-off | Yes |
| AUD-P0-02 | Phone-to-phone works on one recorded pair | `FAIL` | No pair record; defects in §4 | Yes |
| AUD-P0-03 | Permissions requested; denial non-fatal | `PARTIAL` | Notifications in `MainActivity`; BLE in Settings; **Share requests none**. Camera in Peers. No install-unknown-apps in **this** app (recipient is browser). Denial of BLE skips bootstrap (log), does not crash. | Yes |
| AUD-P0-04 | Release artifact installs on supported matrix devices | `FAIL` | Empty matrix; APK is debug | Yes |
| AUD-P0-05 | No unaddressed critical security issue (preview product) | `PARTIAL` | P0 network items have tests/code; MXC preview copy in `wallet_experimental_notice`; residuals unsigned; no new cert | P1/P0 mix |
| AUD-P0-06 | No secrets in published tree | `PASS` | gitignore + ls-files; secret-scan workflow; no tracked `.env`/jks | No* |
| AUD-P0-07 | Build/release docs match CI | `PARTIAL` | RELEASING vs debug APK vs missing CHANGELOG (C12) | Yes |
| AUD-P0-08 | Limitations not contradicted | `FAIL` | Plan §11 C1–C19 still open | Yes |
| AUD-P0-09 | Panic wipe + mesh-copies user-visible | `PARTIAL` | Wipe code yes; UI missing copies sentence | Yes |
| AUD-P0-10 | SD-081 fail-closed | `PASS` | Code + unit tests (not re-run) | No |
| AUD-P0-11 | SD-030 unsigned Data dropped | `PASS` | `sd030_unsigned_drop.rs` (not re-run) | No |
| AUD-P0-12 | SD-171 allowBackup false | `PASS` | Manifest + `backup_rules.xml` | No |
| AUD-P0-13 | Security reporting path | `PASS` | `SECURITY.md` | No |

\*PASS for *tracked* secrets. History not scanned in this audit (`secret-scan` needs CI log).

### P1 — Unless accepted

| ID | Requirement | Status | Blocks if not accepted |
|----|-------------|--------|------------------------|
| AUD-P1-01 | Hardware matrix | `FAIL` | Yes |
| AUD-P1-02 | Samsung / OEM | `FAIL` | Yes |
| AUD-P1-03 | Update/reinstall tested | `UNVERIFIED` | Yes |
| AUD-P1-04 | Dependency/license review recorded | `UNVERIFIED` | Yes |
| AUD-P1-05 | Internal security review vs TM §8 | `FAIL` (no dated review note besides this audit) | Yes |
| AUD-P1-06 | Residuals signed or deferred | `FAIL` (unsigned, not deferred in writing for freeze) | Yes |
| AUD-P1-07 | RC integrity verify | `FAIL` (no RC) | Yes |
| AUD-P1-08 | §11 doc consistency | `FAIL` | Yes |
| AUD-P1-09 | LEVEL0 A2 sign-off | `FAIL` | Yes |
| AUD-P1-10 | BLE-air or softened claims | `FAIL` | Yes |
| AUD-P1-11 | Privacy Pages URL | `UNVERIFIED` | Play only; GitHub freeze P1-unless-accepted |
| AUD-P1-12 | Play Data Safety | `DEFERRED` | No (plan §2.3) |
| AUD-P1-13 | External review scheduled/deferred | `FAIL` (pack exists, not scheduled in-repo) | P1 |
| AUD-P1-14 | CHANGELOG or RELEASING fix | `FAIL` | Yes |
| AUD-P1-15 | Desktop BLE claim vs code | `FAIL` (contradiction) | Yes |
| AUD-P1-16 | Dependabot / cargo vet vs SD-150 | `FAIL` | P1 |

### P2 — Deferred (spot-check: still deferred unless promoted)

| ID | Item | Status |
|----|------|--------|
| AUD-P2-01 | iOS ship | `DEFERRED` (`ios/` scaffold) |
| AUD-P2-02 | Tor/Nym | `DEFERRED` |
| AUD-P2-03 | MLS | `DEFERRED` |
| AUD-P2-04 | Hardware-bound identity keys | `DEFERRED` (MasterKey StrongBox requested only) |
| AUD-P2-05 | Shade production | `DEFERRED` |
| AUD-P2-06 | Multi-operator bootstrap must-ship | `DEFERRED` |
| AUD-P2-07 | Extra mini-app red-team | `DEFERRED` |
| AUD-P2-08 | 520-node live soak / 10–50 drill | `DEFERRED` |
| AUD-P2-09 | Wi-Fi Aware / WebRTC **field** validation | `DEFERRED` (code present) |
| AUD-P2-10 | Auto GPS geohash | `DEFERRED` |
| AUD-P2-11 | MeshCoin BFT | `DEFERRED` |
| AUD-P2-12 | Byte-identical APK zip | `DEFERRED` |
| AUD-P2-13 | Desktop Tauri reproducible | `DEFERRED` |
| AUD-P2-14 | Play listing | `DEFERRED` |
| AUD-P2-15 | OSS-Fuzz submit | `DEFERRED` |
| AUD-P2-16 | Native proximity UI | `DEFERRED` |

**Promotion watch:** if BLE never works on any phone, AUD-P2-09 stays deferred but AUD-S05/P1-10 must not claim pocket mesh.

---

## 4. Phone-to-phone (detailed)

Share **is implemented on `main`**. The 2026-08-01 stash note is **stale as a “not committed” claim** for the files below; `stash@{0} wip: android share` may still contain **additional** uncommitted deltas (not inspected; do not apply blindly).

### AUD-P2P-01 — Runtime permissions

| Field | Value |
|-------|--------|
| **Requirement** | Location / Nearby Wi-Fi / Bluetooth as required by API level before hotspot/Direct. |
| **Status** | `FAIL` |
| **Evidence** | `ShareAppScreen` starts hotspot with **no** permission launcher. Manifest has `ACCESS_FINE_LOCATION`, `NEARBY_WIFI_DEVICES`, `CHANGE_WIFI_STATE`. BLE permissions are a **different** Settings flow. `LocalOnlyHotspot` on API 26–32 typically requires **fine location + location enabled**; API 33+ typically `NEARBY_WIFI_DEVICES`. |
| **Relevant files** | `ShareAppScreen.kt`; `AndroidManifest.xml`; `BleBootstrapPermissions.kt` |
| **Risk** | Hotspot fails (`TETHERING_DISALLOWED` / `GENERIC_ERROR` / `SecurityException`); user sees generic error. |
| **Recommended action** | Request the right permissions on Share start; explain denial. |
| **Priority** | P0 |
| **Blocks freeze** | Yes |

### AUD-P2P-02 — Android API requirements

| Field | Value |
|-------|--------|
| **Requirement** | minSdk 26 paths compile and do not use API 33-only methods unsafely. |
| **Status** | `FAIL` |
| **Evidence** | `minSdk = 26`, `compileSdk = 34`. `WifiDirectShareGroupHelper.registerReceiver(..., Context.RECEIVER_NOT_EXPORTED)` uses `Context.registerReceiver(BroadcastReceiver, IntentFilter, Int)` added in **API 33**. On API 26–32 this is `NoSuchMethodError` when Direct fallback runs. `getParcelableExtra` API 33 branch is correctly version-gated. |
| **Relevant files** | `WifiDirectShareGroupHelper.kt`; `app/build.gradle.kts` |
| **Risk** | P0 crash on older phones taking the documented fallback. |
| **Recommended action** | Use `ContextCompat.registerReceiver` or version-gate the 2-arg register. |
| **Priority** | P0 |
| **Blocks freeze** | Yes |

### AUD-P2P-03 — LocalOnlyHotspot

| Field | Value |
|-------|--------|
| **Requirement** | Reliable local-only hotspot; credentials for QR. |
| **Status** | `PARTIAL` |
| **Evidence** | `WifiManager.startLocalOnlyHotspot` with main-looper Handler; `HotspotStartException(reason)`; cancellation calls `stop()`. No try/catch around `startLocalOnlyHotspot` itself (`SecurityException`). Empty SSID/password possible from null config. Concurrent `start()` if reservation exists reuses it. |
| **Relevant files** | `LocalHotspotHelper.kt` |
| **Risk** | OEM `INCOMPATIBLE_MODE` (STA+AP); Samsung common. |
| **Recommended action** | Catch `SecurityException`; validate non-blank SSID; test Pixel + Samsung. |
| **Priority** | P0 |
| **Blocks freeze** | Yes until proven |

### AUD-P2P-04 — Wi-Fi Direct group

| Field | Value |
|-------|--------|
| **Requirement** | Group-owner fallback with SSID/password. |
| **Status** | `PARTIAL` |
| **Evidence** | `createGroup` + `requestGroupInfo`; 20s timeout. `stop()` `removeGroup` with null listener. Receiver leak possible if `start()` times out (`finally` clears `pendingStart` but `registerReceiver` stays until `stop()` — ViewModel `cleanup` does call `stop()`). |
| **Relevant files** | `WifiDirectShareGroupHelper.kt` |
| **Risk** | API 26–32 crash (AUD-P2P-02); OEM Direct UX. |
| **Recommended action** | Fix registerReceiver; device-test Direct. |
| **Priority** | P0 |
| **Blocks freeze** | Yes |

### AUD-P2P-05 — Fallback behavior

| Field | Value |
|-------|--------|
| **Requirement** | Hotspot fail → pause Wi-Fi → retry → Direct → Quick Share. |
| **Status** | `PARTIAL` |
| **Evidence** | `ShareAppViewModel.startPhoneToPhoneLink` implements hotspot → `WifiPauseHelper` → retry → Direct. UI always offers Quick Share (`shareApkFile`). `WifiPauseHelper` uses deprecated `disconnect()` / `enableNetwork` — unreliable on API 29+. If pause did not happen, still `hotspotHelper.stop()` then Direct. |
| **Relevant files** | `ShareAppViewModel.kt`; `WifiPauseHelper.kt` |
| **Risk** | Pause breaks user’s Wi-Fi without helping hotspot. |
| **Recommended action** | Device-test fallback chain; consider not pausing on APIs where it is a no-op. |
| **Priority** | P1 |
| **Blocks freeze** | Partial — need one working path |

### AUD-P2P-06 — SecurityException handling

| Field | Value |
|-------|--------|
| **Requirement** | Missing permission must not crash. |
| **Status** | `PARTIAL` |
| **Evidence** | ViewModel `catch (e: Exception)` maps to `share_failed`. `WifiAwareDiscoveryHost` catches `SecurityException`. Hotspot helper does **not**. Direct `createGroup` failures go through `ActionListener.onFailure`, not always SecurityException. Uncaught `NoSuchMethodError` is **Error**, not Exception — **crashes**. |
| **Relevant files** | `ShareAppViewModel.kt`; `LocalHotspotHelper.kt` |
| **Risk** | Errors vs Exceptions. |
| **Recommended action** | Catch `Throwable` on share start **or** fix API-33 register; catch SecurityException at hotspot. |
| **Priority** | P0 |
| **Blocks freeze** | Yes |

### AUD-P2P-07 — Lifecycle

| Field | Value |
|-------|--------|
| **Requirement** | Server/hotspot stop on leave; FGS for long share (doc gap). |
| **Status** | `PARTIAL` |
| **Evidence** | `onCleared` / `stopSharing` stop server + hotspot + Direct + restore Wi-Fi. UI: “Keep this screen open.” [OFFLINE_DISTRIBUTION.md](./OFFLINE_DISTRIBUTION.md) remaining gap: no FGS for share. Process death kills HTTP. |
| **Relevant files** | `ShareAppViewModel.kt`; `ShareAppScreen.kt` |
| **Risk** | Recipients mid-download fail if sharer backgrounds (OEM). |
| **Recommended action** | Accept as limitation **or** bound share to FGS (code change). |
| **Priority** | P1 |
| **Blocks freeze** | P1-unless documented |

### AUD-P2P-08 — Cancellation

| Field | Value |
|-------|--------|
| **Requirement** | Coroutine cancel stops hotspot. |
| **Status** | `PARTIAL` |
| **Evidence** | `invokeOnCancellation { stop() }` on hotspot. Double-resume risk if `onStarted` after cancel — `cont.resume` without `isActive` check on success path (failure path checks `isActive`). |
| **Relevant files** | `LocalHotspotHelper.kt` |
| **Risk** | `IllegalStateException` if resume twice. |
| **Recommended action** | Guard `onStarted` with `cont.isActive`. |
| **Priority** | P1 |
| **Blocks freeze** | No unless reproduced |

### AUD-P2P-09 — IP / network discovery

| Field | Value |
|-------|--------|
| **Requirement** | Install URL reachable on the share network. |
| **Status** | `FAIL` |
| **Evidence** | `getLocalIpAddress()` prefers `192.168.43.*` then `49.*` then `4.*` then **first IPv4** then **`localhost`**. Wrong interface → recipient cannot download. Samsung/LocalOnlyHotspot subnets vary. Server starts **before** hotspot IP may exist (`startPhoneToPhoneLink` then `startApkServer`; 300ms delay only). |
| **Relevant files** | `WifiDirectSharer.kt`; `ShareAppViewModel.kt` |
| **Risk** | QR points at unreachable or loopback URL. |
| **Recommended action** | Bind/wait for hotspot interface; never emit `localhost` for phone-to-phone; show error instead. |
| **Priority** | P0 |
| **Blocks freeze** | Yes |

### AUD-P2P-10 — HTTP server start/shutdown

| Field | Value |
|-------|--------|
| **Requirement** | Serve APK on `:8765`; clean shutdown. |
| **Status** | `PARTIAL` |
| **Evidence** | `ServerSocket(PORT)`; raw `Thread` accept loop; `stopServer` sets flag and closes socket. Port conflict → exception → share_failed. HTTP headers use `trimIndent()` (`\n`) plus a final `\r\n` — **not** strict CRLF per line; browsers often tolerate. `readBytes()` loads entire APK in RAM. |
| **Relevant files** | `WifiDirectSharer.kt` |
| **Risk** | OOM on huge APK; header parsers on picky clients. |
| **Recommended action** | Stream file; optional CRLF fix; SO_REUSEADDR. |
| **Priority** | P1 |
| **Blocks freeze** | Unverified |

### AUD-P2P-11 — QR generation

| Field | Value |
|-------|--------|
| **Requirement** | Wi-Fi join QR + install URL QR. |
| **Status** | `PARTIAL` |
| **Evidence** | `ApkSharePayload.buildWifiQr` (`WIFI:T:WPA;...`) with escaping tests. Install QR is raw URL. Empty SSID still generates QR. |
| **Relevant files** | `ApkSharePayload.kt`; `ShareAppScreen.kt`; `BootstrapTests.kt` |
| **Risk** | Camera app rejects empty/malformed WIFI QR. |
| **Recommended action** | Do not show Wi-Fi QR until SSID non-blank. |
| **Priority** | P1 |
| **Blocks freeze** | Unverified |

### AUD-P2P-12 — APK source

| Field | Value |
|-------|--------|
| **Requirement** | What bytes are shared. |
| **Status** | `PASS` (identification) / `PARTIAL` (suitability) |
| **Evidence** | HTTP: `File(context.packageCodePath)`. Quick Share: `File(context.applicationInfo.sourceDir)` via FileProvider. Same installed package. CI/release pipeline produces **debug** `app-debug.apk` (universal-ish: `abiFilters` armeabi-v7a, arm64-v8a, x86_64; **no** Gradle splits). |
| **Relevant files** | `WifiDirectSharer.kt`; `ShareIntent.kt`; `android/build.sh` |
| **Risk** | Sharing a Play-installed **split** APK would be incomplete (Play is deferred, but dangerous if testers use Play internal track). |
| **Recommended action** | Freeze testers must use GitHub/debug **universal** APK. Document: do not share from Play split install. |
| **Priority** | P0 (documentation) |
| **Blocks freeze** | Docs yes; sideload APK itself is complete **if** debug/universal |

### AUD-P2P-13 — APK completeness

| Field | Value |
|-------|--------|
| **Requirement** | Recipient can install a complete app (all ABIs needed). |
| **Status** | `PARTIAL` |
| **Evidence** | Debug APK includes three ABIs in one APK (`abiFilters`, no `splits { abi }`). Play workflow builds **AAB** (`android-release-play.yml`) — Play delivery **splits**; `packageCodePath` would be **base APK only**. Freeze GitHub path: complete. Play path: **FAIL** if used for share. |
| **Relevant files** | `app/build.gradle.kts`; `.github/workflows/android-release-play.yml` |
| **Risk** | INSTALL_FAILED_MISSING_SPLIT / missing `.so`. |
| **Recommended action** | Keep freeze on universal debug/release APK; never use AAB extract for share. |
| **Priority** | P0 for Play mix-up |
| **Blocks freeze** | If anyone shares from Play install |

### AUD-P2P-14 — Installation flow (Device B)

| Field | Value |
|-------|--------|
| **Requirement** | Browser download + unknown-sources; first launch joins mesh. |
| **Status** | `FAIL` |
| **Evidence** | Landing HTML “Install Mycena” → `/mycena.apk`. **No** in-app `REQUEST_INSTALL_PACKAGES` (correct for Device B without Mycena). Landing page does **not** mention “allow unknown apps.” Bootstrap JS auto-download to browser Downloads. `OfflineBootstrap` cannot reliably read that (AUD-P2P-17). No install-permission activity in Mycena for Device B. |
| **Relevant files** | `WifiDirectSharer.kt`; `OfflineBootstrap.kt` |
| **Risk** | User installs APK, skips mesh join, thinks share “worked.” |
| **Recommended action** | UX copy for unknown sources; sidecar via intent extra / nearby file in app storage; first-run paste invite fallback. |
| **Priority** | P0 |
| **Blocks freeze** | Yes |

### AUD-P2P-15 — Samsung-specific behavior

| Field | Value |
|-------|--------|
| **Requirement** | Characterized hotspot/Direct/install on One UI. |
| **Status** | `FAIL` |
| **Evidence** | None. Error strings mention VPN/normal hotspot. `ERROR_INCOMPATIBLE_MODE` (3) is the usual STA conflict. |
| **Relevant files** | `strings.xml` `share_app_hotspot_failed` |
| **Risk** | G3 fails on the most common EU Android OEM. |
| **Recommended action** | G4 + G3 on one Samsung. |
| **Priority** | P1 |
| **Blocks freeze** | P1-unless-accepted |

### AUD-P2P-16 — Bootstrap sidecar generation

| Field | Value |
|-------|--------|
| **Requirement** | Signed invite sidecar; no code execution. |
| **Status** | `PARTIAL` |
| **Evidence** | `refreshBootstrapSidecar`: if `!MeshService.isNodeReady()` → **null sidecar** (empty 200). `mycena.buildOfflineShareBootstrapSidecar(installUrl)` when ready. JSON invite — not executable (agree with OFFLINE_DISTRIBUTION). Doc remaining gap: hotspot before node ready. |
| **Relevant files** | `ShareAppViewModel.kt`; `crates/mycena-app` offline share |
| **Risk** | APK shares without join credentials. |
| **Recommended action** | Block “ready to share” until sidecar non-empty **or** show warning. |
| **Priority** | P0 |
| **Blocks freeze** | Yes for “join mesh” claim |

### AUD-P2P-17 — Sidecar import from Downloads

| Field | Value |
|-------|--------|
| **Requirement** | First launch imports `mycena-bootstrap.myc`. |
| **Status** | `FAIL` |
| **Evidence** | `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)` listFiles. Manifest has **no** `READ_EXTERNAL_STORAGE` / `READ_MEDIA_*` / `MANAGE_EXTERNAL_STORAGE`. On API 29+ scoped storage, Chrome downloads are **not** listable. Import-once flag can stick on failure paths only if prefs written — failure returns null without setting imported. |
| **Relevant files** | `OfflineBootstrap.kt`; `AndroidManifest.xml` |
| **Risk** | Entire auto-onboarding path dead on API 29–34 (target 34). |
| **Recommended action** | Use SAF, MediaStore, or FileProvider handoff; or in-APK extra; or QR invite on first run. |
| **Priority** | P0 |
| **Blocks freeze** | Yes |

### AUD-P2P-18 — FileProvider / Quick Share

| Field | Value |
|-------|--------|
| **Requirement** | Share APK via system sheet without exposing the device. |
| **Status** | `FAIL` (hygiene) / `PARTIAL` (function) |
| **Evidence** | `file_paths.xml` is `<root-path path="." />` — FileProvider can expose **any filesystem path** if a URI is granted. Needed because APK lives under `/data/app/...`. Play often rejects `root-path`. |
| **Relevant files** | `res/xml/file_paths.xml`; `ShareIntent.kt` |
| **Risk** | Over-broad content provider (other bugs could leak files). |
| **Recommended action** | Copy APK to cache and use `<cache-path>`; never `root-path`. |
| **Priority** | P0 (public repo / Play later) |
| **Blocks freeze** | Yes for hygiene G8 spirit |

---

## 5. Android crash / robustness findings

| ID | Finding | Status | Risk | Priority | Blocks |
|----|---------|--------|------|----------|--------|
| AUD-CR-01 | `startForegroundService` in `MainActivity` / Settings with no try/catch (`ForegroundServiceStartNotAllowedException` if ever started from background) | `PARTIAL` | Medium if called off-UI | P1 | No if only from Activity |
| AUD-CR-02 | Node init failure → infinite spinner, `START_STICKY` retry without user signal | `FAIL` | Hang | P0 | Yes |
| AUD-CR-03 | Wi-Fi Direct `registerReceiver` flags API 33+ only | `FAIL` | Crash API 26–32 | P0 | Yes |
| AUD-CR-04 | Hotspot `SecurityException` uncaught at API site | `PARTIAL` | Caught as generic Exception | P0 | Share reliability |
| AUD-CR-05 | BLE: null `bluetoothLeScanner` returns (no crash) | `PASS` | BLE silently off | P1 | No |
| AUD-CR-06 | `!!` on `session.hotspot` inside `if (hotspot != null)` — Compose smart-cast OK | `PASS` | Low | P2 | No |
| AUD-CR-07 | BLE collect jobs cancelled in `stopBleBootstrap`; `SupervisorJob` on service | `PARTIAL` | Cancellation generally OK | P2 | Unverified |
| AUD-CR-08 | Share HTTP `serverSocket!!` in accept thread after close | `PARTIAL` | IOException if serving flag races | P1 | Unverified |
| AUD-CR-09 | No Samsung / doze / OEM APIs | `FAIL` | FGS killed | P1 | G4 |
| AUD-CR-10 | WebRTC/Aware started from mesh service — extra OEM surface | `PARTIAL` | Out of freeze scope but crashable | P2 | No |
| AUD-CR-11 | Instrumentation coverage ≈ 1 test | `FAIL` as G5 expansion | Regressions | P1 | G5 quality |
| AUD-CR-12 | Onboarding `wipeAllLocalData` on every successful complete | `PARTIAL` | Wipes imported bootstrap if user didn’t skip | P1 | If skip fails |

Unsafe platform APIs of note: deprecated Wi-Fi `disconnect`/`enableNetwork`; `LocalOnlyHotspot` `wifiConfiguration` pre-R; `root-path` FileProvider.

---

## 6. Release artifacts

| ID | Requirement | Status | Evidence |
|----|-------------|--------|----------|
| AUD-REL-01 | What APK is shared / released | `PASS` (fact) | **Debug** `android/app/build/outputs/apk/debug/app-debug.apk`, renamed `mycena-${TAG}-android.apk` in reproducible script and GitHub Release. In-app share serves **installed** `packageCodePath`. |
| AUD-REL-02 | Base vs complete artifact | `PARTIAL` | Debug/universal APK is a **complete** APK (3 ABIs, no splits). Not an Android App Bundle. |
| AUD-REL-03 | Play/AAB split problems | `DEFERRED` / trap | Play workflow builds **signed AAB**. Sharing from a Play install would serve a **split base APK** → install failure. Freeze must not mix Play install + share. |
| AUD-REL-04 | Signing | `PARTIAL` | Debug keystore (Gradle default) for freeze APK. Release signingConfig only if `android.injected.signing.store.file` (Play workflow). Two trust roots if both exist. |
| AUD-REL-05 | Reproducibility | `PARTIAL` | Container recipe exists; output is still debug APK; §7 of RELEASE_INTEGRITY admits ZIP timestamp issues. **Not run** in this audit. |
| AUD-REL-06 | Hashes | `PARTIAL` | `SHA256SUMS.txt` + BLAKE3 manifest **on tag workflow**; no tag here. |
| AUD-REL-07 | Versioning | `PASS` | `version.properties` 0.1.75 / 76; bump script in debug-apk workflow. |

**Recommended action:** Treat GitHub Release APK as **debug-signed technical beta** in README; never imply Play signing; forbid share-from-Play in docs.

---

## 7. Public repository

| ID | Requirement | Status | Evidence |
|----|-------------|--------|----------|
| AUD-PUB-01 | Generated artifacts not committed | `PASS` | gitignore `target/`, `android/**/build/`, `artifacts/`, UniFFI generated path |
| AUD-PUB-02 | Secrets / credentials | `PASS` | No tracked env/jks; Play secrets are GitHub secret **names** in YAML |
| AUD-PUB-03 | Private infra | `PASS` | Public Fly hostname by design |
| AUD-PUB-04 | Personal data | `PARTIAL` | Local `android/local.properties` has a **user SDK path**; untracked. Do not commit. |
| AUD-PUB-05 | Dev-only scripts | `PASS` | Present and documented (battery profile, docker) |
| AUD-PUB-06 | License / deny | `PARTIAL` | AGPL LICENSE; `deny.toml` + CI `check-dependencies.sh` (not re-run) |
| AUD-PUB-07 | Stash / WIP | `PARTIAL` | Stash local; WIP doc can mislead contributors |
| AUD-PUB-08 | FileProvider root-path | `FAIL` | Over-broad for a public security-sensitive app |
| AUD-PUB-09 | Dependabot | `FAIL` | Missing; SD-150 promised enable at public launch |
| AUD-PUB-10 | CI_PUBLIC_TRANSITION | `FAIL` | Unchecked |

---

## 8. Plan §11 contradictions — audit disposition (not resolved)

| ID | Status | Audit note |
|----|--------|------------|
| C1 Desktop BLE | **Keep open** | **Code:** `DesktopBleHost` + `blew` in `mycena-desktop`. **Docs:** KNOWN_LIMITATIONS still “not active.” Prefer **code**; fix KNOWN_LIMITATIONS **or** prove host is stub-only at runtime (UNVERIFIED). |
| C2 CONNECTIVITY stub vs shipped | **Keep open** | Same file conflict; desktop module is real source, not a stub file. |
| C3 Disaster mesh intro vs table | **Keep open** | Intro sentence stale if blew host works. |
| C4 Resource map UI | **Keep open** | Need UI grep in a later task; not blocking G3. |
| C5 LEVEL0 A2 | **Confirmed FAIL** | Manual sign-off pending; do not treat as done. |
| C6 Share stash vs Cat 3 empty | **Updated** | Share **is on main**. Stash still exists. WORK_REMAINING Cat 3 empty is **misleading** (freeze defects exist). |
| C7 Offline share gaps | **Confirmed** | FGS + sidecar-before-node + Downloads import match code. |
| C8 StrongBox | **Nuance** | `setRequestStrongBoxBacked(true)` **is** used for MasterKey; identity keys still not StrongBox. architecture.md over-claims; SECURE_HARDWARE_STATUS slightly under-claims MasterKey. |
| C9 SD-150 | **Confirmed** | SBOM/repro/signing scripts exist; cargo vet / Dependabot do not. |
| C10 SD-060 | **Keep open** | Register body vs roadmap “Done” — not re-verified in node code this pass. |
| C11 AR-02 | **Keep open** | Architecture ledger stale vs SD-031 mitigated. |
| C12 CHANGELOG | **Confirmed FAIL** | No root CHANGELOG. |
| C13 Fuzz duration | **YAML wins** | `FUZZ_SECONDS=45`. |
| C14 TM version | **Keep open** | Pack v1.0 vs TM v1.1. |
| C15 Play vs GitHub | **Keep** as product decision | This freeze = GitHub. |
| C16 Residuals unsigned | **Confirmed** | |
| C17 MeshCoin Phase 0 | **PARTIAL** | Wallet string is preview-not-money; roadmap checkboxes inconsistent. |
| C18 Privacy Pages | **UNVERIFIED** | In-app + `docs/legal/` exist; live URL not fetched. |
| C19 BLE demo vs Done | **Confirmed** | Hardware demo outstanding. |

---

## 9. Tests actually present (not executed)

| Layer | What exists | Gap |
|-------|-------------|-----|
| Rust | Workspace nextest in CI; SD-030 integration test; MycenaLab gates; fuzz PR smoke | No log for this SHA |
| Android unit | Permissions, onboarding copy, BLE UUID, **HTTP header strings**, Wi-Fi QR, thermal, activity profile | **No** hotspot/server/instrumented share tests |
| Android instrumented | Manifest service declared | Does not start node |
| Hardware | Pixel 6 battery VALID runs | Not freeze matrix |

---

## Executive summary

Counts below are **audit items in this document** (gates + scope + P0/P1/P2 + P2P + crash + release + public + §11). Sub-rows in tables are counted once at the ID level listed in §§1–8.

| Status | Count |
|--------|-------|
| **PASS** | 14 |
| **PARTIAL** | 41 |
| **FAIL** | 48 |
| **UNVERIFIED** | 14 |
| **DEFERRED** | 18 |
| **NOT APPLICABLE** | 0 |

**Blocking the freeze (must fix or explicitly accept per plan):** all `FAIL` items marked **Blocks freeze: Yes**, including G2–G4, G6, G9–G10, G3/P2P permissions, API 33 registerReceiver, Downloads import, IP/`localhost`, sidecar-if-not-ready, panic disclosure, §11 honesty, debug-APK RC story, empty hardware matrix.

Approximate **hard blockers** (cannot accept away without lying): **~22** (G2 crash paths, G3 E2E+P2P-01/02/09/14/16/17, G6 empty matrix, G9 no RC, P0-01/02/04/08, FileProvider root-path, doc contradictions that contradict user claims).

---

## Top 10 blockers

Ranked by release impact (user cannot complete the advertised technical-beta loop, or would be misled/crashed).

1. **No hardware evidence at all** (AUD-G6, AUD-P0-02/04) — cannot claim a testable beta.
2. **Phone-to-phone E2E is unimplemented as a proven flow** (AUD-G3) plus **Downloads sidecar import dead on API 29+** (AUD-P2P-17).
3. **Wi-Fi Direct fallback crashes on API 26–32** (`registerReceiver` flags) (AUD-P2P-02 / AUD-CR-03).
4. **Share screen requests no hotspot/Nearby permissions** (AUD-P2P-01).
5. **Install URL may be `localhost` or the wrong interface** (AUD-P2P-09).
6. **GitHub “release APK” is `assembleDebug` / debug-signed** (AUD-G9, AUD-REL-01) — must be honest or replaced.
7. **MeshService init failure hangs the UI forever** (AUD-CR-02).
8. **Auto-join sidecar omitted when node not ready; remaining gaps match OFFLINE_DISTRIBUTION** (AUD-P2P-16, C7).
9. **Public claims vs docs (BLE desktop, limitations, unsigned residuals, §11)** (AUD-P0-08, AUD-G1).
10. **FileProvider `root-path` + no Samsung data / no G4 exception** (AUD-P2P-18, AUD-G4).

Honorable mentions: panic UI missing mesh-copies sentence (AUD-S12); empty CHANGELOG vs RELEASING (C12); CI not recorded for this SHA (AUD-G5).

---

## Recommended implementation order

Dependency-aware **next tasks** (do **not** start them in this audit):

1. **Docs honesty pass (G1 remainder)** — Dispose §11: desktop BLE, WORK_REMAINING Cat 3, WIP stash note vs `main`, StrongBox wording, SD-150, CHANGELOG/RELEASING, fuzz 45s. Soften or qualify pocket-mesh until G6.
2. **P0 Android crash/hang fixes (G2)** — `ContextCompat.registerReceiver`; hotspot `SecurityException` + `cont.isActive`; never `localhost` URL; node-init error UI; FileProvider cache-path APK copy.
3. **P0 share correctness (G3 code)** — Runtime permissions on Share; wait for hotspot IP; refuse share without sidecar or warn; replace Downloads scrape with a viable import (or drop auto-onboard and document QR-only join); unknown-sources copy on landing HTML.
4. **Debug vs release artifact decision (G9)** — Document debug-signed universal APK as the freeze artifact **or** add `assembleRelease` universal APK into reproducible-build; add CHANGELOG.
5. **Panic copy + wipe failure handling (AUD-S12)**.
6. **G5 evidence** — Run/record `cargo nextest`, `android-ci`, fuzz-pr, gitleaks, `check-dependencies.sh` on the post-fix SHA.
7. **G3+G6 hardware** — Two phones (include one Samsung or write exception): share, LAN chat, panic, reinstall, optional BLE-air.
8. **G4** — Samsung idle/FGS/hotspot or public “untested” sentence.
9. **G7/G8** — Residual deferral writing; Dependabot or SD-150 update; CI_PUBLIC_TRANSITION when flipping public; `MYCENA_CI_TIER=full`.
10. **G9 tag + G10** — Only after evidence exists.

Do not implement those tasks in the same change as this audit.
