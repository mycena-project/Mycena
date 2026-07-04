# Accepted security residuals (pre–public beta)

**Purpose:** Written record of risks we **accept** for Play public beta, pending Phase C/D work or external review sign-off.

**Authority:** Engineering + product; legal review recommended before wide marketing.

**Cross-ref:** [THREAT_MODEL.md](./THREAT_MODEL.md) §5 · [BETA_SECURITY_ASSESSMENT.md](./BETA_SECURITY_ASSESSMENT.md)

---

## Accepted for public beta

| # | Residual | Rationale | Debt / doc |
|---|----------|-----------|------------|
| R1 | **Offline / partition double-spend** until voucher settlement | Emergency utility model; merge detects retroactively | SD-011, SD-061; [VOUCHER_SETTLEMENT.md](./VOUCHER_SETTLEMENT.md) |
| R2 | **Metadata not confidential** (PeerId, scopes, timing, size) | E2E protects content only | SD-001, SD-070; Privacy Phase C |
| R3 | **Sybil witness quorum** for MeshCoin | Preview-only MXC; caps + disclaimers | SD-010, SD-011 |
| R4 | **WebView TCB** for mini-apps | Industry-standard Chromium surface | SD-050 |
| R5 | **Mesh copies not centrally erasable** | Decentralized replication | Privacy policy; SD-230 |
| R6 | **Relay sees IP + circuit metadata** | libp2p relay v2 design | SD-020, SD-210; [RELAY_OPERATOR_GUIDE.md](./RELAY_OPERATOR_GUIDE.md) §10 |
| R7 | **Custody carrier sees routing metadata** | DTN store-and-forward | [CUSTODY_DTN.md](./CUSTODY_DTN.md); THREAT_MODEL §5.2 |
| R8 | **No nation-state / traffic-analysis resistance** | Deferred to Shade / Tor | SD-180, SD-190 |
| R9 | **Group PSK in QR** | MLS deferred | SD-101, SD-242 |
| R10 | **Balance query responses disabled by default** | SD-002 partial — txs still gossip on coin scope | `CoinNode::respond_to_balance_queries = false` |

---

## Not accepted (mitigated — do not regress)

| ID | Item |
|----|------|
| SD-030, SD-140 | Unsigned wire `Data` |
| SD-031 | Relay fanout amplification |
| SD-080 | Gossip re-sign |
| SD-081, SD-141 | Plaintext chat fallback |
| SD-040, SD-041, SD-240 | Mini-app CSP / sideload signature |
| SD-171 | Android backup |
| SD-131 | Desktop weak vault (new installs) |

---

## Sign-off (fill when approved)

| Role | Name | Date | Commit SHA |
|------|------|------|------------|
| Engineering | | | |
| Product | | | |
| External reviewer | | | |

---

## Change log

| Date | Change |
|------|--------|
| Jul 2026 | Initial residual register for Play beta |
