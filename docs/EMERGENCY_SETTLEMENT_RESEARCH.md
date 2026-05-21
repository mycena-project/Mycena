# Emergency Settlement Research — Experimental Ideas

Novel mechanisms for **bounded-risk** offline settlement. Items marked **R&D** are not implemented unless noted.

---

## 1. Trust-radius limits (R&D)

**Idea:** Credits only settle when sender and receiver share a trust path ≤ N hops with minimum reputation.

**Why:** Reduces global value transfer; aligns with disaster **community** cells.

**Open questions:** How to bootstrap reputation offline? Gaming colluding rings?

---

## 2. Community-scoped balances (R&D)

**Idea:** Credits tagged with `scope` (e.g. `relief/berlin-mitte-2026`). Valid only within peers that opted into scope.

**Why:** Prevents credits from becoming generic bearer assets.

**Sketch:** Extend `AccountState` with scope field; reject cross-scope transfers unless explicit bridge policy (default deny).

---

## 3. Locality decay (R&D)

**Idea:** Witness weight = `base × decay(hops, time_since_seen)`.

**Why:** Prefer physically nearby witnesses; weaken remote Sybil clusters.

**Depends on:** Peer discovery trust signals (BLE proximity, manual vouch).

---

## 4. Transaction aging (R&D)

**Idea:** Pending credits lose witness eligibility after T hours unless re-attested.

**Why:** Limits stale offline branches; pushes reconciliation.

---

## 5. Social fraud detection (R&D)

**Idea:** Nodes share **hashed** anomaly flags (velocity spike, cap jamming, circular flows) without full graph deanonymization.

**Why:** Community defense without centralized AML database.

**Privacy:** Use bloom filters + rate limits; see [PRIVACY_ROADMAP.md](./PRIVACY_ROADMAP.md).

---

## 6. Probabilistic reconciliation (R&D)

**Idea:** On reconnect, peers exchange Merkle summaries of recent settlement IDs; sample disputes for full tx fetch.

**Why:** Bandwidth-efficient sync after partition.

See [OFFLINE_RECONCILIATION_EXPERIMENTS.md](./OFFLINE_RECONCILIATION_EXPERIMENTS.md).

---

## 7. Delayed consistency (partial — today)

**Today:** 3 witnesses before credit; gossip delay.

**Research:** Adjustable quorum by amount tier (e.g. 2 witnesses for &lt;5 MXC, 5 for &gt;20 MXC).

---

## 8. Mesh-native escrow (R&D)

**Idea:** Two-phase commit: `EscrowLock` → delivery attestation → `EscrowRelease`.

**Use case:** Offline trade “pay when package received” within cap.

**Risk:** Dispute resolution undefined — needs human/community fallback.

---

## 9. Emergency issuance (R&D)

**Idea:** Certified issuer keys (NGO, municipality) mint **time-limited** relief credits with on-device cap bypass **only** for scoped relief.

**Guardrails:** Max issuance per event; public issuer registry in app; audit log.

**Regulatory:** Highest scrutiny — counsel required.

---

## 10. Temporary local settlement pools (R&D)

**Idea:** Event organizer holds pooled credits for festival/disaster shelter; distributes micro-credits to participants.

**Model:** Closed-loop voucher operator — not open P2P mint.

---

## Priority matrix

| Idea | Impact | Complexity | Beta? |
|------|--------|------------|-------|
| Enforce payment expiry | High | Low | **Next** |
| Trust-weighted witnesses | High | Medium | P1 |
| Community scope | High | Medium | P2 |
| Trust-radius | Medium | High | P2 |
| Probabilistic reconciliation | Medium | High | P2 |
| Emergency issuance | High | Very high | Research only |

---

## Implemented (May 2026)

- `SettlementPolicy` balance / transfer / velocity caps  
- `VelocityTracker` sled window  
- UX string repositioning (Android EN)  

See [MESHCOIN_ARCHITECTURE_REVIEW.md](./MESHCOIN_ARCHITECTURE_REVIEW.md).
