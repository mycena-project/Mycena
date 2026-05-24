# Mycelium — Beta Blockers (Pre–Public Beta)

**Review date:** May 2026  
**Method:** Document re-read + targeted code validation (not a formal penetration test)  
**Registers:** [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) · [SECURITY_ROADMAP.md](./SECURITY_ROADMAP.md)

---

## Executive summary

All P0 network blockers fixed. Closed beta with ≤100 known users is appropriate.

Remaining items (P1/P2) are documented in KNOWN_LIMITATIONS.md.

---

## P0 — Must fix or sign off before public beta

| ID | Title | Code verified? | Status | Notes |
|----|-------|----------------|--------|-------|
| **SD-030** | Unsigned `WireMessage::Data` accepted | **Yes** | ✅ FIXED | Hard drop after grace period + gossip-relay exception |
| **SD-031** | Unknown-destination fanout | **Yes** | ✅ FIXED | Max 3 relay candidates (`max_relay_fanout`) |
| **SD-080** | Gossip re-sign with local key | **Yes** | ✅ FIXED | `Envelope::new_unsigned()` for group/appstore gossip |
| **SD-081** | Plaintext chat fallback | **Yes** | ✅ FIXED | Fail-closed + UI warning (Android + desktop) |
| **SD-140** | Protocol downgrade (unsigned Data) | **Yes** | ✅ FIXED | Same fix as SD-030 |
| **SD-141** | Unencrypted chat downgrade | **Yes** | ✅ FIXED | Same fix as SD-081 |
| **SD-171** | Android backup exfiltration | **Yes** | ✅ FIXED | `allowBackup=false` + `backup_rules.xml` |
| **SD-131** | Desktop weak at-rest fallback | **Yes — OPEN** | `at_rest.rs`: derives key from `db_path` when no keyring; desktop `storage_key: None` | Weaker than Android Keystore path |

### P0 — Product / legal (no code line, still blocking positioning)

| ID | Title | Status |
|----|-------|--------|
| **SD-011** | MeshCoin Sybil witnesses | **OPEN** if marketed as money; **waivable** if preview-only + caps + copy |
| MeshCoin caps | Velocity / balance limits | **Implemented** (`settlement_policy.rs`) — verify UX still says “preview” |

---

## P0 — Partially mitigated (disclose + tighten or sign off)

| ID | Title | Status | Gap |
|----|-------|--------|-----|
| **SD-040** | CSP `unsafe-inline` | **PARTIAL** | Strict CSP when `strict_csp_eligible`; inline-script bundles still get `unsafe-inline` |
| **SD-041** | Unsigned mini-app install | **PARTIAL** | `install_verified` + listing sig for store path; **sideload with user ack still allowed** |
| **SD-240** | Unsigned offline mini-app payloads | **PARTIAL** | Same as sideload; bundled revocations empty |
| **SD-042** | Runtime permission granularity | **PARTIAL** | Install UI + runtime grants + HMAC caps (P4); not Signal-grade consent UX |

---

## Recently mitigated (do not regress)

| Area | Evidence |
|------|----------|
| EncryptedDirect without sig | Dropped in `handle_incoming_direct` |
| Unsigned WireMessage::Data | SD-030 hard drop after grace period |
| Relay fanout cap | SD-031 `max_relay_fanout` |
| Gossip author binding | SD-080 unsigned gossip envelopes |
| Chat fail-closed | SD-081 no plaintext direct fallback |
| Android backup disabled | SD-171 manifest + extraction rules |
| Mini-app install preview | Revocation, downgrade, bundle scan, trust levels |
| Mini-app bridge | Session token, rate limits, capability MAC, safe mode |
| Android mini-app process | `:miniapp` + `MiniAppBridgeService` IPC |
| Desktop postMessage | Reply targets iframe `origin` (not `*`) |
| MeshCoin emergency framing | Docs + settlement caps |
| Gossip listing verify on ingest | `verify_signature()` on app store scope |
| Revocation gossip | Signed ingest + publish path |

---

## Doc vs code drift (fix before beta)

These documents **overstate** remaining mini-app gaps or **understate** network gaps:

| Document | Issue |
|----------|-------|
| `MINIAPP_RUNTIME_HARDENING.md` §2–7, §12 | Updated May 2026 — see status banner |
| `SECURITY_ROADMAP.md` Phase 0 | SD-041 “not implemented” — **partially implemented** |
| `ABUSE_RESPONSE_PLAYBOOK.md` | Sideload entry updated — partially mitigated |
| `ARCHITECTURE_RISKS.md` | Accurate on SD-031; mini-app AR-07 severity unchanged |

**Action:** Update registers after P0 fixes; link this file from `SECURITY_ROADMAP.md`.

---

## Waiver template (if shipping with open P0)

Each waived item requires:

1. **ID + title**
2. **Residual risk** (1 paragraph, no marketing)
3. **User-visible warning** (where shown)
4. **Expiry** (next release target)
5. **Sign-off:** Engineering lead, Product, Legal (if MXC / activists)

**Example:** Waiving SD-131 for desktop-only beta with local data — not acceptable for activist deployments without Keystore-equivalent path.

---

## Recommended gate

| Gate | Command / action |
|------|------------------|
| Rust tests | `cargo test --workspace` |
| SD-030 / SD-031 regression | `cargo test -p mycelium-node sd030 sd031` |
| SD-081 regression | `cargo test -p mycelium-app sd081` |
| Red team | `MINIAPP_ATTACK_SCENARIOS.md` 90 min script |
| Android | `allowBackup=false`, UniFFI regen, `./gradlew test` |
| Legal | Privacy policy URL live; MeshCoin preview disclaimer |

**Minimum bar for “beta” label:** P0 network items fixed; mini-app P0 items at **PARTIAL** minimum with in-app warnings; SD-131 waived or fixed for desktop.
