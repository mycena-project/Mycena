# Mycena Release Freeze Implementation Backlog

**Reconciled:** 2026-08-29 against current source, tests, and freeze docs. Do **not** treat older Cursor prose as evidence.
**Created:** 2026-08-29
**Based on:** [RELEASE_FREEZE_PLAN.md](./RELEASE_FREEZE_PLAN.md), [RELEASE_FREEZE_AUDIT.md](./RELEASE_FREEZE_AUDIT.md) (SHA `fb32b1e`)
**Does not replace:** [WORK_REMAINING.md](./WORK_REMAINING.md). Remaining **code before hardware:** [P0_IMPLEMENTATION_QUEUE.md](./P0_IMPLEMENTATION_QUEUE.md).

Every item is justified by the audit, source/tests, or explicit freeze criteria. Nice-to-haves are P2. Plan §2.2 out-of-scope work is **not** listed as freeze implementation (iOS ship, Tor, MLS, MeshCoin BFT, Play listing, OSS-Fuzz submit, Aware/WebRTC field validation, FGS-for-share as a new product feature — the last is only a **documentation** choice in RFB-P2P-09).

## Official project status (this reconciliation)

| Gate | Status |
|------|--------|
| Feature freeze | **ACTIVE** (policy lock: [DEVELOPMENT_FREEZE.md](./DEVELOPMENT_FREEZE.md)) |
| Release freeze | **NOT APPROVED** |
| Public release | **NO-GO** |
| Hardware validation | **NOT YET PERFORMED** |
| G9 | **FAIL** ([RELEASE_CANDIDATE.md](./RELEASE_CANDIDATE.md)) |
| G10 | **NOT APPROVED** ([FINAL_DEVELOPMENT_FREEZE.md](./FINAL_DEVELOPMENT_FREEZE.md) evidence tables). Policy lock ≠ G10 pass. |

## Item status vocabulary

Do **not** use bare `done` when runtime or hardware evidence is still required.

| Value | Meaning |
|-------|---------|
| `OPEN` | Required work is not in the tree (or public claim still false). |
| `IN_PROGRESS` | Partial: some files updated, remainder still required. |
| `DONE_STATIC` | Implemented in source and/or docs; **not** proven by a recorded runtime/hardware/CI job. |
| `DONE_TESTED` | Covered by automated tests **in this tree** (test source exists). Device/CI still may be UNVERIFIED. |
| `BLOCKED` | Cannot proceed without an external dependency. |
| `UNVERIFIED` | Implementation exists or is N/A; required evidence (device, CI URL, script log) is missing. |
| `DEFERRED` | Explicitly postponed with owner/date (not a pass). |

**PERM-01** is an alias of **RFB-P2P-02** (`DONE_TESTED` for JVM permission lists; device UNVERIFIED).

**2026-08-29 phone-to-phone code:** share helpers are in source. **No hardware G3/G4 run.** Manual IDs: [PHONE_TO_PHONE_MANUAL_TEST.md](./PHONE_TO_PHONE_MANUAL_TEST.md).

**Audit cross-ref:** IDs like `AUD-P2P-02` point at [RELEASE_FREEZE_AUDIT.md](./RELEASE_FREEZE_AUDIT.md).

## Reconciliation index (source/test evidence, 2026-08-29)

| ID | Status | Evidence (this pass) |
|----|--------|----------------------|
| RFB-DOC-01 | OPEN | No C1–C19 disposition table. BLE pocket-mesh “Good fit” remainder closed as RFB-DOC-05. |
| RFB-DOC-02 | DONE_STATIC | README + WORK_REMAINING link freeze docs. |
| RFB-DOC-03 | DONE_STATIC | README / OFFLINE_DISTRIBUTION / hardware runbook name Technical Beta vs Development vs Play. CI debug path still exists and is labeled Development. |
| RFB-DOC-04 | DONE_STATIC | RELEASING.md does not require root CHANGELOG. |
| RFB-DOC-05 | DONE_STATIC | KNOWN_LIMITATIONS: BLE pocket mesh implemented, not hardware-verified; HV-06 / BLE-air empty. |
| RFB-DOC-06 | DONE_STATIC | WIP: ShareApp on `main`; stash not blindly applied. Category 3 does not imply freeze P0 complete. |
| RFB-P2P-01 | DONE_STATIC | `ContextCompat.registerReceiver` in `WifiDirectShareGroupHelper.kt`. |
| RFB-P2P-02 | DONE_TESTED | `SharePermissionsSdkTest`. |
| RFB-P2P-03 | DONE_TESTED | Blank SSID helper + unit tests. |
| RFB-P2P-04 | DONE_TESTED | `ShareLinkAddressesTest.pick_never_falls_back_to_localhost`. |
| RFB-P2P-05 | DONE_STATIC | `sidecarReady` UI warning. |
| RFB-P2P-06 | DONE_STATIC | `OfflineBootstrap.applyAfterWipe`. |
| RFB-P2P-07 | DONE_STATIC | ViewModel `CancellationException` + `Throwable` catch. |
| RFB-P2P-08 | DONE_TESTED | `canPauseStaWifi` API 29+ no-op test. |
| RFB-P2P-09 | DONE_STATIC | Docs + keep-open string; no FGS. |
| RFB-P2P-10 | DONE_STATIC | `ShareHttp` CRLF; streamed APK copy. |
| RFB-APK-01 | DONE_TESTED | BootstrapTests landing/unknown-apps copy. |
| RFB-APK-02 | DONE_STATIC | `file_paths.xml` cache-path only. |
| RFB-APK-03 | DONE_STATIC | Share refuses splits + debug; Technical Beta is universal assembleRelease. Device ABI install: UNVERIFIED. |
| RFB-PERM-01 | DONE_TESTED | Alias of P2P-02. |
| RFB-PERM-02 | UNVERIFIED | Device deny matrix empty. |
| RFB-OEM-01 | DONE_STATIC | Published Samsung-untested exception; no named Galaxy. |
| RFB-OEM-02 | UNVERIFIED | Idle/doze unrun. |
| RFB-OEM-03 | UNVERIFIED | Static only. |
| RFB-NET-01 | DONE_STATIC | `nodeFailed` + timeout UI. |
| RFB-NET-02 | UNVERIFIED | No pairing record. |
| RFB-PERS-01 | DONE_TESTED | Panic confirm + fail-closed wipe in source; `LocalDataWiperTest`. HV-18 UNVERIFIED. |
| RFB-PERS-02 | UNVERIFIED | Restart persistence unrun. |
| RFB-PERS-03 | DONE_STATIC | `applyAfterWipe` after onboarding wipe. |
| RFB-PERS-04 | DONE_STATIC | Keep-vs-wipe / key table in OFFLINE_DISTRIBUTION. `adb install -r` unrun. |
| RFB-TEST-01 | UNVERIFIED | No CI URLs. |
| RFB-TEST-02 | DONE_TESTED | `scripts/android-test.sh` + `requireUniffiKotlin`; JVM suite recorded 2026-08-29. Device P2P-MT-01 still UNVERIFIED. |
| RFB-TEST-03 | UNVERIFIED | Local G7 tests noted; no freeze-SHA CI URL. |
| RFB-HW-01 | UNVERIFIED | Matrix empty. |
| RFB-HW-02 | UNVERIFIED | A2 unsigned. |
| RFB-HW-03 | UNVERIFIED | No BLE-air drill. |
| RFB-HW-04 | DONE_STATIC | Harness docs + capture script. |
| RFB-SEC-01 | DONE_STATIC | Unsigned GitHub-only disposition. |
| RFB-SEC-02 | DONE_STATIC | SECURITY_RELEASE_REVIEW.md. |
| RFB-SEC-03 | DEFERRED | External review not commissioned. |
| RFB-SEC-04 | UNVERIFIED | Workflow exists; no job URL. |
| RFB-SC-01 | UNVERIFIED | Full check-dependencies.sh not recorded. |
| RFB-SC-02 | DONE_STATIC | dependabot.yml in tree. |
| RFB-HYG-01 | OPEN | Day-of public transition unchecked. |
| RFB-HYG-02 | DONE_STATIC | Dead `mycena-android` path filter removed. |
| RFB-HYG-03 | DONE_STATIC | local.properties not tracked. |
| RFB-REL-01 | DONE_STATIC | Technical Beta assembleRelease + local JKS. New APK SHA-256 `276e653e…` in TECHNICAL_BETA_ARTIFACT_VERIFICATION.md. Install smoke UNVERIFIED. |
| RFB-REL-02 | OPEN | No tag; G9 FAIL. |
| RFB-REL-03 | OPEN | G10 / release freeze not approved. |

---

## How to use this queue

1. Implement in **Implementation Sequence** order unless a later P0 has no dependency on an unfinished earlier P0.
2. Do not mark a gate passed because this backlog exists.
3. Hardware items require device records; CI items require job URLs or command logs.

---

## 1. Documentation

### RFB-DOC-01 — Dispose freeze-plan §11 contradictions

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G1 |
| **Problem statement** | Plan G1 cannot pass while §11 rows have no disposition. Public claims contradict code or each other (C1–C19). |
| **Current behavior** | Contradictions listed; none accepted/fixed with owner. |
| **Desired behavior** | Each C1–C19 has: fix doc, fix code, or accept-with-owner-and-date. User-facing docs match the chosen claim. |
| **Affected components/files** | `docs/KNOWN_LIMITATIONS.md`, `docs/CONNECTIVITY_MODES.md`, `docs/DISASTER_MESH_ROADMAP.md`, `docs/LEVEL0_ROADMAP.md`, `docs/WORK_REMAINING.md`, `docs/WIP-ANDROID-SHARE-STASH.md`, `docs/architecture.md`, `docs/SECURE_HARDWARE_STATUS.md`, `docs/SECURITY_DEBT_REGISTER.md` (SD-150, SD-060), `docs/ARCHITECTURE_RISKS.md`, `docs/FUZZING.md`, `docs/CI_PUBLIC_TRANSITION.md`, `docs/EXTERNAL_SECURITY_REVIEW.md`, `docs/SECURITY_ROADMAP.md`, `docs/SECURITY_REMEDIATION_ROADMAP.md`, `RELEASING.md`, `README.md` |
| **Dependencies** | None (docs can start immediately; BLE claim may wait on RFB-HW-03 if choosing “unverified” wording). |
| **Implementation notes** | Audit §8: prefer **code** for desktop BLE (`mycena-desktop` `blew` host) unless runtime proves stub. C6: share **is on main**; update WIP stash note. C8: MasterKey `setRequestStrongBoxBacked(true)` exists; identity keys are not StrongBox. C13: YAML `FUZZ_SECONDS=45` wins. C15: freeze = GitHub, not Play. C4 resource-map UI: grep Android/Desktop and one-line status. Do not invent new product claims. |
| **Verification method** | Reviewer checklist: every §11 ID has a disposition paragraph in this backlog’s evidence note or a “Dispositions” subsection in WORK_REMAINING / freeze plan. |
| **Acceptance criteria** | No remaining “keep open” without owner. README / KNOWN_LIMITATIONS do not contradict CONNECTIVITY_MODES on BLE. |
| **Evidence required** | Dated doc diff; optional table C# → disposition. |
| **Status** | `OPEN` |

### RFB-DOC-02 — Link freeze plan from README and WORK_REMAINING

| Field | Content |
|-------|---------|
| **Priority** | P2 |
| **Release gate** | G0 / G1 |
| **Problem statement** | Audit G0: plan exists but is easy to miss. |
| **Current behavior** | README and WORK_REMAINING link DEVELOPMENT_FREEZE, RELEASE_FREEZE_PLAN, and this backlog. |
| **Desired behavior** | One-line links so freeze gates are the queue, not a second silent roadmap. |
| **Affected components/files** | `README.md`, `docs/WORK_REMAINING.md` |
| **Dependencies** | RFB-DOC-01 preferred so links do not point at contradictory docs. |
| **Implementation notes** | Point at PLAN, AUDIT, this BACKLOG. Do not duplicate roadmaps. |
| **Verification method** | Links resolve. |
| **Acceptance criteria** | Both files mention freeze overlay. |
| **Evidence required** | Diff. |
| **Status** | `DONE_STATIC` — README + WORK_REMAINING link freeze docs (2026-08-29). |

### RFB-DOC-03 — Honest GitHub APK / do-not-share-from-Play

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G9 / G3 |
| **Problem statement** | Release workflow ships **debug-signed** `app-debug.apk` renamed. Play AAB splits would make in-app share serve a **base APK only**. |
| **Current behavior** | README points at GitHub Releases; `release.yml` already says debug-signed in the body; user-facing README does not stress debug key or “never share from a Play install.” |
| **Desired behavior** | README / OFFLINE_DISTRIBUTION / RELEASING state: freeze artifact is debug-signed universal APK unless RFB-REL-01 chooses otherwise; sharing from Play split installs is unsupported. |
| **Affected components/files** | `README.md`, `docs/OFFLINE_DISTRIBUTION.md`, `RELEASING.md`, `docs/RELEASE_INTEGRITY.md` (if wording implies official Play-grade APK) |
| **Dependencies** | RFB-REL-01 (decision: keep debug vs add release-signed universal). Can draft “debug until REL-01” immediately. |
| **Implementation notes** | Audit AUD-REL-01, AUD-P2P-12, AUD-P2P-13. |
| **Verification method** | Doc review against `android/build.sh` (`assembleDebug`) and `scripts/reproducible-build.sh`. |
| **Acceptance criteria** | A stranger cannot reasonably think GitHub APK is Play-signed or that Play-installed share is supported. |
| **Evidence required** | Diff. |
| **Status** | `DONE_STATIC` — Development vs Technical Beta vs Play documented. Share refuses debug/split. Runbook uses `build-technical-beta-apk.sh`. CI/reproducible-build still debug (labeled Development). |

### RFB-DOC-04 — CHANGELOG vs RELEASING.md

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G9 |
| **Problem statement** | RELEASING step 2 requires `CHANGELOG.md`; repo has none at root (C12). |
| **Current behavior** | Process doc is unsatisfiable. |
| **Desired behavior** | Either add root `CHANGELOG.md` for the RC, or remove/amend the RELEASING step. |
| **Affected components/files** | `CHANGELOG.md` (new) and/or `RELEASING.md` |
| **Dependencies** | RFB-REL-02 (tag content). |
| **Implementation notes** | Doc-only is enough for freeze. |
| **Verification method** | RELEASING and tree agree. |
| **Acceptance criteria** | A releaser can follow RELEASING without a missing file. |
| **Evidence required** | File present or step removed. |
| **Status** | `DONE_STATIC` — `RELEASING.md` records changes on the GitHub Release; no root `CHANGELOG.md`. |
| **Implementation record** | 2026-08-29: `RELEASING.md` step 2 records user-visible changes on the GitHub Release; no root `CHANGELOG.md` required. |

### RFB-DOC-05 — Soften or qualify pocket-mesh / BLE-air until hardware exists

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G1 / G6 |
| **Problem statement** | BLE F1–F3 code exists; LEVEL0 A2 unsigned; no BLE-air device record. Claiming pocket mesh without evidence violates known-limitations policy. |
| **Current behavior** | Disaster/README language implies Android BLE pocket mesh works. |
| **Desired behavior** | Until RFB-HW-03 passes: user docs say BLE pocket mesh is **unverified on this freeze** (or similar), **or** HW-03 is completed. |
| **Affected components/files** | `docs/KNOWN_LIMITATIONS.md`, `README.md` (disaster row), optionally `docs/LEVEL0_ROADMAP.md` status |
| **Dependencies** | Mutually exclusive with completing RFB-HW-03 first. |
| **Implementation notes** | Plan P1: drill **or** soften. Do not delete ADR 002. |
| **Verification method** | Doc vs empty BLE-air matrix cell. |
| **Acceptance criteria** | No “works today” BLE-air claim without a filled matrix row. |
| **Evidence required** | Diff or HW-03 record. |
| **Status** | `DONE_STATIC` — `KNOWN_LIMITATIONS.md` disaster section: LAN mesh remains Good fit; Android BLE pocket mesh is implemented and **not hardware-verified** (HV-06 / BLE-air empty). ADR 002 kept. |
| **Implementation record** | 2026-08-29: BLE “Good fit” claim removed. Remaining work is RFB-HW-03 (hardware). |

### RFB-DOC-06 — Align WORK_REMAINING Category 3 and WIP share stash note

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G1 |
| **Problem statement** | Cat 3 says no software left; freeze defects exist. WIP doc says share was not committed; share **is** on `main`. |
| **Current behavior** | Misleading completeness. |
| **Desired behavior** | WORK_REMAINING points at this backlog for freeze defects. WIP note: stash may hold extra deltas; do not apply blindly; `main` has ShareApp. |
| **Affected components/files** | `docs/WORK_REMAINING.md`, `docs/WIP-ANDROID-SHARE-STASH.md` |
| **Dependencies** | None. |
| **Implementation notes** | Optional: `git stash show -p` locally for a one-line “stash vs main” note; do not dump stash into the repo. |
| **Verification method** | Cat 3 no longer implies zero Android share work. |
| **Acceptance criteria** | Contributors are not told software freeze work is empty. |
| **Evidence required** | Diff. |
| **Status** | `DONE_STATIC` — Category 3 footer: historical Iteration E completeness does not mean freeze P0 / hardware is done. `WIP-ANDROID-SHARE-STASH.md`: ShareApp is on `main`; stash may hold extra deltas — do not apply blindly. |

---

## 2. Phone-to-phone

### RFB-P2P-01 — Fix Wi-Fi Direct `registerReceiver` for API 26–32

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G2 / G3 |
| **Problem statement** | `Context.registerReceiver(receiver, filter, flags)` is API 33+. minSdk is 26. Fallback path crashes with `NoSuchMethodError`. Errors are not `Exception` — ViewModel catch does not help. |
| **Current behavior** | `WifiDirectShareGroupHelper` uses `RECEIVER_NOT_EXPORTED` three-arg register. |
| **Desired behavior** | Registration works on API 26–32 and 33+ without crash. |
| **Affected components/files** | `android/app/src/main/java/io/github/mycena_project/app/bootstrap/WifiDirectShareGroupHelper.kt` |
| **Dependencies** | None. |
| **Implementation notes** | Use `ContextCompat.registerReceiver` or version-gate 2-arg vs 3-arg. Keep `RECEIVER_NOT_EXPORTED` on API 33+. |
| **Verification method** | Unit/instrumented test if feasible; otherwise API 26 emulator starting Direct fallback must not crash (logcat). |
| **Acceptance criteria** | Direct fallback on API 26 emulator does not throw `NoSuchMethodError`. |
| **Evidence required** | Logcat snippet + API level; or test output. |
| **Status** | `DONE_STATIC` — `WifiDirectShareGroupHelper` uses `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)`. No API 26 JVM test. Device: UNVERIFIED (P2P-MT-01). |
| **Implementation record** | `WifiDirectShareGroupHelper` now uses `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` (same pattern as `SosViewModel`). |
| **Test result** | Unit tests cannot invoke `Context.registerReceiver` on API 26. JVM tests added for share helpers; this crash path is device-only. |
| **Remaining device verification** | P2P-MT-01 (API 26 emulator Direct fallback). |
| **Evidence still required** | Logcat proving no `NoSuchMethodError`. |

### RFB-P2P-02 — Request share/hotspot runtime permissions before start

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G2 / G3 |
| **Problem statement** | Share starts LocalOnlyHotspot / Direct with no permission launcher. API 26–32 typically needs fine location; API 33+ Nearby Wi-Fi. |
| **Current behavior** | `ShareAppScreen` has no request; BLE permissions are a separate Settings flow. |
| **Desired behavior** | Before hotspot/Direct: request the API-appropriate set; denial is explained and non-fatal (Quick Share still offered). |
| **Affected components/files** | `ShareAppScreen.kt`, possibly `BleBootstrapPermissions.kt` or a small `SharePermissions` helper, `strings.xml` |
| **Dependencies** | None. Align with RFB-PERM-01 (same work; this is the share-screen ticket). |
| **Implementation notes** | Reuse `NEARBY_WIFI_DEVICES` vs `ACCESS_FINE_LOCATION` split already in `BleBootstrapPermissions`. Location services ON may still be required by OEM — document in UI if denied after grant. |
| **Verification method** | Fresh install: deny → no crash, error copy; grant → hotspot attempt proceeds. |
| **Acceptance criteria** | Plan P0-03 for share path: requested, denial explained, no crash. |
| **Evidence required** | Device record API 26-adjacent + API 33+ (can be same as G2 table). |
| **Status** | `DONE_TESTED` — `SharePermissions.required(sdk)` + `SharePermissionsSdkTest` / `BleBootstrapPermissionsTest`. Share screen requests. Device grant/deny: UNVERIFIED. |
| **Implementation record** | `SharePermissions` + `ShareAppScreen` `RequestMultiplePermissions`. API 33+: `NEARBY_WIFI_DEVICES`; else `ACCESS_FINE_LOCATION`. Rationale, deny, permanent deny → app settings. Location toggle check on API &lt; 33. ViewModel `phoneToPhoneBlocker` if started without grants. Bluetooth not requested for this path. |
| **Test result** | `SharePermissionsSdkTest` on JVM. Device deny/grant unrun. |
| **Remaining device verification** | P2P-MT-02, P2P-MT-03, P2P-MT-04. |
| **Evidence still required** | Fresh-install screenshots. |

### RFB-P2P-03 — Hotspot SecurityException, blank SSID, coroutine resume

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G2 / G3 |
| **Problem statement** | `startLocalOnlyHotspot` can throw `SecurityException`. Success path `cont.resume` lacks `isActive`. Blank SSID still shown as QR. |
| **Current behavior** | `LocalHotspotHelper` only maps callback `onFailed`. |
| **Desired behavior** | SecurityException → `HotspotStartException`; no double-resume; do not present Wi-Fi QR with blank SSID. |
| **Affected components/files** | `LocalHotspotHelper.kt`, `ShareAppScreen.kt` / ViewModel |
| **Dependencies** | RFB-P2P-02 (permissions reduce SecurityException rate). |
| **Implementation notes** | Guard `onStarted` with `cont.isActive`. Validate credentials before returning. |
| **Verification method** | Unit test for blank-SSID rejection if extracted; deny-permission device test. |
| **Acceptance criteria** | No process crash; no WIFI QR with empty S/P. |
| **Evidence required** | Test or logcat. |
| **Status** | `DONE_TESTED` — `LocalHotspotHelper.REASON_BLANK_SSID`, `cont.isActive` guards, SecurityException path. Blank-SSID / QR tests in `ShareFreezeTests` / `BootstrapTests`. Device hotspot: UNVERIFIED. |
| **Implementation record** | `startLocalOnlyHotspot` wrapped in `SecurityException` / `UnsupportedOperationException`. `onStarted` checks `cont.isActive` and rejects blank SSID (`REASON_BLANK_SSID`). `onStopped` notifies ViewModel if still serving. |
| **Test result** | `ApkSharePayload.buildWifiQrOrNull` rejects blank SSID. Live hotspot unrun. |
| **Remaining device verification** | Deny-permission + OEM hotspot fail. |
| **Evidence still required** | Logcat or device notes. |

### RFB-P2P-04 — Reachable install URL (never `localhost`)

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G3 |
| **Problem statement** | `getLocalIpAddress()` can pick the wrong interface or `localhost`. Server starts 300ms after hotspot, often before the AP interface exists. |
| **Current behavior** | Heuristic 192.168.43/49/4 then first IPv4 then localhost. |
| **Desired behavior** | Phone-to-phone URL is an IPv4 on the share link (hotspot or Direct GO). If none in time, fail with error — never advertise `http://localhost:8765/`. Wait/retry for the interface. |
| **Affected components/files** | `WifiDirectSharer.kt`, `ShareAppViewModel.kt` |
| **Dependencies** | RFB-P2P-02, RFB-P2P-03 (hotspot actually up). |
| **Implementation notes** | Prefer address on the interface that matches hotspot/Direct; bind order: start link, wait for IP, then `ServerSocket`. |
| **Verification method** | Two-phone: Device B browser loads Device A QR URL. |
| **Acceptance criteria** | QR host is not loopback; Device B can HTTP GET `/` on that URL while sharing. |
| **Evidence required** | Models, URL captured, transfer or landing-page load. |
| **Status** | `DONE_TESTED` — `ShareLinkAddresses.pickShareHost` never falls back to localhost (`ShareFreezeTests`). Device URL on hotspot/Direct: UNVERIFIED. |
| **Implementation record** | `ShareLinkAddresses.pickShareHost`: Direct `groupOwnerAddress` if IPv4; else newly appeared ap/p2p/wlan address; **never** loopback. Server waits up to 8s before bind. Same-LAN uses ConnectivityManager Wi‑Fi IPv4. No 192.168.43/49 prefix requirement. |
| **Test result** | `ShareLinkAddressesTest` (no localhost fallback). Two-phone HTTP unrun. |
| **Remaining device verification** | P2P-MT-05, P2P-MT-06. |
| **Evidence still required** | Photo of URL + Device B page load. |

### RFB-P2P-05 — Sidecar required or explicit warning

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G3 |
| **Problem statement** | If node not ready, sidecar is empty 200; recipient gets APK without join credentials. |
| **Current behavior** | `refreshBootstrapSidecar` nulls sidecar when `!MeshService.isNodeReady()`. |
| **Desired behavior** | Either block “sharing ready” until sidecar bytes exist, or UI states clearly that mesh auto-join will not happen. |
| **Affected components/files** | `ShareAppViewModel.kt`, `ShareAppScreen.kt`, `strings.xml` |
| **Dependencies** | Mesh node running (G2 startup). |
| **Implementation notes** | OFFLINE_DISTRIBUTION remaining gap “hotspot before node ready.” Prefer wait/retry over silent empty file. |
| **Verification method** | Start share immediately after launch: either wait until sidecar present or warning visible; `/mesh/bootstrap.myc` non-empty when claiming join. |
| **Acceptance criteria** | No silent empty join file while UI implies mesh share. |
| **Evidence required** | Screenshot or HTTP Content-Length of sidecar. |
| **Status** | `DONE_STATIC` — `sidecarReady` + `share_app_sidecar_unavailable` on `ShareAppScreen`. Device sidecar 404/join: UNVERIFIED. |
| **Implementation record** | Wait up to 10s for `MeshService.isNodeReady()`. Empty sidecar → 404 and UI warning. Invite QR shown when sidecar JSON has `invite_uri`. |
| **Test result** | Landing HTML + `OfflineBootstrapParseTest`. Live sidecar Content-Length unrun. |
| **Remaining device verification** | P2P-MT-08; share immediately after cold start. |
| **Evidence still required** | Screenshot of warning or non-empty `/mesh/bootstrap.myc`. |

### RFB-P2P-06 — Replace Downloads-directory sidecar import

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G3 |
| **Problem statement** | `OfflineBootstrap` lists public Downloads. No `READ_EXTERNAL_STORAGE` / media permissions. API 29+ scoped storage: Chrome downloads are not listable. Auto-onboarding is dead on targetSdk 34. |
| **Current behavior** | `tryImportFromDownloads` via `DIRECTORY_DOWNLOADS`. |
| **Desired behavior** | Recipient can apply invite/bootstrap without relying on listing another app’s Downloads. Options justified by audit: SAF picker, MediaStore if viable, copy via FileProvider from share session, first-run paste/scan of invite QR, or **drop auto-onboard** and document QR-only join (then update OFFLINE_DISTRIBUTION). |
| **Affected components/files** | `OfflineBootstrap.kt`, `OnboardingActivity.kt` / `MainActivity.kt`, `AndroidManifest.xml` (only if a real permission is required), landing HTML if flow changes |
| **Dependencies** | RFB-P2P-05, RFB-APK-01 (unknown-sources copy). RFB-PERS-03 if onboarding wipe destroys import. |
| **Implementation notes** | Do not add `MANAGE_EXTERNAL_STORAGE`. Prefer in-band invite (QR on Device A Connect after install) if file import stays unreliable. |
| **Verification method** | Device B: install from share, first launch joins or has a clear manual join path that was tested. |
| **Acceptance criteria** | G3 pass: “whether Device B auto-onboarded” recorded honestly; if auto is dropped, docs match. |
| **Evidence required** | Two-phone record. |
| **Status** | `DONE_STATIC` — `OfflineBootstrap.applyAfterWipe` from `OnboardingActivity`. Parse tests exist. Device import-then-onboard: UNVERIFIED. |
| **Implementation record** | Downloads listing kept as best-effort only. Primary: Device A Step 3 invite QR. Onboarding **I have a mesh join file** (SAF) applied **after** wipe (`applyAfterWipe`) so join prefs are not clobbered (RFB-PERS-03). |
| **Test result** | `OfflineBootstrapParseTest`. Two-phone join unrun. |
| **Remaining device verification** | P2P-MT-08. |
| **Evidence still required** | Whether Device B auto-onboarded (honest: likely via QR/SAF, not Downloads). |

### RFB-P2P-07 — Catch Errors on share start (defense in depth)

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G2 |
| **Problem statement** | ViewModel catches `Exception` only; `NoSuchMethodError` still kills the process until RFB-P2P-01. |
| **Current behavior** | `catch (e: Exception)`. |
| **Desired behavior** | Share start failures surface in UI even if an `Error` slips through remaining OEM APIs — **or** omit if P2P-01 is proven sufficient. |
| **Affected components/files** | `ShareAppViewModel.kt` |
| **Dependencies** | RFB-P2P-01 (primary fix). |
| **Implementation notes** | Catching `Throwable` can swallow `CancellationException` — rethrow cancellation. P1 not P0 if P2P-01 lands first. |
| **Verification method** | Code review + no crash on Direct fallback. |
| **Acceptance criteria** | Share failures are UI errors, not process death, for remaining known call sites. |
| **Evidence required** | Diff + optional logcat. |
| **Status** | `DONE_STATIC` — `ShareAppViewModel` rethrows `CancellationException`; other failures set `_error` and `cleanupPhoneToPhone()`. Device: UNVERIFIED. |
| **Implementation record** | `startSharing` catches `Throwable`, rethrows `CancellationException`. |
| **Test result** | Code review. Crash-free Direct still needs P2P-MT-01. |
| **Remaining device verification** | P2P-MT-01. |
| **Evidence still required** | Logcat on API 26 Direct fallback. |

### RFB-P2P-08 — Document or fix Wi-Fi pause fallback (API 29+)

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G3 / G4 |
| **Problem statement** | `WifiPauseHelper` uses deprecated `disconnect` / `enableNetwork`; often a no-op on modern Android; may still disrupt the user. |
| **Current behavior** | Pause then retry hotspot then Direct. |
| **Desired behavior** | Either skip pause on APIs where it cannot work, or document it as best-effort; keep Direct + Quick Share as real fallbacks. |
| **Affected components/files** | `WifiPauseHelper.kt`, `ShareAppViewModel.kt`, `docs/OFFLINE_DISTRIBUTION.md` |
| **Dependencies** | RFB-P2P-01 (Direct must not crash). |
| **Implementation notes** | Need **one** working share path (hotspot, Direct, same-LAN, or Quick Share + manual join). |
| **Verification method** | Device: hotspot fail path reaches Direct or Quick Share without leaving Wi-Fi unrestored when possible. |
| **Acceptance criteria** | Fallback chain described in OFFLINE_DISTRIBUTION matches code; at least one path works on the G3 pair. |
| **Evidence required** | Device notes. |
| **Status** | `DONE_TESTED` — `ShareTransportPreconditions.canPauseStaWifi` false on API 29+ (`ShareFreezeTests`). Device fallback chain: UNVERIFIED. |
| **Implementation record** | `WifiPauseHelper.pauseIfConnected()` no-ops on API 29+. Documented in OFFLINE_DISTRIBUTION. Fallback remains Direct then Quick Share. |
| **Test result** | `ShareTransportPreconditionsTest.canPauseStaWifi`. Device fallback unrun. |
| **Remaining device verification** | Hotspot fail → Direct or Quick Share on a phone. |
| **Evidence still required** | Device notes. |

### RFB-P2P-09 — Share lifecycle limitation (no FGS)

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G3 |
| **Problem statement** | HTTP share dies when the Share screen/ViewModel is cleared; OFFLINE_DISTRIBUTION lists missing FGS. Plan does not require a new FGS feature. |
| **Current behavior** | “Keep this screen open” string exists. |
| **Desired behavior** | Explicit limitation: leaving Share or process death stops the server. **Do not** implement share-FGS unless later promoted (that would be new product work → P2, out of default freeze). |
| **Affected components/files** | `docs/OFFLINE_DISTRIBUTION.md`, `docs/KNOWN_LIMITATIONS.md`, existing `share_app_keep_open_hint` |
| **Dependencies** | None. |
| **Implementation notes** | Documentation is the freeze action. |
| **Verification method** | Docs match `onCleared` / `stopSharing`. |
| **Acceptance criteria** | Not advertised as background APK server. |
| **Evidence required** | Diff. |
| **Status** | `DONE_STATIC` — documentation + existing `share_app_keep_open_hint`. No FGS added (by design). |
| **Implementation record** | OFFLINE_DISTRIBUTION + KNOWN_LIMITATIONS: share is not a background server. Keep-screen-on while serving only. No FGS added. |
| **Test result** | Doc review. |
| **Remaining device verification** | P2P-MT-10. |
| **Evidence still required** | Optional leave-screen observation. |

### RFB-P2P-10 — Blank Wi-Fi QR / HTTP hardening (P1 leftovers)

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G3 / G5 |
| **Problem statement** | Empty SSID QR (partially RFB-P2P-03). HTTP `trimIndent` LF headers; full-file `readBytes()`. |
| **Current behavior** | Browsers often tolerate LF; large APK uses RAM. |
| **Desired behavior** | CRLF headers and/or stream APK bytes if cheap; no empty WIFI QR (if not already in P2P-03). |
| **Affected components/files** | `WifiDirectSharer.kt` |
| **Dependencies** | RFB-P2P-03 for QR. |
| **Implementation notes** | Stream + CRLF are reliability, not new features. Extend `BootstrapTests` for CRLF if headers change. |
| **Verification method** | Unit test header contains `\r\n`; optional curl against local server. |
| **Acceptance criteria** | Tests updated if header format changes; no empty WIFI QR in UI. |
| **Evidence required** | `./gradlew testDebugUnitTest` log for bootstrap tests. |
| **Status** | `DONE_STATIC` — `ShareHttp` CRLF; APK `copyTo` stream; blank SSID has no WIFI QR. Gradle log on this tree: UNVERIFIED (UniFFI Kotlin generation). |
| **Implementation record** | `ShareHttp` CRLF headers; APK streamed with `copyTo`; blank SSID has no WIFI QR. |
| **Test result** | `./gradlew :app:testDebugUnitTest --tests io.github.mycena_project.app.bootstrap.*` was **not runnable** on this machine: `:uniffi-bindings:compileDebugKotlin` failed (`Unresolved reference: uniffi`). Bindings are generated by `scripts/regenerate-uniffi-kotlin.sh` and are not in this tree. Re-run after generating Kotlin. |
| **Remaining device verification** | None beyond P2P-MT-05 (no empty WIFI QR). |
| **Evidence still required** | Gradle test log. |

---

## 3. APK distribution / install

### RFB-APK-01 — Recipient install UX (unknown sources)

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G3 |
| **Problem statement** | Landing HTML does not mention allowing unknown apps. Device B has no Mycena yet — OS install prompt is the control. Users fail silently. |
| **Current behavior** | “Tap Install Mycena on the page” only. |
| **Desired behavior** | Landing page (and/or Device A on-screen steps) explains Chrome download + system “install unknown apps” for that browser. Malicious-APK warning already in OFFLINE_DISTRIBUTION — surface a short version on the page (official hash later = RFB-REL). |
| **Affected components/files** | `WifiDirectSharer.buildInstallLandingHtml`, `strings.xml` share steps, `docs/OFFLINE_DISTRIBUTION.md` |
| **Dependencies** | None. |
| **Implementation notes** | No `REQUEST_INSTALL_PACKAGES` on Device B (correct). |
| **Verification method** | Device B from factory-ish settings completes install using the written steps. |
| **Acceptance criteria** | G3 record includes whether unknown-sources was required and that copy was accurate. |
| **Evidence required** | Two-phone notes. |
| **Status** | `DONE_TESTED` — landing HTML / `share_app_step_install_body` covered in `BootstrapTests`. Device unknown-apps/Samsung Auto Blocker: UNVERIFIED. |
| **Implementation record** | Landing HTML + Device A Step 2 copy: unknown apps, Samsung Auto Blocker, Play Protect. SHA-256 on page and `/mycena.apk.sha256`. No install-permission on Device B. |
| **Test result** | `install_landing_includes_bootstrap_sidecar_path` asserts unknown-apps + Samsung strings. Device B install unrun. |
| **Remaining device verification** | P2P-MT-07, P2P-MT-16. |
| **Evidence still required** | Two-phone notes. |

### RFB-APK-02 — FileProvider: copy APK to cache, drop `root-path`

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G8 / G3 |
| **Problem statement** | `file_paths.xml` `<root-path path="." />` can expose the filesystem if a URI is granted. Used so Quick Share can attach `/data/app/.../base.apk`. |
| **Current behavior** | `shareApkFile` uses `applicationInfo.sourceDir` + FileProvider. |
| **Desired behavior** | Copy APK to cache and use `<cache-path>` (or files-path). No root-path. |
| **Affected components/files** | `res/xml/file_paths.xml`, `ShareIntent.kt` |
| **Dependencies** | None. |
| **Implementation notes** | Audit AUD-P2P-18. Same source bytes as HTTP share (`packageCodePath` / `sourceDir`). |
| **Verification method** | Quick Share / Files still attaches an APK; `file_paths.xml` has no `root-path`. |
| **Acceptance criteria** | Manifest provider paths are cache/files only. |
| **Evidence required** | Diff; optional device Quick Share smoke. |
| **Status** | `DONE_STATIC` — `file_paths.xml` is only `<cache-path path="apk-share/" />`. `ShareApkArtifact` tests exist. Device Quick Share: UNVERIFIED. |
| **Implementation record** | `file_paths.xml` is `<cache-path path="apk-share/" />` only. `ShareApkArtifact.materializeInstalledApk` copies `sourceDir` when not split. |
| **Test result** | `ShareApkArtifactTest` split detection. Quick Share on device unrun (P2P-MT-14). |
| **Remaining device verification** | P2P-MT-14. |
| **Evidence still required** | Confirm Files/Quick Share attaches APK. |

### RFB-APK-03 — Universal APK only for freeze share

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G3 / G9 |
| **Problem statement** | Completeness is true for debug universal APK; false for Play split installs. |
| **Current behavior** | Gradle `abiFilters` three ABIs, no ABI splits. Play workflow is AAB. |
| **Desired behavior** | Freeze testers and docs use GitHub/debug universal APK only (RFB-DOC-03). No code to “assemble splits into one APK” unless RFB-REL-01 chooses a new artifact. |
| **Affected components/files** | Docs primarily; `app/build.gradle.kts` only if introducing splits (do **not** add splits for freeze). |
| **Dependencies** | RFB-DOC-03, RFB-REL-01. |
| **Implementation notes** | Do not change Play AAB workflow except docs. Share path **refuses** split installs (`ShareApkArtifact.isSplitInstall`) instead of serving base.apk. |
| **Verification method** | `aapt dump badging` or unzip APK shows lib/armeabi-v7a and arm64-v8a. |
| **Acceptance criteria** | Shared/released freeze APK installs on matrix ABI devices without missing-split. |
| **Evidence required** | APK listing or install success on arm64 device. |
| **Status** | `DONE_STATIC` — Share refuses splits and debug; Technical Beta is universal `assembleRelease`. Device ABI install: UNVERIFIED. |

---

## 4. Android permissions

### RFB-PERM-01 — Share Nearby / location (canonical)

Same work as **RFB-P2P-02**. Track progress there. Do not duplicate implementation.

**Status:** `DONE_TESTED` (alias of P2P-02 JVM permission lists). Device grant/deny remains `UNVERIFIED`.

### RFB-PERM-02 — Permission-deny paths do not crash (G2 matrix)

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G2 |
| **Problem statement** | Plan G2 requires deny paths for notifications, BLE, camera, share/install-related OS dialogs. |
| **Current behavior** | Notifications requested in `MainActivity`; BLE in Settings; camera in Peers; share deny/permanent-deny UI implemented (P2P-02). Device deny table empty. |
| **Desired behavior** | Recorded deny tests: notifications off → no crash; BLE deny → skipped with UI/log; camera deny → QR message; share deny after P2P-02. |
| **Affected components/files** | Existing launchers; evidence record |
| **Dependencies** | RFB-P2P-02. |
| **Implementation notes** | Mostly verification after P2P-02. Share deny/permanent-deny UI is implemented; G2 table still needs two API levels recorded. |
| **Verification method** | Plan G2 table on two API levels. |
| **Acceptance criteria** | No crash on deny; user-visible explanation where the flow is blocked. |
| **Evidence required** | Device/OS/SHA table. |
| **Status** | `UNVERIFIED` — deny-path UI exists in code; G2 two-API-level device table empty. |

---

## 5. Samsung / OEM robustness

### RFB-OEM-01 — Samsung validation or public exception

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G4 |
| **Problem statement** | G4 cannot pass silently. No One UI data; KNOWN_LIMITATIONS does not say Samsung untested. |
| **Current behavior** | Generic OEM hotspot strings only. |
| **Desired behavior** | (a) Named Samsung model: cold start after idle, share hotspot or documented fallback, notification permission, unknown-app install; **or** (b) KNOWN_LIMITATIONS: Samsung untested; do not imply Samsung support. |
| **Affected components/files** | Hardware evidence note; possibly `docs/KNOWN_LIMITATIONS.md` |
| **Dependencies** | RFB-P2P-* for share to be testable; RFB-CRASH-01 for startup. |
| **Implementation notes** | Plan: P1-unless-accepted. Prefer (a) if a device exists. Landing page already explains Auto Blocker / unknown apps / Play Protect (no bypass). That copy does **not** satisfy G4 without a named device or a KNOWN_LIMITATIONS Samsung-untested paragraph. |
| **Verification method** | G4 evidence table or published exception. |
| **Acceptance criteria** | Plan G4 pass criteria (a) or (b). |
| **Evidence required** | Model + One UI version + results, or limitation paragraph. |
| **Status** | `DONE_STATIC` — option (b) published: KNOWN_LIMITATIONS Samsung **not hardware-verified**. Option (a) device table: UNVERIFIED. Not a G4 pass. |
| **Implementation record** | 2026-08-29: [SAMSUNG_STABILITY_AUDIT.md](./SAMSUNG_STABILITY_AUDIT.md); KNOWN_LIMITATIONS now states Samsung is **not hardware-verified** (option b until a device table exists). Static crash hardening is **not** a G4 pass. |
| **Test result** | No Samsung device in this task. |
| **Remaining device verification** | Audit OEM-MT-03, 06, 07 plus G4 table **or** keep the published exception. |
| **Evidence still required** | Named model + One UI + SHA, or accept limitation (b) at freeze sign-off. |

### RFB-OEM-02 — Characterize FGS kill / doze (no new OEM APIs required)

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G4 / G6 |
| **Problem statement** | `START_STICKY` only; no ignore-battery UX (audit: optional, Play-sensitive). Mesh may die after OEM sleep. |
| **Current behavior** | Uncharacterized. |
| **Desired behavior** | Record what happens after hours idle on at least one OEM (Samsung if OEM-01(a)). If killed: document limitation. Do **not** add `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` unless evidence shows P0 and product accepts Play risk (that would be a new capability → treat as P2 extra, not default). |
| **Affected components/files** | Evidence; `docs/KNOWN_LIMITATIONS.md` if failure |
| **Dependencies** | RFB-OEM-01 device or another matrix phone. |
| **Implementation notes** | Characterization, not a new battery-exemption feature. |
| **Verification method** | Idle test notes. |
| **Acceptance criteria** | G4 idle row filled or skipped with disposition. |
| **Evidence required** | Timestamped notes. |
| **Status** | `UNVERIFIED` — no idle/doze device record. No battery-exemption API added. |
| **Implementation record** | No battery-exemption API added. Idle kill remains **NEEDS RUNTIME** (OEM-MT-08). Diagnostics tag `MycenaStability` if the process dies uncaught. |

### RFB-OEM-03 — Remaining Samsung verification after static hardening

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G4 |
| **Problem statement** | Static mitigations must not be confused with device proof. |
| **Current behavior** | Code: ContextCompat receivers, null-safe P2P, BLE SecurityException, API 34 FGS type, start-failure UI, stability logs. |
| **Desired behavior** | Fill OEM-MT-01–09 **and** [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md) Samsung rows on a named Galaxy **or** keep VERIFIED ON REAL SAMSUNG HARDWARE empty. |
| **Affected components/files** | Evidence; [SAMSUNG_STABILITY_AUDIT.md](./SAMSUNG_STABILITY_AUDIT.md) |
| **Dependencies** | RFB-OEM-01. |
| **Implementation notes** | Do not mark hardware verified without logcat + model. |
| **Verification method** | Audit manual table. |
| **Acceptance criteria** | Each OEM-MT row pass / fail-with-limitation / skip-with-disposition. |
| **Evidence required** | Filled table. |
| **Status** | `UNVERIFIED` — static hardening in tree ([SAMSUNG_STABILITY_AUDIT.md](./SAMSUNG_STABILITY_AUDIT.md)); OEM-MT rows unrun. |
| **Implementation record** | 2026-08-29: static hardening + `MycenaStability` logs exist; OEM-MT table in the audit is unfilled. Additional null-safe BLE/NSD/location service casts and BLE stop `SecurityException` handling landed the same day. |
| **Test result** | No Samsung device in this task. |

---

## 6. Networking

### RFB-NET-01 — Surface MeshService node init failure (no infinite spinner)

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G2 |
| **Problem statement** | `initNode` catch logs and returns `START_STICKY` without `nodeReady`; UI loops forever. |
| **Current behavior** | `MainActivity` `while (!nodeReady) delay(50)`. |
| **Desired behavior** | User-visible error and retry; do not spin indefinitely. |
| **Affected components/files** | `MeshService.kt`, `MainActivity.kt` |
| **Dependencies** | None. |
| **Implementation notes** | Audit AUD-CR-02 / AUD-S01. |
| **Verification method** | Inject/simulate failure if possible; else code review + happy-path cold start twice. |
| **Acceptance criteria** | Failed init is not an infinite progress indicator. |
| **Evidence required** | Code + G2 cold-start table (happy path). |
| **Status** | `DONE_STATIC` — `MeshService.nodeFailed` + `MainActivity` 15s timeout/retry. Device init-failure: UNVERIFIED. |
| **Implementation record** | `MeshService.nodeFailed` + 15s timeout + retry in `MainActivity`. Infinite spinner removed in code. Happy-path cold start still **NEEDS RUNTIME**. |
| **Test result** | Not run on device. Gradle unit tests blocked on UniFFI in this clone. |
| **Remaining device verification** | OEM-MT-02 / G2 cold start. |
| **Evidence still required** | Two cold starts on a phone. |

### RFB-NET-02 — LAN mesh / QR chat (hardware proof, not new protocol)

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G6 |
| **Problem statement** | Core LAN/QR path is unverified (LEVEL0 A2 pending). No protocol change requested. |
| **Current behavior** | Code exists; matrix empty. |
| **Desired behavior** | mvp-smoke-test A2-1, A2-5, B1 executed on recorded devices. |
| **Affected components/files** | Evidence; `docs/LEVEL0_ROADMAP.md` A2 sign-off |
| **Dependencies** | RFB-NET-01 (app must start); RFB-HW-01. |
| **Implementation notes** | Run existing app; do not add transports. |
| **Verification method** | Plan §5.1 rows. |
| **Acceptance criteria** | Pass, fail-with-limitation, or skip-with-disposition — never “should work.” |
| **Evidence required** | Filled matrix. |
| **Status** | `UNVERIFIED` — LAN/QR pairing matrix empty. |

---

## 7. Persistence / recovery

### RFB-PERS-01 — Panic UI: mesh copies remain; fail if wipe incomplete

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G2 |
| **Problem statement** | Confirm copy omits “copies on other devices remain.” Failed `deleteRecursively` still continues to relaunch. |
| **Current behavior** | `settings_panic_confirm_body` “cannot be undone” only. |
| **Desired behavior** | User-visible local-only limitation (plan P0-09). If DB still exists after retries, do not claim success. |
| **Affected components/files** | `strings.xml` (+ locales), `LocalDataWiper.kt`, `SettingsScreen.kt` `performPanicWipe` |
| **Dependencies** | None. |
| **Implementation notes** | Match KNOWN_LIMITATIONS / threat model §2.1. |
| **Verification method** | UI copy review; device panic: identity gone, app launchable. |
| **Acceptance criteria** | Mesh-copies sentence visible; incomplete wipe is an error. |
| **Evidence required** | Screenshot + G2 panic row. |
| **Status** | `DONE_TESTED` — confirm copy includes this-device wipe, cannot be undone, and copies on other mesh devices remain. Incomplete `dbDir` delete does not relaunch; Settings shows failure. `LocalDataWiperTest` PASS. HV-18 **UNVERIFIED**. |

### RFB-PERS-02 — Restart persistence (A2-5)

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G6 |
| **Problem statement** | Contacts/keys/chat survive restart — unverified. |
| **Current behavior** | sled + vault implemented. |
| **Desired behavior** | Force-quit both apps; chat still encrypts without re-scan. |
| **Affected components/files** | Evidence only unless a bug is found. |
| **Dependencies** | RFB-NET-02 pairing first. |
| **Implementation notes** | Hardware, not a new store. |
| **Verification method** | mvp-smoke-test A2-5. |
| **Acceptance criteria** | Matrix row. |
| **Evidence required** | Device record. |
| **Status** | `UNVERIFIED` — sled/vault in tree; restart persistence not device-proven. |

### RFB-PERS-03 — Onboarding wipe vs imported bootstrap

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G3 |
| **Problem statement** | Completing `OnboardingActivity` calls `wipeAllLocalData` then writes display name — destroys imported sidecar prefs if user did not skip. |
| **Current behavior** | Skip only if import sets `onboarding_complete` first. |
| **Desired behavior** | If RFB-P2P-06 still uses prefs for invite, do not wipe them on first complete; or skip onboarding when invite present. |
| **Affected components/files** | `OnboardingActivity.kt`, `OfflineBootstrap.kt` |
| **Dependencies** | RFB-P2P-06. |
| **Implementation notes** | Only needed if auto-join still uses SharedPreferences. |
| **Verification method** | Sideload path does not delete invite before apply. |
| **Acceptance criteria** | Import then first-run cannot clobber join data. |
| **Evidence required** | Device or unit-level prefs test. |
| **Status** | `DONE_STATIC` — onboarding wipe then `applyAfterWipe` if a join file was picked. Device: UNVERIFIED. |
| **Implementation record** | Onboarding wipe then `OfflineBootstrap.applyAfterWipe` if a join file was picked. Downloads auto-onboard still sets `onboarding_complete` before MainActivity so wipe is skipped on that path. |
| **Test result** | Parse tests only; wipe order is code review. |
| **Remaining device verification** | Pick `.myc` during onboarding, confirm bootstrap peers survive. |
| **Evidence still required** | Device or prefs dump. |

### RFB-PERS-04 — Reinstall keep-vs-wipe documented and tested

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G6 |
| **Problem statement** | Plan P1: `adb install -r` data disposition undocumented/untested. Debug vs Play keys are different trust roots. |
| **Current behavior** | `WelcomeBackActivity` / `IdentityHelper` exist. |
| **Desired behavior** | Short doc: same signing key keeps data; uninstall loses data (`allowBackup=false`); Play vs debug cannot update in place. One `install -r` test on matrix. |
| **Affected components/files** | `android/README.md` or `docs/OFFLINE_DISTRIBUTION.md`; evidence |
| **Dependencies** | RFB-REL-01 (which key the freeze APK uses). |
| **Implementation notes** | Do not add in-app updater. |
| **Verification method** | `adb install -r` on one device (HV-16 / HV-17 in [HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md)). |
| **Acceptance criteria** | Written disposition + one test. |
| **Evidence required** | Command output + data still present or wiped as documented. |
| **Status** | `DONE_STATIC` (docs) — keep-vs-wipe table in [OFFLINE_DISTRIBUTION.md](./OFFLINE_DISTRIBUTION.md). Device `adb install -r`: UNVERIFIED. |

---

## 8. Automated tests

### RFB-TEST-01 — Record CI on freeze-candidate SHA

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G5 |
| **Problem statement** | Workflows exist; no job URLs for audited SHA. Plan forbids claiming tests passed without logs. |
| **Current behavior** | Unverified. |
| **Desired behavior** | On the SHA that will be tagged (or a listed freeze candidate): green `ci.yml`, `android-ci.yml` (build + `testDebugUnitTest`), `fuzz-pr.yml`, `secret-scan.yml`; or waivers. Also record `./scripts/check-dependencies.sh`. |
| **Affected components/files** | Evidence note (e.g. `docs/freeze-evidence/` — create when recording, not in this task) |
| **Dependencies** | Code P0s that must be in that SHA (P2P-01, NET-01, etc.). |
| **Implementation notes** | Android CI path filters: include Android paths or `MYCENA_CI_TIER=full` so Android job actually runs. |
| **Verification method** | URLs + SHA match. |
| **Acceptance criteria** | Plan G5 pass criteria. |
| **Evidence required** | Job URLs or full command logs. |
| **Status** | `UNVERIFIED` — no freeze-SHA CI job URLs. |

### RFB-TEST-02 — Regression tests for freeze P0 Android bugs

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G5 |
| **Problem statement** | Plan: new P0 crashes need a regression test or documented device-only reason. Share tests are header/QR only. |
| **Current behavior** | `BootstrapTests` HTTP content-type and WIFI QR; instrumentation is manifest-only. |
| **Desired behavior** | Tests for: IP helper never returns localhost for share URL construction (if extracted); Direct register path doesn’t use API 33-only call unsafely (if testable on JVM); blank SSID rejected. Device-only: registerReceiver — document emulator API 26. |
| **Affected components/files** | `android/app/src/test/...`, maybe extract pure functions from `WifiDirectSharer` |
| **Dependencies** | RFB-P2P-01, RFB-P2P-04. |
| **Implementation notes** | Do not require connectedDebugAndroidTest as a freeze blocker beyond documenting why (G2 already says instrumentation is thin). |
| **Verification method** | `./scripts/android-test.sh` (generates UniFFI then `testDebugUnitTest`). Gradle-only fails closed if bindings are missing. |
| **Acceptance criteria** | At least one new unit test per extractable P0 helper, or written device-only waiver. |
| **Evidence required** | Gradle test log. |
| **Status** | `DONE_TESTED` — JVM suite runnable from documented clean-checkout path (`./scripts/android-test.sh`). Device registerReceiver still UNVERIFIED (P2P-MT-01). |
| **Implementation record** | `ShareFreezeTests` + `BootstrapTests`. UniFFI Kotlin remains gitignored. `uniffi-bindings` `requireUniffiKotlin` fails with generate/`android-test.sh`/`android/build.sh` instructions. CI still generates via `android/build.sh` then tests (unchanged). |
| **Test result** | 2026-08-29: missing-bindings Gradle fail-closed recorded; `./scripts/android-test.sh` (or equivalent generate + `testDebugUnitTest`) recorded in this task. |
| **Remaining device verification** | P2P-MT-01. |
| **Evidence still required** | CI job URL on freeze SHA (RFB-TEST-01). |

### RFB-TEST-03 — Confirm SD-030 / SD-081 still in CI

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G5 / G7 |
| **Problem statement** | Audit PASS on code; freeze SHA must still run them. |
| **Current behavior** | `sd030_unsigned_drop.rs`; `enc_key_not_yet_exchanged` / `send_chat_encrypted_requires_peer_key` in app crate; budget CI security regression job exists. **2026-08-29 local run** recorded in [SECURITY_RELEASE_REVIEW.md](./SECURITY_RELEASE_REVIEW.md). Freeze SHA **CI job URL** still missing. |
| **Desired behavior** | Freeze SHA log includes those tests (or nextest workspace run covering them). |
| **Affected components/files** | Evidence; no code unless tests were dropped from CI. |
| **Dependencies** | RFB-TEST-01. |
| **Implementation notes** | Do not reimplement mitigations. |
| **Verification method** | CI log grep. |
| **Acceptance criteria** | Tests executed on freeze SHA. |
| **Evidence required** | Log excerpt. |
| **Status** | `UNVERIFIED` — local G7 cargo tests recorded in SECURITY_RELEASE_REVIEW; freeze-SHA CI URL missing. |

---

## 9. Hardware validation

### RFB-HW-01 — Fill freeze hardware matrix (minimum scenarios)

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G6 |
| **Problem statement** | Plan §5.1 template empty. Cannot claim testable beta. |
| **Current behavior** | Pixel 6 battery only. |
| **Desired behavior** | Rows: HV-01–20 in [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md) (includes A2-1, A2-2 if relay available, A2-5, B1, G3 share, Panic, Reinstall; G4 Samsung or skip). Device, OS, SHA, pass/fail, logs/hashes. |
| **Affected components/files** | [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md); optional `docs/freeze-evidence/` summary |
| **Dependencies** | P0 code: P2P-*, NET-01, PERS-01, APK-01/02, REL-01 artifact. |
| **Implementation notes** | Procedure: [HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md). Capture: `./scripts/android/capture-freeze-evidence.sh`. Also mvp-smoke-test Phase A/B. Two physical phones required for G3/LAN. The **empty template is not a G6 pass**. |
| **Verification method** | Plan evidence rules (no “looks good”). |
| **Acceptance criteria** | Every in-scope scenario pass / fail-with-limitation / skip-with-disposition. |
| **Evidence required** | Filled table. |
| **Status** | `UNVERIFIED` — [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md) all rows `unrun`. |

### RFB-HW-02 — LEVEL0 A2 manual sign-off

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G6 |
| **Problem statement** | A2 is “Done (doc) — manual sign-off pending.” |
| **Current behavior** | Unsigned. |
| **Desired behavior** | Sign off Mac+Android **or** Android-Android pair with models recorded; update LEVEL0 A2. |
| **Affected components/files** | `docs/LEVEL0_ROADMAP.md` |
| **Dependencies** | RFB-HW-01 overlapping scenarios. |
| **Implementation notes** | Desktop optional if freeze is Android-first; then document Mac unsigned. |
| **Verification method** | Checklist in mvp-smoke-test Phase A. |
| **Acceptance criteria** | Status no longer “pending” without a named exception. |
| **Evidence required** | Dated sign-off in LEVEL0 or evidence file. |
| **Status** | `UNVERIFIED` — LEVEL0 A2 manual sign-off still pending. |

### RFB-HW-03 — BLE airplane-mode two-phone drill or accept RFB-DOC-05

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G6 |
| **Problem statement** | Disaster mesh 2.4–2.5 unverified. |
| **Current behavior** | Code only. |
| **Desired behavior** | Two phones airplane + BLE chat within documented time, **or** RFB-DOC-05 remains the user-facing truth. |
| **Affected components/files** | Evidence; Settings BLE permissions already exist |
| **Dependencies** | RFB-PERM-02 BLE grant on both devices. |
| **Implementation notes** | Do not add new BLE protocol work. |
| **Verification method** | DISASTER_MESH 2.4–2.5 exit criteria. |
| **Acceptance criteria** | Matrix BLE-air row or documented unverified. |
| **Evidence required** | Device record or DOC-05. |
| **Status** | `UNVERIFIED` — no BLE-air two-phone record. DOC-05 still incomplete (Good fit line). |

### RFB-HW-04 — Release verification harness (docs + capture script)

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G2 / G6 (infrastructure; does not pass the gates) |
| **Problem statement** | Plan §5.1 was an empty table with no runbook; another developer could not reproduce freeze validation. |
| **Current behavior** | Specialized P2P-MT / OEM-MT / mvp-smoke-test docs existed; no single HV catalog or log-capture path. |
| **Desired behavior** | Runbook + matrix + crash reporting + a small adb capture script. Matrix remains **unfilled** until devices are run. |
| **Affected components/files** | `docs/HARDWARE_TEST_RUNBOOK.md`, `docs/HARDWARE_TEST_MATRIX.md`, `docs/CRASH_REPORTING.md`, `scripts/android/capture-freeze-evidence.sh` |
| **Dependencies** | None. |
| **Implementation notes** | Do not duplicate P2P-MT/OEM-MT procedures; link them. Logs under `artifacts/hardware-runs/` (gitignored). |
| **Verification method** | Docs review; `capture-freeze-evidence.sh --help`; `make android-hw-info` needs a device. |
| **Acceptance criteria** | A new developer can follow the runbook end-to-end on paper; HV-01–20 defined; Pixel + Samsung identity slots exist and are empty. |
| **Evidence required** | This item’s files on the freeze SHA. |
| **Status** | `DONE_STATIC` — runbook, matrix template, crash reporting, `scripts/android/capture-freeze-evidence.sh`. Not executed on a device. |
| **Implementation record** | 2026-08-29: harness docs + capture script. **No hardware run.** |
| **Test result** | Not a device test. |
| **Remaining device verification** | RFB-HW-01 (fill the matrix). |

---

## 10. Security

### RFB-SEC-01 — Freeze-accept or sign ACCEPTED_SECURITY_RESIDUALS

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G7 |
| **Problem statement** | Residuals unsigned; Play would require signatures. GitHub freeze may explicitly defer sign-off. |
| **Current behavior** | Empty names; “not a release certificate.” GitHub-only freeze paragraph dated 2026-08-29 (maintainers); Play still requires names. |
| **Desired behavior** | Written freeze disposition: GitHub-only beta accepts R1–R10 unsigned **with owner/date**, or names filled. Must not be described as certification. |
| **Affected components/files** | `docs/ACCEPTED_SECURITY_RESIDUALS.md` |
| **Dependencies** | RFB-DOC-01 C16. |
| **Implementation notes** | Plan P1-06. No new crypto work. |
| **Verification method** | Doc review. |
| **Acceptance criteria** | Not an empty table presented as approval. |
| **Evidence required** | Diff with names or explicit deferral paragraph. |
| **Status** | `DONE_STATIC` — GitHub-only unsigned R1–R10 paragraph dated 2026-08-29. Not a certificate. Table still unsigned. |

### RFB-SEC-02 — Dated internal review vs threat-model §8

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G7 |
| **Problem statement** | Audit is G1 inventory, not a TM reviewer-checklist sign-off. |
| **Current behavior** | [SECURITY_RELEASE_REVIEW.md](./SECURITY_RELEASE_REVIEW.md) dated 2026-08-29 vs HEAD `fb32b1e`. |
| **Desired behavior** | One dated page: snapshot of SECURITY_DEBT_REGISTER, P0 mitigations not regressed (point at TEST-03), preview MXC still preview, no new Critical for preview product. |
| **Affected components/files** | Short `docs/` note or section in freeze-evidence |
| **Dependencies** | RFB-TEST-01, RFB-TEST-03. |
| **Implementation notes** | Not EXTERNAL_SECURITY_REVIEW commissioning (that is RFB-SEC-03). Freeze plan says “TM §8”; the actual checklist is **THREAT_MODEL §9** (§8 is the mapping table). |
| **Verification method** | Checklist against THREAT_MODEL **§9** items in scope. |
| **Acceptance criteria** | Plan G7 pass criteria for internal review. |
| **Evidence required** | Dated SHA + register date. |
| **Status** | `DONE_STATIC` — [SECURITY_RELEASE_REVIEW.md](./SECURITY_RELEASE_REVIEW.md) dated 2026-08-29. G7 gate still not passed. |

### RFB-SEC-03 — External review scheduled or explicitly deferred

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G7 |
| **Problem statement** | Pack exists; not commissioned; PRE_RELEASE_GAPS wants schedule for Play. |
| **Current behavior** | `EXTERNAL_SECURITY_REVIEW.md`: not commissioned; GitHub-only beta **explicitly deferred** 2026-08-29 (maintainers). Play still needs schedule. |
| **Desired behavior** | One sentence: deferred for GitHub technical beta (owner/date) **or** engagement scheduled. Align C14 pack version with TM v1.1 when touching the pack. |
| **Affected components/files** | `docs/EXTERNAL_SECURITY_REVIEW.md`, `docs/PRE_RELEASE_GAPS.md` |
| **Dependencies** | Product: GitHub vs Play (C15). |
| **Implementation notes** | Do not fake an audit. |
| **Verification method** | Doc. |
| **Acceptance criteria** | Not left as “pending” with no owner. |
| **Evidence required** | Diff. |
| **Status** | `DEFERRED` — external review not scheduled; owner/date recorded. Pack TM v1.1 / checklist §9. |

### RFB-SEC-04 — Secret-scan evidence on freeze SHA

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G8 / G5 |
| **Problem statement** | Tracked tree looks clean; history not evidenced. |
| **Current behavior** | `secret-scan.yml` exists. |
| **Desired behavior** | Gitleaks job URL on freeze SHA. |
| **Affected components/files** | Evidence |
| **Dependencies** | RFB-TEST-01 (same SHA). |
| **Implementation notes** | If history hits, remediate (rewrite is out of scope unless a real secret is found — then P0). |
| **Verification method** | Workflow green. |
| **Acceptance criteria** | Plan P0-06 with scan evidence. |
| **Evidence required** | Job URL. |
| **Status** | `UNVERIFIED` — `.github/workflows/secret-scan.yml` has a gitleaks job; no job URL on freeze SHA. |

---

## 11. Supply chain

### RFB-SC-01 — Record dependency/license gate; refresh SD-150

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G7 |
| **Problem statement** | `check-dependencies.sh` not recorded. SD-150 still lists SBOM/repro/signing as missing though scripts exist. Allowlist `review_by` 2026-09-30. |
| **Current behavior** | CI has the script. SD-150 updated (SBOM/repro/M-of-N; cargo vet still missing). Allowlist `review_by` **2026-09-30** — `./scripts/check-advisory-allowlist.sh` **OK** 2026-08-29. Full `./scripts/check-dependencies.sh` **not** recorded this session. |
| **Desired behavior** | Log of `./scripts/check-dependencies.sh` on freeze SHA. SD-150 missing-mitigations updated: SBOM/repro/M-of-N exist; `cargo vet` still absent; Dependabot absent until RFB-SC-02. |
| **Affected components/files** | `docs/SECURITY_DEBT_REGISTER.md`, evidence |
| **Dependencies** | RFB-TEST-01. |
| **Implementation notes** | Do not implement cargo-vet for freeze unless chosen in SC-02 (P2 to add vet). |
| **Verification method** | Script exit 0 or documented ignores. |
| **Acceptance criteria** | SD-150 matches reality; allowlist not expired without review. |
| **Evidence required** | Command log + debt-row diff. |
| **Status** | `UNVERIFIED` — allowlist `review_by` 2026-09-30 checked OK in a prior session; full `./scripts/check-dependencies.sh` not recorded. |

### RFB-SC-02 — Dependabot (or explicit reject) at public launch

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G8 |
| **Problem statement** | SD-150 promised Dependabot at public launch; no `dependabot.yml`. |
| **Current behavior** | `.github/dependabot.yml` present (2026-08-29). GitHub **settings** may still have Dependabot off. |
| **Desired behavior** | Add `.github/dependabot.yml` **or** update SD-150: not enabling for freeze, with reason (CI minutes, review capacity). |
| **Affected components/files** | `.github/dependabot.yml` and/or `SECURITY_DEBT_REGISTER.md` |
| **Dependencies** | RFB-HYG-01 (public switch). |
| **Implementation notes** | cargo-vet remains optional P2. Enable Dependabot in GitHub when visibility is Public. |
| **Verification method** | File exists or debt row says won’t enable. |
| **Acceptance criteria** | No contradiction with SD-150. |
| **Evidence required** | Diff. |
| **Status** | `DONE_STATIC` — `.github/dependabot.yml` present. GitHub Dependabot **setting** UNVERIFIED. |
| **Implementation record** | 2026-08-29: `dependabot.yml` + SD-150 rewrite. Remaining: turn Dependabot **on** in GitHub at public launch. |

---

## 12. Repository hygiene

### RFB-HYG-01 — CI_PUBLIC_TRANSITION day-of (or waiver)

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G8 |
| **Problem statement** | Checklist unchecked. `MYCENA_CI_TIER` budget vs full. |
| **Current behavior** | Docs describe the switch. |
| **Desired behavior** | When going public: fill checklist or waive items with reason; set `MYCENA_CI_TIER=full` or document remaining budget mode while private. |
| **Affected components/files** | `docs/CI_PUBLIC_TRANSITION.md` (checkboxes), GitHub repo settings (not in git) |
| **Dependencies** | RFB-DOC-01; RFB-SEC-04. |
| **Implementation notes** | OSS-Fuzz submit stays P2/deferred. |
| **Verification method** | Checklist + public clone smoke. |
| **Acceptance criteria** | Plan G8 pass criteria. |
| **Evidence required** | Filled checklist screenshot or markdown ticks with date. |
| **Status** | `OPEN` — [PUBLIC_REPOSITORY_AUDIT.md](./PUBLIC_REPOSITORY_AUDIT.md) CONDITIONALLY READY; day-of CI_PUBLIC_TRANSITION boxes unchecked. |
| **Implementation record** | 2026-08-29: [PUBLIC_REPOSITORY_AUDIT.md](./PUBLIC_REPOSITORY_AUDIT.md) verdict **CONDITIONALLY READY**. Day-of boxes still unchecked; visibility/`MYCENA_CI_TIER` not flipped in this task. |

### RFB-HYG-02 — Stale `mycena-android` path in Android CI filter

| Field | Content |
|-------|---------|
| **Priority** | P2 |
| **Release gate** | G5 |
| **Problem statement** | `android-ci.yml` filters `crates/mycena-android/**`; crate does not exist. |
| **Current behavior** | Harmless extra filter. |
| **Desired behavior** | Remove dead path or add a comment. |
| **Affected components/files** | `.github/workflows/android-ci.yml` (and `ci.yml` android filter if present) |
| **Dependencies** | None. |
| **Implementation notes** | Hygiene only. |
| **Verification method** | Diff. |
| **Acceptance criteria** | No phantom crate path without comment. |
| **Evidence required** | Diff. |
| **Status** | `DONE_STATIC` — `crates/mycena-android/**` removed from `android-ci.yml` and `ci.yml` path filters. |
| **Implementation record** | 2026-08-29: removed `crates/mycena-android/**` from `android-ci.yml` and `ci.yml` path filters. |

### RFB-HYG-03 — Do not commit `local.properties`; stash discipline

| Field | Content |
|-------|---------|
| **Priority** | P1 |
| **Release gate** | G8 |
| **Problem statement** | Local SDK path file exists untracked; stash may contain unpublished share deltas. |
| **Current behavior** | gitignored; stash local. |
| **Desired behavior** | Maintainers do not `git add` local.properties; do not `stash apply` as a feature dump without review. |
| **Affected components/files** | Process; WIP doc (RFB-DOC-06) |
| **Dependencies** | RFB-DOC-06. |
| **Implementation notes** | No new ignore rules required if already gitignored. |
| **Verification method** | `git ls-files android/local.properties` empty on freeze SHA. |
| **Acceptance criteria** | Same as audit PASS for tracked secrets. |
| **Evidence required** | `git ls-files` output. |
| **Status** | `DONE_STATIC` — `android/local.properties` not tracked. Stash apply discipline remains a maintainer process item (RFB-DOC-06). |
| **Implementation record** | 2026-08-29 public audit: `git ls-files android/local.properties` empty; file gitignored. Stash discipline remains a maintainer process item. |

---

## 13. Release engineering

### RFB-REL-01 — Decide freeze APK signing (debug vs release-signed universal)

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G9 |
| **Problem statement** | Reproducible pipeline copies `assembleDebug`. Must be an explicit freeze decision. |
| **Current behavior** | 2026-08-29 RC: local `assembleRelease` APK exists ([RELEASE_CANDIDATE.md](./RELEASE_CANDIDATE.md)); **not** debug-signed. CI / `reproducible-build.sh` still copy `assembleDebug`. |
| **Desired behavior** | **Either** document debug-signed technical beta as the official freeze artifact **or** change `android/build.sh` / reproducible-build to a **universal** `assembleRelease` (still not Play AAB) with documented key. Do not silently mix keys. |
| **Affected components/files** | `android/build.sh`, `scripts/reproducible-build.sh`, `.github/workflows/release.yml`, README (RFB-DOC-03) |
| **Dependencies** | RFB-DOC-03 wording follows this decision. |
| **Implementation notes** | Adding release signing in CI needs secrets — only if choosing non-debug. Default freeze path can remain debug if honest. |
| **Verification method** | `apksigner verify` / `jarsigner` shows expected cert. |
| **Acceptance criteria** | Artifact type matches docs; installs on one matrix device. |
| **Evidence required** | Decision note + APK cert + SHA256. |
| **Status** | `DONE_STATIC` — Decision: freeze/phone-to-phone uses **Technical Beta** (`assembleRelease` + local JKS, `scripts/build-technical-beta-apk.sh`). Development remains `assembleDebug`. Play AAB unchanged. CI `reproducible-build.sh` still debug (not this artifact). Install smoke **UNVERIFIED**. See [TECHNICAL_BETA_ARTIFACT.md](./TECHNICAL_BETA_ARTIFACT.md). |

### RFB-REL-02 — Cut freeze RC tag with hashes

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G9 |
| **Problem statement** | No `v*.*.*` in audited clone; G9 FAIL. |
| **Current behavior** | versionName 0.1.75 / versionCode 76. Local APK `0.1.75-rc.1` hashed in [RELEASE_CANDIDATE.md](./RELEASE_CANDIDATE.md). **No tag, not published.** |
| **Desired behavior** | Tag after P0 code + TEST-01. GitHub Release: APK, SHA256SUMS, integrity JSON (unsigned OK if not called official), SBOM if workflow produces it. Verify with `release-verify.sh` or documented hash check. |
| **Affected components/files** | git tag; `android/version.properties`; `CHANGELOG.md` if RFB-DOC-04 added one |
| **Dependencies** | RFB-REL-01, RFB-TEST-01, RFB-HW-01 at least one install smoke, RFB-DOC-04. |
| **Implementation notes** | Follow RELEASING.md with recorded deviations. User asked not to publish this RC. |
| **Verification method** | Tag exists; artifacts downloadable; hash matches. |
| **Acceptance criteria** | Plan G9 pass criteria. |
| **Evidence required** | Tag name, URLs, hashes. |
| **Status** | `OPEN` — local hashes exist; **no tag**; not published. G9 **FAIL**. |

### RFB-REL-03 — G10 freeze declaration

| Field | Content |
|-------|---------|
| **Priority** | P0 |
| **Release gate** | G10 |
| **Problem statement** | Exit criteria unmet until prior gates pass. |
| **Current behavior** | Technical G10 tables: **NOT APPROVED**. Policy overlay: [DEVELOPMENT_FREEZE.md](./DEVELOPMENT_FREEZE.md) feature freeze with exceptions. That overlay is **not** release freeze / G10. |
| **Desired behavior** | All plan §8 bullets true; post-freeze remainder stays in WORK_REMAINING / SECURITY_ROADMAP; optional GitHub description “public technical beta / development paused.” |
| **Affected components/files** | README banner; WORK_REMAINING pointer |
| **Dependencies** | All P0 items done or accepted; P1 done or accepted. |
| **Implementation notes** | Do not declare early. |
| **Verification method** | Plan §8 checklist. |
| **Acceptance criteria** | “Active development may be paused” is honest. |
| **Evidence required** | Gate evidence pack. |
| **Status** | `OPEN` — Feature freeze **ACTIVE** ([DEVELOPMENT_FREEZE.md](./DEVELOPMENT_FREEZE.md) policy lock). Release freeze **NOT APPROVED**. G10 **NOT APPROVED**. Policy lock is not G9/G10 evidence pass. |

---

## Explicitly not in this backlog (plan §2.2 / audit P2)

Do not implement as freeze work: iOS ship, Tor/Nym, MLS, hardware-bound identity keys, Shade on by default, multi-operator bootstrap as must-ship, extra mini-app red-team, 520-node live soak, 10–50 node drill, Wi-Fi Aware/WebRTC **field** validation, auto GPS geohash, MeshCoin BFT, byte-identical APK zip, Tauri reproducible desktop, Play listing / Data Safety form, OSS-Fuzz submission, native proximity UI, share-as-FGS (unless later promoted), `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, cargo-vet adoption (optional later; freeze only requires SD-150 honesty).

---

## Implementation Sequence

**Remaining work that must land before hardware validation** is **only** [P0_IMPLEMENTATION_QUEUE.md](./P0_IMPLEMENTATION_QUEUE.md). Do not treat the historical phases below as an open coding queue: Phases 1–2 share/crash code is `DONE_STATIC` / `DONE_TESTED` in source; hardware is still `UNVERIFIED`.

### Historical phases (how the tree got here)

### Phase 0 — Honesty (docs)

**Does:** RFB-DOC-01, DOC-03 (draft), DOC-05 or schedule HW-03, DOC-06, DOC-02 (optional).
**Unlocks:** G1 pass criteria for §11; stops false completeness.
**Depends on:** Nothing.
**Verified by:** Contradiction disposition table; README/KNOWN_LIMITATIONS vs code for BLE/share/APK signing.

### Phase 1 — P0 crash / hang / provider

**Does:** RFB-P2P-01, NET-01, APK-02, P2P-03, P2P-07 (if still needed).
**Unlocks:** Safe Direct fallback; app can fail visibly; Quick Share not a filesystem oracle.
**Depends on:** Phase 0 can parallel.
**Verified by:** API 26 emulator Direct fallback logcat; init-failure UI code review; `file_paths.xml` without root-path.

### Phase 2 — P0 share correctness

**Does:** RFB-P2P-02 / PERM-01, P2P-04, P2P-05, P2P-06, APK-01, PERS-03, P2P-08, P2P-09, P2P-10.
**Unlocks:** A share path that can be hardware-tested (G3).
**Depends on:** Phase 1 (Direct must not crash; node must start).
**Verified by:** Unit tests (TEST-02) + one developer two-phone smoke (not yet the official G6 matrix).

### Phase 3 — Panic, permissions deny, artifact decision

**Does:** RFB-PERS-01, PERM-02, REL-01, DOC-03 finalize, DOC-04, APK-03.
**Unlocks:** G2 remainder (panic/deny); honest RC type.
**Depends on:** Phase 1 (startup). Phase 2 for deny-share.
**Verified by:** Panic screenshot; deny-path notes; apksigner/docs agreement.

### Phase 4 — Automated evidence

**Does:** RFB-TEST-01, TEST-02, TEST-03, SEC-04, SC-01 (script run), HYG-02.
**Unlocks:** G5; dependency record.
**Depends on:** Phases 1–3 merged to the freeze SHA.
**Verified by:** CI job URLs + gradle/cargo logs on that SHA.

### Phase 5 — Hardware (G3/G4/G6)

**Does:** RFB-HW-01, HW-02, HW-03 or keep DOC-05, NET-02, PERS-02, PERS-04, OEM-01, OEM-02, OEM-03.
**Harness (done, not a gate pass):** RFB-HW-04 — [HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md).
**Unlocks:** G3 pass, G4 pass or exception, G6 matrix.
**Depends on:** Phase 2 (share), Phase 3 (artifact to install), Phase 4 preferred so SHA is known.
**Verified by:** Filled [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md); LEVEL0 A2; Samsung (a) or (b).

### Phase 6 — Security / hygiene / RC / freeze

**Does:** RFB-SEC-01, SEC-02, SEC-03 (docs `done` 2026-08-29 — residuals unsigned-accepted, internal review page, external review deferred), SC-02, HYG-01, HYG-03, REL-02, REL-03. SEC-04 and SC-01 remain **open**.
**Unlocks:** G7, G8, G9, G10.
**Depends on:** Phase 5 (or documented HW exceptions); Phase 4.
**Verified by:** Residuals disposition; TM **§9** note ([SECURITY_RELEASE_REVIEW.md](./SECURITY_RELEASE_REVIEW.md)); CI_PUBLIC_TRANSITION; tag + hashes; plan §8. **G7 not passed.**

---

## Queue index (ID → phase)

| ID | Phase | P |
|----|-------|---|
| RFB-DOC-01 | 0 | P0 |
| RFB-DOC-02 | 0 | P2 |
| RFB-DOC-03 | 0/3 | P0 |
| RFB-DOC-04 | 3 | P1 |
| RFB-DOC-05 | 0 | P1 |
| RFB-DOC-06 | 0 | P1 |
| RFB-P2P-01 | 1 | P0 |
| RFB-P2P-02 | 2 | P0 |
| RFB-P2P-03 | 1 | P0 |
| RFB-P2P-04 | 2 | P0 |
| RFB-P2P-05 | 2 | P0 |
| RFB-P2P-06 | 2 | P0 |
| RFB-P2P-07 | 1 | P1 |
| RFB-P2P-08 | 2 | P1 |
| RFB-P2P-09 | 2 | P1 |
| RFB-P2P-10 | 2 | P1 |
| RFB-APK-01 | 2 | P0 |
| RFB-APK-02 | 1 | P0 |
| RFB-APK-03 | 3 | P0 |
| RFB-PERM-02 | 3 | P0 |
| RFB-OEM-01 | 5 | P1 |
| RFB-OEM-02 | 5 | P1 |
| RFB-OEM-03 | 5 | P1 |
| RFB-NET-01 | 1 | P0 |
| RFB-NET-02 | 5 | P0 |
| RFB-PERS-01 | 3 | P0 |
| RFB-PERS-02 | 5 | P0 |
| RFB-PERS-03 | 2 | P1 |
| RFB-PERS-04 | 5 | P1 |
| RFB-TEST-01 | 4 | P0 |
| RFB-TEST-02 | 4 | P1 |
| RFB-TEST-03 | 4 | P1 |
| RFB-HW-01 | 5 | P0 |
| RFB-HW-02 | 5 | P1 |
| RFB-HW-03 | 5 | P1 |
| RFB-HW-04 | 5 | P1 |
| RFB-SEC-01 | 6 | P1 |
| RFB-SEC-02 | 6 | P1 |
| RFB-SEC-03 | 6 | P1 |
| RFB-SEC-04 | 4 | P0 |
| RFB-SC-01 | 4 | P1 |
| RFB-SC-02 | 6 | P1 |
| RFB-HYG-01 | 6 | P1 |
| RFB-HYG-02 | 4 | P2 |
| RFB-HYG-03 | 6 | P1 |
| RFB-REL-01 | 3 | P0 |
| RFB-REL-02 | 6 | P0 |
| RFB-REL-03 | 6 | P0 |

**PERM-01** = alias of P2P-02 (Phase 2).
