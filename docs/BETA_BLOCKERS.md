# Mycelium — Beta Blockers (Pre–Public Beta)

**Review date:** May 2026  
**Method:** Document re-read + targeted code validation (not a formal penetration test)  
**Registers:** [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) · [SECURITY_ROADMAP.md](./SECURITY_ROADMAP.md)

---

## Executive summary

**Public wide beta is not recommended** until the **P0 code blockers** below are fixed **or** each item has a **written risk acceptance** signed by engineering + product + (if applicable) legal.

The Cell mini-app stack (P0–P4 hardening) is **materially improved** but does **not** clear network-layer beta blockers (SD-030, SD-031, SD-080, SD-081).

---

## P0 — Must fix or sign off before public beta

| ID | Title | Code verified? | Status | Notes |
|----|-------|----------------|--------|-------|
| **SD-030** | Unsigned `WireMessage::Data` accepted | **Yes — OPEN** | `node.rs` `handle_incoming_direct`: verifies signature only when `signature.is_some()`; **unsigned frames continue processing** | Enables spam, forgery, relay load |
| **SD-031** | Unknown-destination fanout | **Yes — OPEN** | `send_direct_internal`: if `to_peer` ∉ `known_peers`, **enqueues clone to every connected peer** | O(n) amplification per message |
| **SD-080** | Gossip re-sign with local key | **Yes — OPEN** | `ScopedReceived` path: `envelope.sign(local_keypair)` on gossip payloads | Author binding confusion |
| **SD-081** | Plaintext chat fallback | **Yes — OPEN** | `AppNode::send_chat`: if no enc key → `SendDirectPayload` plaintext | Fail-open, not fail-closed |
| **SD-140** | Protocol downgrade (unsigned Data) | **Yes — OPEN** | Same as SD-030; legacy interop = attack surface | Tie to SD-030 fix |
| **SD-141** | Unencrypted chat downgrade | **Yes — OPEN** | Same as SD-081 | Tie to SD-081 fix |
| **SD-171** | Android backup exfiltration | **Yes — OPEN** | `AndroidManifest.xml`: `android:allowBackup="true"` | adb backup / cloud backup risk |
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
| `MINIAPP_RUNTIME_HARDENING.md` §2–7, §12 | Still lists pre–Cell gaps (identity ungated, install verify ✗) |
| `SECURITY_ROADMAP.md` Phase 0 | SD-041 “not implemented” — **partially implemented** |
| `ABUSE_RESPONSE_PLAYBOOK.md` | “Sideload unsigned until SD-041 fixed” — **partially mitigated** |
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

**Example:** Waiving SD-031 for LAN-only beta with max 8 peers — not acceptable for Internet-wide beta without relay rate limits.

---

## Recommended gate

| Gate | Command / action |
|------|------------------|
| Rust tests | `cargo test -p mycelium-app -p mycelium-node -p mycelium-coin` |
| SD-030 regression test | Add: unsigned `Data` → dropped + metric |
| Red team | `MINIAPP_ATTACK_SCENARIOS.md` 90 min script |
| Android | `allowBackup=false`, UniFFI regen, security checklist |
| Legal | Privacy policy URL live; MeshCoin preview disclaimer |

**Minimum bar for “beta” label:** All **OPEN** P0 network items fixed **or** waivers signed; mini-app P0 items at **PARTIAL** minimum with in-app warnings.
