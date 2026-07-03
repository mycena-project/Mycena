# Mycelium — Security Roadmap

**Horizon:** 2026 Q2–2027  
**Principle:** Ship beta only with known, documented, accepted risk—never unknown risk.  
**Registers:** [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) · [ARCHITECTURE_RISKS.md](./ARCHITECTURE_RISKS.md) · [BETA_SECURITY_ASSESSMENT.md](./BETA_SECURITY_ASSESSMENT.md) · [SECURITY_REMEDIATION_ROADMAP.md](./SECURITY_REMEDIATION_ROADMAP.md)

---

## Phase 0 — Beta gate (before wide public beta)

**Goal:** Close beta-blockers or obtain written risk acceptance.

| Priority | Item | Debt IDs | Effort | Owner |
|----------|------|----------|--------|-------|
| P0 | Reject unsigned `WireMessage::Data` (protocol v2) | SD-030, SD-140 | S | node — **done** |
| P0 | Fail-closed encrypted chat (no plaintext fallback) | SD-081, SD-141 | S | app — **done** |
| P0 | Cap relay fanout / no broadcast-to-all-peers | SD-031 | M | node — **done** |
| P0 | Fix gossip re-sign semantics | SD-080 | M | node — **done** |
| P0 | Mini-app install signature verification | SD-041, SD-240 | S | app-store — **done** |
| P0 | Tighten CSP (remove `unsafe-inline` or nonce-only) | SD-040 | M | Android + desktop — **done** |
| P0 | `android:allowBackup="false"` + full-backup exclusion | SD-171 | S | Android — **done** |
| P0 | Desktop: refuse weak vault fallback | SD-131 | S | node/secrets — **done** |
| P0 | MeshCoin UX = preview only (no real-money copy) | SD-011, SD-160 | S | product/legal |
| P0 | Emergency settlement caps (balance, tx, velocity) | SD-011, SD-160 | S | mycelium-coin |

**MeshCoin repositioning docs:** [MESHCOIN_POSITIONING.md](./MESHCOIN_POSITIONING.md) · [MESHCOIN_ARCHITECTURE_REVIEW.md](./MESHCOIN_ARCHITECTURE_REVIEW.md)

**Exit criteria:** All P0 done OR waiver signed; `cargo test` + `clippy` + Android security checklist; red-team half-day on mini-apps.

---

## Phase 1 — Hardening (beta + 3 months)

**Goal:** Resist A1–A2 adversaries at modest scale.

| Priority | Item | Debt IDs | Effort |
|----------|------|----------|--------|
| P1 | Per-author rate limits (direct + gossip) | SD-032 | S | **Done** — `ingest_guard.rs` |
| P1 | Replay window + monotonic nonces | SD-060 | S | **Done** — 30 min freshness + per-author id window |
| P1 | `postMessage` origin lock (desktop) | SD-051 | S |
| P1 | Mini-app permission UI at install | SD-042 | M |
| P1 | Signed remote bootstrap manifest | SD-091, SD-221 | M |
| P1 | Multi-relay pool (3+ operators) | SD-020, SD-220 | M |
| P1 | Supply-chain: `cargo vet`, SBOM in CI | SD-150 | M |
| P1 | FFI fuzzing harness | SD-151 | M |
| P1 | Safety numbers / key change UI | SD-102 | M |
| P1 | Emergency signed config (kill switch) | SD-200 | S |

**Exit criteria:** No open **High** beta-blockers; Sybil spam mitigated in soak test; incident playbook drill completed.

---

## Phase 2 — Privacy architecture (6–12 months)

**Goal:** Tor-informed metadata resistance for high-risk users.

| Item | Debt IDs | Effort |
|------|----------|--------|
| Traffic shaping + padding layer | SD-070 | L |
| Pairwise identifiers for direct chat | SD-190 | L |
| Expiring introduction QR tokens | SD-191 | M |
| Cover traffic mode (battery tradeoff) | SD-070 | L |
| Minimized PeerInfo / lazy handshake | SD-004 | M |
| Private coin gossip (if coin continues) | SD-002 | L |

**Exit criteria:** Independent metadata analysis shows reduced leakage vs Phase 0 on fixed topology testbed.

---

## Phase 3 — Cryptographic maturity (12–18 months)

**Goal:** Signal-parity for 1:1 and groups.

| Item | Debt IDs | Effort |
|------|----------|--------|
| Double Ratchet (1:1) | SD-100 | **Done (v1)** — [ADR 004](./adr/004-double-ratchet-e2e.md) |
| MLS or Sender Keys (groups) | SD-101, SD-242 | L |
| EncryptedGroup per-hop auth | forwarding | M |
| Hardware-backed keys (StrongBox) | SD-120 | M |

---

## Phase 4 — Economic layer (only if product commits to coin)

**Do not start until legal + threat model for money transmission.**

| Item | Debt IDs | Effort |
|------|----------|--------|
| BFT or witness reputation graph | SD-011 | L |
| Global nonce / anti-replay | SD-061 | M |
| Remove local faucet from release | SD-161 | S |
| Hardware wallet / watch-only default | SD-160 | M |

---

## Phase 5 — Nation-state resilience (18+ months)

| Item | Debt IDs | Effort |
|------|----------|--------|
| Pluggable transports (Tor/Nym) | SD-180 | L |
| Mini-app isolated process | SD-050 | L |
| Decoy / duress mode | SD-201, SD-211 | L |
| Relay federation + operator diversity | SD-220 | L |

---

## Metrics (track quarterly)

| Metric | Target (Phase 1) |
|--------|------------------|
| `messages_dropped_invalid_sig` / total wire | measurable, trending down |
| `messages_dropped_no_sig` | → 0 for supported clients |
| Mean forward fanout per message | < 3 |
| Mini-app installs without valid sig | 0 |
| Days since last security register review | < 14 |
| Open Critical debts | 0 at beta gate |

---

## Non-goals (explicit)

- **Not** promising anonymity equal to Tor/I2P in beta.
- **Not** guaranteeing moderation of illegal mesh content.
- **Not** insuring MXC value.
- **Not** supporting rooted devices as first-class secure.

---

## References

- User-facing: [security-for-users.md](./security-for-users.md)
- Network: [network-hardening.md](./network-hardening.md)
- Abuse ops: [ABUSE_RESPONSE_PLAYBOOK.md](./ABUSE_RESPONSE_PLAYBOOK.md)
- Privacy: [PRIVACY_ROADMAP.md](./PRIVACY_ROADMAP.md)
