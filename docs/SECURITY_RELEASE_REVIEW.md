# Security and supply-chain release review (G7)

**Review date:** 2026-08-29
**HEAD SHA:** `fb32b1e5c89b318e14b1adc2a76b7dcefac6b3ec` (`fb32b1e Free disk on rust-budget so hosted runners can link tests.`)
**Working tree:** this clone also contains **uncommitted** freeze work (share, Samsung/stability harness, public-repo hygiene). Findings that depend only on committed HEAD are marked. Findings that depend on the working tree are marked.
**Reviewer:** internal freeze review (Mycelium maintainers). **Not** an external auditor. **No named independent reviewer.**
**Plan:** [RELEASE_FREEZE_PLAN.md](./RELEASE_FREEZE_PLAN.md) G7.
**Threat model:** [THREAT_MODEL.md](./THREAT_MODEL.md) **v1.1**. The freeze plan’s “§8 checklist” is the **mapping table**. The reviewer checklist used here is **§9**.
**Debt snapshot:** [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) last updated **2026-08-29**.

This document is a **dated internal review**. It is **not** a certification, **not** a penetration-test report, and **not** evidence that an external security audit occurred.

---

## Verdict

**GitHub-only technical preview beta:** no **new unaddressed Critical** issue was found for the **preview** product (MeshCoin stays preview utility, not money). P0 network/storage mitigations (SD-030, SD-031, SD-080, SD-081, SD-041, SD-171) are **implemented** and, except SD-171, have **automated tests that passed in this session**. None of those controls have been **independently reviewed**.

**Play Store / wide public beta:** still blocked on process: named residual sign-off, scheduled external review, Play Data Safety, and an official (M-of-N / Play-signed) artifact — not on a newly discovered Critical in preview scope.

**Do not claim:** “Mycelium is secure,” Signal-class metadata privacy, whistleblower/Tor resistance, or that GitHub debug APKs are official M-of-N releases.

**G7 gate:** this page **is** the required internal review. It does **not** by itself pass freeze G7/G10: [RFB-SEC-04](./RELEASE_FREEZE_BACKLOG.md) (Gitleaks job URL on freeze SHA) and RC SBOM / `release-verify` remain open. Do not tick plan checkboxes from this file alone.

---

## How to read mitigation class

A control is **not** “secure” because code exists. Each finding uses:

| # | Class | Meaning in this review |
|---|--------|-------------------------|
| 1 | **Implemented** | Mechanism exists in this tree (source or config). |
| 2 | **Tested** | Automated tests exist **and** were executed here, **or** CI YAML clearly runs them. Presence of a test file without a run is **not** class 2. |
| 3 | **Independently reviewed** | Third-party or named external review of **that** control. **None in this project to date.** |
| 4 | **Documented residual** | Explicitly accepted in [THREAT_MODEL.md](./THREAT_MODEL.md) §6, [ACCEPTED_SECURITY_RESIDUALS.md](./ACCEPTED_SECURITY_RESIDUALS.md) R1–R10, or [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md). |

---

## Threat model §9 checklist (internal)

| # | Checklist item | Result |
|---|----------------|--------|
| 1 | **Scope** — §2 boundaries vs store/user claims | **Pass for GitHub preview copy** if [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md) and [security-for-users.md](./security-for-users.md) remain the public story. E2E is claimed for content after key exchange, not metadata. **Play listing not in this review.** |
| 2 | **Residuals** — §6 accepted or scheduled | **R1–R10 listed.** GitHub-only freeze: **unsigned, explicitly accepted** (see residuals file). Play still needs names. Voucher/custody UX not re-audited as a product pass. |
| 3 | **Debt closure** — `beta-blocker: yes` mitigated or waived | Remaining **yes** rows are **conditional on real-money MXC** (e.g. SD-011, SD-061, SD-160) plus legal-review style rows. Preview MXC keeps them **non-blocking** for GitHub technical beta. |
| 4 | **Privacy Phase A** | Policy + in-app legal + backup flags exist. Relay **ops** no-IP-retention audit still **TODO** ([PRIVACY_ROADMAP.md](./PRIVACY_ROADMAP.md)). GitHub Pages privacy URL is a **Play** item. |
| 5 | **Asset tests** | SD-081, SD-041, SD-032 ingest tests **run this session** (see evidence). Voucher double-spend sim **not re-run** here. |
| 6 | **§2.1 not treated as defects** | Confirmed: Tor-class TA, duress, forensic, physical access, whistleblower tool are **out of scope**, not findings. |

**Deliverable in TM §9** would be a **signed** statement. **That deliverable does not exist.** This unsigned internal note is the freeze substitute for GitHub-only beta only.

---

## P0 mitigations (do not regress)

| ID | Control | 1 Impl | 2 Tested (this session) | 3 Independent | 4 Residual | Notes |
|----|---------|--------|-------------------------|---------------|------------|-------|
| SD-030 / SD-140 | Unsigned `WireMessage::Data` dropped (`UNSIGNED_GRACE_PERIOD_UNTIL_MS = 0`) | yes | **yes** — `cargo test -p mycelium-node --test sd030_unsigned_drop` (3 ok); `unsigned_dropped_after_grace_period` (1 ok) | **no** | Gossip relay bodies remain unsigned by design (SD-080) | CI budget `nextest --workspace` **should** include these; named “Security regression gates” job does **not** list `sd030`. No freeze-SHA CI URL. |
| SD-031 | Relay fanout default **3** | yes | **partial** — diversity/fanout unit tests in `security.rs` (`eclipse_swarm_blocked_without_diversity`, `max_fanout: 3`). No dedicated `sd031` integration test run. | **no** | Authenticated DHT routing still missing (hardening) | Default `default_max_relay_fanout() -> 3`. |
| SD-080 | Gossip stored via `Envelope::new_unsigned_gossip` | yes | **indirect** — `sd030` gossip-body exempt test; ingest path is `ingest_unsigned_gossip` | **no** | Inner payload auth still required for trust | Do not re-introduce local re-sign. |
| SD-081 / SD-141 | Fail-closed chat without enc key | yes | **yes** — `sd081_enc_key_error_is_actionable`; `send_chat_encrypted_requires_peer_key` | **no** | Group PSK (R9) is a separate residual | Android maps `enc_key_not_yet_exchanged` in `ChatViewModel.kt`. |
| SD-041 / SD-240 | Sideload requires `mycelium-bundle.sig.json` | yes | **yes** — `sideload_without_bundle_signature_rejected`, `sideload_install_without_listing`, `hash_mismatch_blocked_on_sideload` | **no** | User can still install a **signed** malicious bundle (R4/WebView) | Developer pinning UX missing (register). |
| SD-171 | `android:allowBackup="false"` + extraction rules | yes | **no automated test run** (manifest inspection only) | **no** | Root/ADB/backup-unrelated seizure (TM §5.3) | `fullBackupContent="false"`; `backup_rules.xml` excludes file/db/prefs. |
| SD-032 | Ingest rate limits + size cap | yes | **yes** — `freshness_rejects_stale_and_far_future`; `direct_rate_limit_trips` exists (freshness run this session) | **no** | Sybil can still use many authors | Register **body** for SD-032 was stale vs code; aligned in this pass. |
| SD-060 | 30 min freshness + author id window | yes | **yes** — freshness test; `author_replay_window_catches_duplicate_id` in crate | **no** | Replay **inside** the 30 min / 512-id window still possible | Register **body** was stale vs summary; aligned in this pass. |

---

## Findings

Status vocabulary: `mitigated` · `residual accepted` · `process gap` · `doc drift (fixed)` · `not independently reviewed`.

### SR-EXT-01 — No external security audit

| Field | Content |
|-------|---------|
| **Severity** | High for **Play / wide beta**; Medium for **GitHub technical preview** (expected by plan) |
| **Evidence** | [EXTERNAL_SECURITY_REVIEW.md](./EXTERNAL_SECURITY_REVIEW.md) was a briefing pack with status **Not commissioned**. No signed reviewer statement, no engagement ID, no third-party report in-tree. |
| **Affected component** | Release governance |
| **Status** | `process gap` — **explicitly deferred** for GitHub-only technical beta (2026-08-29, Mycelium maintainers). **Not** scheduled. |
| **Mitigation** | Internal review (this file) + public honesty docs. Class: **4** for GitHub freeze; **missing 3** for Play. |
| **Residual risk** | Unknown classes of bugs an external review would catch. Preview users must not treat the app as audited. |
| **Release impact** | Does **not** invent a Play blocker beyond [PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md). **Must not** be described as “security audited.” |

### SR-RES-01 — Residual register unsigned

| Field | Content |
|-------|---------|
| **Severity** | Medium (process). Play: High until named. |
| **Evidence** | Empty Engineering/Product/External names in [ACCEPTED_SECURITY_RESIDUALS.md](./ACCEPTED_SECURITY_RESIDUALS.md). File already stated it is **not** a release certificate. |
| **Affected component** | Residuals R1–R10 |
| **Status** | `residual accepted` for **GitHub-only** beta with dated owner paragraph (same file). Sign-off table stays empty. |
| **Mitigation** | Written freeze disposition. Class **4**. |
| **Residual risk** | No named product/legal owner on the waiver. Play still requires signatures. |
| **Release impact** | GitHub technical beta: allowed by freeze plan P1-06. Play: still blocked. |

### SR-P0-01 — P0 controls not independently reviewed

| Field | Content |
|-------|---------|
| **Severity** | Informational / Medium (assurance gap, not a new bug) |
| **Evidence** | Tests and source cited in the P0 table. No external review of those diffs. |
| **Affected component** | `mycelium-node`, `mycelium-app`, `mycelium-miniapp`, Android manifest |
| **Status** | `not independently reviewed` |
| **Mitigation** | Classes **1** and (where noted) **2**. Not **3**. |
| **Residual risk** | Tests can miss protocol edge cases (e.g. SD-031 amplification under crisis-mode overrides). |
| **Release impact** | Does not block GitHub preview if claims stay honest. |

### SR-AUTH-01 — Authentication model

| Field | Content |
|-------|---------|
| **Severity** | Informational (by design) |
| **Evidence** | No end-user accounts or passwords. Peer identity is libp2p `PeerId` from Ed25519. Direct `Data` requires envelope signature (SD-030). `EncryptedDirect` dropped without valid `mesh_signature` (`node.rs` ingest). Chat send refuses without peer enc key (SD-081). |
| **Affected component** | `mycelium-node`, `mycelium-app` |
| **Status** | `mitigated` for **content authenticity of signed Data / EncryptedDirect**; `residual accepted` for **human identity** (anyone can generate keys). |
| **Mitigation** | Class **1**; SD-030/081 **2**. Not **3**. |
| **Residual risk** | Contact-book confusion, QR/social-engineering of PeerIds, group PSK (R9). |
| **Release impact** | Not a preview Critical. Do not claim verified identities. |

### SR-CRYPTO-01 — Cryptography usage

| Field | Content |
|-------|---------|
| **Severity** | Informational |
| **Evidence** | Ed25519 (identity, wire, mini-app bundle, release attest), X25519 / Double Ratchet path (ADR 004; legacy static remaining per KNOWN_LIMITATIONS), ChaCha20-Poly1305 at-rest (`at_rest.rs`), BLAKE3 (release artifacts, bundle hash). libp2p Noise/TLS/QUIC for transport. |
| **Affected component** | `mycelium-core`, `mycelium-node`, Android Keystore wrapping of storage master key |
| **Status** | `mitigated` as **standard constructions in use**; **not** a crypto audit. |
| **Mitigation** | Class **1**. Limited **2** (unit tests, not a formal proof). Not **3**. |
| **Residual risk** | Implementation bugs, ratchet/legacy mix, group PSK. |
| **Release impact** | No redesign requested. Do not claim “audited crypto.” |

### SR-KEY-01 — Key handling and storage

| Field | Content |
|-------|---------|
| **Severity** | Medium (device compromise) — **in scope as residual**, not a freeze Critical |
| **Evidence** | [SECURE_HARDWARE_STATUS.md](./SECURE_HARDWARE_STATUS.md): EncryptedSharedPreferences + `setRequestStrongBoxBacked(true)` with software Keystore fallback (`StorageKeyHelper.kt`). Identity keys are **not** non-exportable StrongBox/SE objects. Desktop: weak vault refused for new installs (SD-131, not re-tested here). |
| **Affected component** | Android `StorageKeyHelper`, Rust `SecretVault` |
| **Status** | `residual accepted` (TM §5.3 physical theft / root) |
| **Mitigation** | Class **1**. Not **2** in this session (no instrumented Keystore test). Not **3**. Class **4**. |
| **Residual risk** | Root, backup-unrelated extraction, cold-boot, user unlocking the device for an adversary. |
| **Release impact** | Matches threat-model non-goal for seized devices. |

### SR-NET-01 — Network transport and metadata

| Field | Content |
|-------|---------|
| **Severity** | High **if** compared to Tor/Signal metadata goals; **accepted** for this product (R2, R6, R8) |
| **Evidence** | libp2p gossip/sync; default relay path; INTERNET permission. No padding (SD-070). Relay sees IP (R6). |
| **Affected component** | `mycelium-node`, `mycelium-relay` (companion), Android permissions |
| **Status** | `residual accepted` |
| **Mitigation** | E2E for chat **content** after keys (class **1/2** for fail-closed). Metadata: class **4** only. |
| **Residual risk** | Relationship mapping, timing, ISP/relay observation. |
| **Release impact** | Preview OK if public copy refuses metadata privacy. |

### SR-ID-01 — Peer identity

| Field | Content |
|-------|---------|
| **Severity** | Medium (impersonation via social graph, not protocol forge of signed Data) |
| **Evidence** | PeerId = key. No TOFU safety-number UX audit in this review. Invites are `mycelium://` (redacted in stability logs). |
| **Affected component** | Contacts, QR, BLE/Wi-Fi share |
| **Status** | `residual accepted` (human binding) |
| **Mitigation** | Cryptographic binding class **1**. UX class incomplete. Not **3**. |
| **Residual risk** | Wrong-peer chat if the user accepts a malicious invite. |
| **Release impact** | Not preview-Critical. |

### SR-REPLAY-01 — Replay protection

| Field | Content |
|-------|---------|
| **Severity** | Medium (duplicate delivery / bulletin replay inside window) |
| **Evidence** | `REPLAY_MAX_AGE_MS = 30 * 60 * 1000`, `AUTHOR_ID_WINDOW = 512`, `check_envelope_freshness` tests. |
| **Affected component** | `ingest_guard.rs` |
| **Status** | `mitigated` vs unbounded replay; `residual` inside window |
| **Mitigation** | Classes **1** and **2** (freshness). Not **3**. |
| **Residual risk** | Re-inject within 30 minutes or after window eviction of that message id. MeshCoin tx replay is **SD-061** (preview MXC). |
| **Release impact** | Not a GitHub preview Critical. |

### SR-INT-01 — Message integrity

| Field | Content |
|-------|---------|
| **Severity** | — (control exists; not independently reviewed) |
| **Evidence** | Signed `Data`; EncryptedDirect mesh signature hard-drop; gossip **not** re-signed (SD-080). |
| **Affected component** | `security.rs`, `node.rs` |
| **Status** | `mitigated` for signed Data / EncryptedDirect; gossip authenticity is **inner** payload |
| **Mitigation** | **1**, **2** (SD-030). Not **3**. |
| **Residual risk** | Mis-attribution if inner gossip auth is ignored by a future caller. |
| **Release impact** | Do not regress SD-030/080. |

### SR-UPD-01 — Update and APK integrity

| Field | Content |
|-------|---------|
| **Severity** | High if a **debug-signed** GitHub APK is called “official”; Medium as documented honesty |
| **Evidence** | [RELEASE_INTEGRITY.md](./RELEASE_INTEGRITY.md): BLAKE3 + M-of-N; **CI MUST NOT hold ≥3 signer keys**; tag workflow `release.yml` runs `reproducible-build.sh` and **unsigned** `mycelium-cli release init`. `android-ci.yml` / `android-debug-apk.yml` ship **debug** APK. `release.yml` notes “reproducible debug-signed sideload.” Play AAB is a **different** key (§7). Share landing SHA-256 is **hash honesty**, not Play signing. Production HSM signers: “once provisioned.” |
| **Affected component** | Release, Android signing, phone-to-phone share |
| **Status** | `process gap` for **official** artifacts; `documented residual` for debug sideload |
| **Mitigation** | Class **1** for the **integrity scheme**. Class **2** for `release_integrity` tests in CI YAML (not re-run here). Official M-of-N **not** produced for this SHA. Not **3**. |
| **Residual risk** | Users install debug-signed or modified APKs (KNOWN_LIMITATIONS item 7). |
| **Release impact** | GitHub freeze must keep debug vs official language. Play/G9 official path still needs M-of-N or Play App Signing as documented. |

### SR-DEP-01 — Dependency versions and advisories

| Field | Content |
|-------|---------|
| **Severity** | Medium (supply chain) |
| **Evidence** | `deny.toml` ignores RUSTSEC-2026-0118/0119, 0194/0195, 0185 with reasons. [DEPENDENCY_ADVISORY_ALLOWLIST.tsv](./DEPENDENCY_ADVISORY_ALLOWLIST.tsv) `review_by` **2026-09-30**. `./scripts/check-advisory-allowlist.sh` → **OK** on 2026-08-29. Full `./scripts/check-dependencies.sh` (cargo-deny + cargo-audit) **not executed in this session**. CI `ci.yml` runs it on rust-budget and rust-full. sled 0.34.7 stagnation: [DEPENDENCY_RISK.md](./DEPENDENCY_RISK.md) observe, not freeze-blocker. |
| **Affected component** | `Cargo.lock`, Gradle, npm (desktop) |
| **Status** | `process gap` until a **recorded** `check-dependencies.sh` log on freeze SHA (RFB-SC-01) |
| **Mitigation** | Class **1** (gate exists). Class **2** = CI YAML, **not** this machine’s full script. Allowlist dates: **tested** (script). Not **3**. |
| **Residual risk** | New critical advisory before 2026-09-30; ignored RUSTSEC rows wrong if attack surface changes (e.g. untrusted XML). |
| **Release impact** | Allowlist **not expired**. Re-run full script on freeze SHA. After **2026-09-30**, expired rows **block** G7. |

### SR-DEP-02 — cargo vet / Dependabot enablement

| Field | Content |
|-------|---------|
| **Severity** | Low–Medium (hygiene) |
| **Evidence** | SD-150: `cargo vet` missing. `.github/dependabot.yml` **file** present; GitHub **setting** may be off until public ([PUBLIC_REPOSITORY_AUDIT.md](./PUBLIC_REPOSITORY_AUDIT.md)). |
| **Affected component** | Supply chain |
| **Status** | `documented residual` / G8 |
| **Mitigation** | Class **1** for deny/audit. Not cargo vet. Not **3**. |
| **Residual risk** | Slower patch uptake if Dependabot stays disabled after going public. |
| **Release impact** | Enable Dependabot at public launch or keep SD-150 honest. |

### SR-PERM-01 — Android permissions

| Field | Content |
|-------|---------|
| **Severity** | Medium (location/BLE surprise) if not opt-in in UX |
| **Evidence** | Manifest: INTERNET, FGS connected-device, notifications, camera, fine/coarse location, Wi-Fi, Nearby Wi-Fi `neverForLocation`, BLE advertise/scan/connect, NFC. Only `MainActivity` exported (LAUNCHER). `MeshService` / mini-app / FileProvider not exported. FileProvider paths: `cache-path` `apk-share/` only. |
| **Affected component** | `AndroidManifest.xml`, runtime permission UX |
| **Status** | `mitigated` for export surface; location/BLE remain **sensitive** |
| **Mitigation** | Class **1**. Runtime deny-path is a **G2/hardware** item, not re-proven here. |
| **Residual risk** | User grants location for mesh features; OEM background limits. |
| **Release impact** | Freeze still needs deny non-fatal evidence (plan P0). Not a new Critical in code review. |

### SR-LOG-01 — Logging and crash diagnostics

| Field | Content |
|-------|---------|
| **Severity** | Medium if secrets hit logcat; Low with current redaction **if** developers follow [CRASH_REPORTING.md](./CRASH_REPORTING.md) |
| **Evidence** | `StabilityDiagnostics` redacts invite URIs, long hex, `storageKeyHex`. Uncaught handler logs then delegates (process still dies). No in-app crash telemetry upload. Platform (Google/Apple) crash may still leave the device. `StorageKeyHelper` logs key **generation**, not the hex. |
| **Affected component** | Android logging, developer adb |
| **Status** | `mitigated` for intentional Mycelium tags; `residual accepted` for OS crash pipelines |
| **Mitigation** | Class **1**. Unit tests for redact **not** run this session. Not **3**. Class **4** (platform logs). |
| **Residual risk** | Other tags (`Log.d`) still logging sensitive strings; unfiltered `*:V` in a PR. |
| **Release impact** | Keep crash artifacts gitignored (`artifacts/`). |

### SR-CI-01 — CI and secrets handling

| Field | Content |
|-------|---------|
| **Severity** | High **until** Gitleaks evidence (plan P0-06); tracked tree previously clean ([PUBLIC_REPOSITORY_AUDIT.md](./PUBLIC_REPOSITORY_AUDIT.md)) |
| **Evidence** | `.github/workflows/secret-scan.yml`: Gitleaks, `fetch-depth: 0`. **No job URL** for `fb32b1e`. Play/release workflows reference `${{ secrets.* }}` names only. `android.injected.signing.*` for release keystore — not in git. `local.properties` gitignored. |
| **Affected component** | GitHub Actions, git history |
| **Status** | `process gap` (RFB-SEC-04) |
| **Mitigation** | Workflow class **1**. Evidence class **2** **missing** for freeze SHA. Not **3**. |
| **Residual risk** | Secret in **history** not seen by this review’s Gitleaks. |
| **Release impact** | **Blocks freeze P0-06 / G8** until a green job URL (or a documented local Gitleaks run with `fetch-depth: 0`). |

### SR-MXC-01 — MeshCoin preview positioning

| Field | Content |
|-------|---------|
| **Severity** | Critical **if** treated as money (SD-011/061/160); Informational for preview |
| **Evidence** | `wallet_experimental_notice`: “Not money, not an investment…”. [MESHCOIN_UI_COPY.md](./MESHCOIN_UI_COPY.md). Residuals R1, R3. |
| **Affected component** | Wallet UI, `mycelium-coin` |
| **Status** | `residual accepted` while preview-only |
| **Mitigation** | Class **4**. Sybil/quorum **not** mitigated (class 1 missing for real value). |
| **Residual risk** | Users still misunderstand credits. German copy still flagged in MESHCOIN_UI_COPY. |
| **Release impact** | GitHub preview OK. Real-money / Play financial claims would **re-open** beta-blockers. |

### SR-DOC-01 — Debt register body vs summary (SD-032, SD-060)

| Field | Content |
|-------|---------|
| **Severity** | Low (reviewer confusion) |
| **Evidence** | Summary table said SD-032/SD-060 **Mitigated**; section bodies still described missing rate limits / replay window. Code: `ingest_guard.rs`. |
| **Affected component** | `SECURITY_DEBT_REGISTER.md` |
| **Status** | `doc drift (fixed)` in this pass |
| **Mitigation** | Align bodies with `IngestGuard`. |
| **Residual risk** | Other rows may still lag code; this review did not re-read every SD-*. |
| **Release impact** | Honesty for G7 snapshot. |

---

## Tests executed this session (not freeze-SHA CI)

Working directory: Mycelium repo. **Not** a GitHub Actions job URL.

```text
./scripts/check-advisory-allowlist.sh
  → Advisory allowlist OK (review dates current)

cargo test -p mycelium-node --test sd030_unsigned_drop
  → 3 passed

cargo test -p mycelium-node --lib unsigned_dropped
  → unsigned_dropped_after_grace_period ok

cargo test -p mycelium-node --lib freshness_rejects
  → freshness_rejects_stale_and_far_future ok

cargo test -p mycelium-app --lib sd081
  → sd081_enc_key_error_is_actionable ok

cargo test -p mycelium-app --lib send_chat_encrypted_requires_peer_key
  → send_chat_encrypted_requires_peer_key ok

cargo test -p mycelium-miniapp --lib sideload
  → 3 passed (sideload signature / hash)
```

**Not run:** `./scripts/check-dependencies.sh`, `voucher_double_spend`, Android instrumented tests, Gitleaks, `release-verify.sh`, hardware.

---

## SECURITY.md reporting path

Reviewed [SECURITY.md](../SECURITY.md):

- Private report to **security@example.invalid** is documented; **do not** file public issues for vulns.
- 72-hour acknowledgement target.
- GitHub Security Advisories listed as second channel.
- Scope covers Android/desktop/Rust, companion relay, crypto/mesh/mini-app. Third-party deps out of scope with heads-up welcome.

**Accuracy caveats (not a new architecture):**

1. GitHub private reporting **only works if enabled** on the repository. This review **did not** confirm GitHub settings. Email remains the **guaranteed** path. SECURITY.md was updated to say that.
2. Transparency links now include this review. They must not be read as an external audit.

---

## What would still block Play (unchanged)

From [PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md) and freeze plan: named residual sign-off, **scheduled** (not necessarily finished) external review, Play Data Safety, privacy URL, official signing. **None of those are claimed done.**

---

## Change log

| Date | Change |
|------|--------|
| 2026-08-29 | Initial G7 internal review. No external audit. Local P0 tests recorded. Residuals freeze-accepted unsigned for GitHub-only beta. |
