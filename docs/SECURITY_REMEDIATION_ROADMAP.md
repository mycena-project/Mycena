# Mycena — Security Remediation Roadmap (Post-Review)

**Derived from:** Pre–public beta closure review (May 2026)  
**Companion:** [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) · [SECURITY_ROADMAP.md](./SECURITY_ROADMAP.md)

---

## P0 — Must fix before public beta (code)

| # | Item | Debt | Effort | Owner |
|---|------|------|--------|-------|
| 1 | Reject `WireMessage::Data` without valid envelope signature | SD-030, SD-140 | S | **Done** |
| 2 | Remove broadcast-to-all-peers on unknown `to_peer` | SD-031 | M | **Done** |
| 3 | Stop gossip re-sign; forward opaque signed blob | SD-080 | M | **Done** |
| 4 | Fail-closed chat when enc key missing | SD-081, SD-141 | S | **Done** |
| 5 | `android:allowBackup="false"` + backup rules | SD-171 | S | **Done** |
| 6 | Desktop: refuse weak vault OR require keyring | SD-131 | S | **Done** |

**P0 product (no code):**

| # | Item | Action |
|---|------|--------|
| 7 | MeshCoin positioning | Store copy + in-app banner = preview only (SD-011 waiver) · **Done** (wallet banner) |
| 8 | Privacy policy | Live URL, not template · **Done** — in-app `privacy_policy.html` + Settings link |

**P0 verification:**

| # | Item |
|---|------|
| 9 | Red-team `MINIAPP_ATTACK_SCENARIOS.md` script + written results · **Done** — [MINIAPP_REDTEAM_RESULTS.md](./MINIAPP_REDTEAM_RESULTS.md) |
| 10 | Regression tests for SD-030, SD-031 · **Done** — `mycena-node/tests/sd030_unsigned_drop.rs` |

---

## P1 — Immediately after beta (0–3 months)

| # | Item | Debt | Effort |
|---|------|------|--------|
| 1 | Per-author rate limits (direct + gossip) | SD-032 | S · **Done** |
| 2 | Replay window + monotonic envelope nonces | SD-060 | S · **Done** |
| 3 | Signed bootstrap manifest (multi-relay) | SD-022, SD-091 | M |
| 4 | Multi-relay pool (3+ operators) | SD-020 | M |
| 5 | Curator peer allowlist for revocations + listings | new | M |
| 6 | Mini-app: mandatory sig OR hardened sideload tier | SD-041, SD-240 | M · **Done** |
| 7 | Remove `unsafe-inline` entirely (reject bundles) | SD-040 | M · **Done** |
| 8 | Safety numbers / key change UI | SD-102 | M |
| 9 | `cargo vet` / SBOM in CI | SD-150 | M |
| 10 | FFI + wire fuzz harness | SD-151 | M |
| 11 | Update stale security docs (§2–12 hardening) | doc | S |
| 12 | Android UniFFI CI + `:miniapp` instrumented smoke | ops | M |
| 13 | Emergency signed remote config (kill switch) | SD-200 | S |
| 14 | Relay status minimal public surface | SD-003 | S |

---

## P2 — Research / long-term (6–18+ months)

| # | Item | Debt | Horizon |
|---|------|------|---------|
| 1 | Metadata resistance (padding, cover traffic) | SD-070 | 6–12 mo |
| 2 | ~~Double Ratchet 1:1~~ | SD-100 | **Done (2026-07)** — [ADR 004](./adr/004-double-ratchet-e2e.md) |
| 3 | MLS / sender keys groups | SD-101, SD-242 | 12–18 mo |
| 4 | Pluggable transport (Tor/Nym) | SD-180 | 18 mo |
| 5 | WASM sandbox executor | H22 | 12+ mo |
| 6 | Desktop utility process isolation | SD-050 | 12 mo |
| 7 | BFT / witness reputation graph (if MXC continues) | SD-011 | 18+ mo |
| 8 | Shade privacy overlay | PRIVACY_EXPERIMENTS | R&D |
| 9 | Pairwise rotating identifiers | SD-190 | L |

---

## Waivers (only if P0 code not finished)

| Waiver | Acceptable only if… | Not acceptable if… |
|--------|---------------------|---------------------|
| SD-030/031 | LAN-only closed beta, <10 peers, no Internet bootstrap | Public Play launch, activist campaign |
| SD-081 | Beta labeled “encryption experimental”; plaintext disabled in UI | “Secure messenger” marketing |
| SD-040 partial | All store bundles strict-CSP; sideload disabled | Open sideload + journalist use |
| SD-011 MXC | Preview credits only, hard caps, no fiat claims | Payments product |

---

## Success metrics (Phase 1)

| Metric | Target |
|--------|--------|
| `messages_dropped_no_sig` / wire `Data` | → 100% unsigned dropped |
| Mean outbound fanout (unknown dest) | 0 (drop) or ≤2 |
| Mini-app red-team AS-01,02,05–07 | Blocked |
| Open P0 without waiver | 0 |
