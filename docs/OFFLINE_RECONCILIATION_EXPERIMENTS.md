# Offline Reconciliation Experiments

Protocols to merge partitioned settlement ledgers **without** claiming global cryptocurrency consensus.

---

## Experiment 1 — Merkle tail exchange (R&D)

**On reconnect**, peers exchange:

```
{ address, merkle_root, tail_count, last_timestamp }
```

If roots differ, request `diff` of tx IDs in last N hours.

**Metrics:** Bytes per reconcile, false-positive rate.

**Failure mode:** Adversarial peer floods diff requests — rate limit (SD-002 pattern).

---

## Experiment 2 — Conflict bipartite graph (R&D)

Build graph: nodes = tx IDs; edge = same `(from, nonce)` or same `(from, amount, time_window)` with different `to`.

Components with degree &gt; 1 → **conflict component**.

**Actions:**

- Freeze `from` address pending review  
- Notify counterparties “settlement contested”  
- Optional community vote (mesh-native, R&D)

---

## Experiment 3 — Probabilistic sampling (R&D)

For large histories, sample 1/k tx IDs for full validation; escalate if mismatch rate &gt; ε.

Suitable for **low-value** regimes only; document statistical confidence bounds.

---

## Experiment 4 — Delayed global anchor (R&D)

When **Internet** available, optional upload of daily settlement summary hash to transparency log (not chain).

**Purpose:** Audit trail for NGOs — not live consensus.

**Privacy:** Hash-only vs full tx disclosure — policy choice.

---

## Experiment 5 — Scoped reconciliation (R&D)

Only reconcile txs with matching `community_scope` tag.

Reduces cross-community bleed when partitions are geographic.

---

## Experiment 6 — Payment request expiry enforcement (ready)

**Status:** `PaymentRequest::is_expired()` exists; wire into:

- `CoinNode::submit_transfer_from_identity_path`  
- FFI `send_transaction`  
- Android send dialog  

**Test:** Send after expiry → rejected with clear error.

---

## Experiment 7 — Credit lot expiry (R&D)

Split balance into FIFO lots with `expires_at_ms`. Spend oldest first.

Reduces long-term hoarding on device.

---

## Sim harness suggestions

Extend `mycena-sim` with:

1. Two partitions, one double-spend, merge event  
2. Measure victim balance under caps  
3. Witness Sybil fraction vs confirmation rate  

---

## Success criteria (beta)

| Criterion | Target |
|-----------|--------|
| Reconcile latency | &lt; 5 s for 1k events |
| False freeze rate | &lt; 1% in benign sim |
| User-visible conflicts | Clear, non-crypto jargon |
| Bandwidth | &lt; 50 KB per daily reconcile |

---

## Non-goals

- Proof-of-work anchoring  
- Cross-chain bridges  
- Exchange-grade finality  

See [DOUBLE_SPEND_TRADEOFFS.md](./DOUBLE_SPEND_TRADEOFFS.md).
