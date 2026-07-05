# Implementation status — last four prompts (2026-07-05)

Audit of the four most recent feature prompts in the Mycelium session. Use this as the checklist for what shipped in commit `3eac036` and the follow-up commit on the same date.

---

## 1. CRDT merge model (`consistency.rs` → real types)

| Requirement | Status | Notes |
|-------------|--------|-------|
| Contact book: HLC LWW per field | **Done** | `crates/mycelium-core/src/crdt/{hlc,lww,contact}.rs` |
| Group membership: OR-Set | **Done** | `crdt/or_set.rs`, `crdt/group.rs` |
| Marketplace stock: PN-Counter (no naive LWW) | **Done** | `crdt/pn_counter.rs`; conflict surfaced, not silent merge |
| `docs/CRDT_MODEL.md` | **Done** | Rationale for marketplace non-auto-merge |
| Unit tests | **Done** | CRDT module tests including oversell scenario |

**Commit:** `3eac036`

---

## 2. Mesh-Sync + MeshMarket binding

| Requirement | Status | Notes |
|-------------|--------|-------|
| CRDT documents over mesh (contact, group meta, marketplace stock) | **Done** | `crdt_sync.rs`, `node.rs` publish/ingest |
| MeshMarket ↔ PN-Counter via bridge API | **Done** | `bridge_host.rs`, `bridge_api.js`, `miniapps/meshmarket/` |
| Storage merge APIs | **Done** | `storage.rs` |

**Commit:** `3eac036`

---

## 3. Secure hardware / user key storage audit

| Requirement | Status | Notes |
|-------------|--------|-------|
| Clarify: `release_integrity` / `reproducible_build` ≠ user keys | **Done** | `docs/SECURE_HARDWARE_STATUS.md` |
| Separate mobile repo? | **Done** | **No** — `android/`, `ios/` in monorepo |
| Android Keystore reality (`StorageKeyHelper.kt`) | **Done** | EncryptedSharedPreferences only; documented |
| iOS Keychain gap | **Done** | Documented as open |
| Align `THREAT_MODEL.md`, `SECURITY.md`, `ROADMAP.md` | **Done** | §4.1, §5.3; keys at rest → **Partial (beta)** |
| `at_rest.rs` comment | **Done** | Points to SECURE_HARDWARE_STATUS |

### Deferred (with reason)

| Item | Reason |
|------|--------|
| **Hardware-bound identity keys** (StrongBox / Secure Enclave) | Pre–Play/App Store engineering; tracked SD-120/SD-131. Host must supply non-exportable key handles — Rust `SecretVault` API is ready. |
| **iOS Keychain wrapper** | Shell incomplete (`ios/README.md` checklist); blocks iOS parity with Android prefs encryption. |
| **User-auth gate before key use** | Product/UX decision + platform APIs; not required for beta sideload. |
| **Root/jailbreak detect + refuse** | SD-120 documents risk; enforcement deferred to store-hardening phase. |

---

## 4. Geographic `PeerDiversityConfig` (local mesh geohash)

| Requirement | Status | Notes |
|-------------|--------|-------|
| Geohash bucketing helper (reuse SOS) | **Done** | `sos::GEOHASH_DIVERSITY_PREFIX_LEN`, `geohash_diversity_bucket()` |
| `PeerDiversityConfig` + `PeerRoutingMeta.geohash_bucket` | **Done** | `security.rs` |
| `apply_peer_diversity` / custody carrier path | **Done** | Via existing `pick_custody_carrier` → `apply_peer_diversity` |
| Relay selection | **Done** | Same diversity path as SD-031 |
| Peer trust persistence | **Done** | `last_geohash_bucket`, `note_peer_geohash`, `note_peer_co_presence_geohash` |
| Node wiring (SOS/resource gossip, PeerUp co-presence) | **Done** | `node.rs` |
| Corroboration diversity | **Done** | `SourceObservation.geohash_bucket` |
| Unit test | **Done** | `geographic_diversity_defers_same_geohash_cluster` |
| Threat model cross-ref | **Done** | `THREAT_MODEL.md` §5.4 |

### Deferred (with reason)

| Item | Reason |
|------|--------|
| **MyceliumLab eclipse / commuter custody sims** with geohash | Logic covered by unit test; sim scenarios are regression nice-to-have, not blocking. |
| **FFI/UI exposure of peer geohash bucket** | Out of scope for routing-only prompt; trust store already persists for next selection. |
| **GPS-derived geohash on device** | Co-presence requires `local_geohash_prefix` / `MYCELIUM_LOCAL_GEOHASH` (see `AR-06_REMAINING_GAPS.md`); without it, SOS/resource gossip remains the strong signal. |

---

## Out of scope for this commit (earlier prompt, still in working tree)

| Prompt | Status | Location |
|--------|--------|----------|
| WebRTC data-channel bearer (phone↔desktop/browser) | **Implemented, uncommitted** | `bearer_webrtc.rs`, `webrtc_signaling.rs`, Android/desktop hosts, ADR 010 |

Intentionally excluded from the secure-hardware + geo-diversity commit to keep review scope aligned with the last four prompts.

---

## Verification

```bash
cargo test -p mycelium-node geographic_diversity security::tests
cargo test -p mycelium-core crdt information_trust sos::tests
```
