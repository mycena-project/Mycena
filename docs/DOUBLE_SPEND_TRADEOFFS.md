# Double-Spend Tradeoffs — Mesh Emergency Settlement

MeshCoin **does not** solve global double-spend prevention. It **bounds exposure** for offline emergency utility.

---

## The problem

Alice offline in partition A spends credit to Bob. Alice offline in partition B spends same credit to Carol. When partitions merge, **both spends may have valid local witnesses**.

---

## What we optimize for

| Goal | Priority |
|------|----------|
| Commerce continuity during outage | High |
| Bounded financial loss | High |
| Global consensus correctness | **Low** (explicitly deferred) |
| Speculative soundness | **None** |

---

## Current mitigations

| Mechanism | Effect | Limit |
|-----------|--------|-------|
| Local nonce | Stops replay **same device** | No cross-device sync (SD-061) |
| 3-peer witnesses | Social delay to confirm | Sybil-able (SD-011) |
| Balance cap (100 MXC) | Max vault damage | Per-address |
| Transfer cap (25 MXC) | Max single event | Chunked returns needed |
| Velocity limits | Burst slowdown | Local only |
| Pending → confirmed | Delayed credit | Not fraud-proof |
| **Payment vouchers** | Signed cap + expiry; merge settlement | **Double-spend detected at merge, not prevented offline** — see [VOUCHER_SETTLEMENT.md](./VOUCHER_SETTLEMENT.md) |

---

## Voucher double-spend (Scenario D)

Alice issues one signed voucher offline. In partition A, Bob redeems; in partition B, Carol redeems the **same** voucher. Both see `Unsettled` credit locally.

When relays merge:

- Earliest redemption becomes canonical (`Settled` → pending).
- Later redemption(s) → `DoubleSpendDetected`, `unsettled_voucher_muon` clawed back.

**Residual risk:** Merchants who deliver goods on unsettled vouchers bear up to **voucher max amount** until settlement. UI must show: *"Unsettled payment — risk X MXC until settlement"*.

Sim: `cargo test -p mycena-sim voucher_double_spend_detected_at_merge_not_prevented_offline`

---

## Exposure bounding (worked example)

Defaults: 100 MXC vault, 25 MXC/tx, 50 MXC/hour outbound.

Worst naive case in one hour: **50 MXC outbound** (velocity) not 100, unless attacker controls issuance.

Attacker with Sybil witnesses could confirm fraudulent inbound — victim’s **loss** still capped by what victims voluntarily accept as payment.

---

## Tradeoff table

| Approach | Pros | Cons |
|----------|------|------|
| **No consensus (today)** | Simple, offline-fast | Double-spend across partitions |
| **Strong BFT global** | Correctness | Internet-scale complexity; wrong product |
| **Central issuer ledger** | Easy reconcile | Single point; not mesh-native |
| **CRDT merge** | Offline-friendly | Hard with signed spends |
| **Probabilistic audit on sync** | Bounded bandwidth | Delayed detection |
| **Trusted community notary** | Human-legible | Trust bottleneck |

**Chosen path:** Local ledger + gossip witnesses + **hard caps** + future reconciliation experiments.

---

## Partition scenarios

### Scenario A — Same merchant, two tabs

Low risk if merchant checks nonce/idempotency locally (future merchant API).

### Scenario B — Two sellers, one buyer offline

Buyer can double-spend until reconciliation. Sellers should treat unconfirmed credits as **provisional** (UI: “pending witnesses”).

### Scenario C — Reconnect storm

Merkle diff sync (R&D) flags conflicting tx IDs; community policy: freeze vault, manual review.

---

## UX honesty requirements

- Show **pending vs confirmed** clearly  
- Never label preview credits as “cash” or “bank balance”  
- Recommend confirmed threshold for high-trust merchants  

---

## Research directions

1. **Conflict sets** — Store competing spends; UI prompts reconciliation  
2. **Issuer revocation list** — NGO-signed freeze messages  
3. **Amount-tier quorum** — More witnesses for larger (within-cap) amounts  
4. **Time decay** — Unwitnessed spends expire  

See [OFFLINE_RECONCILIATION_EXPERIMENTS.md](./OFFLINE_RECONCILIATION_EXPERIMENTS.md).

---

## When NOT to use MeshCoin

- Salary payments  
- Savings  
- Cross-border remittance at scale  
- High-value commerce without external reconciliation  
- Any context requiring legal tender finality  

---

## Related debt

- SD-011 Witness Sybil  
- SD-061 Cross-peer replay  
- SD-161 Local faucet mint  
