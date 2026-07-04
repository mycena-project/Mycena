# ADR 006: Mesh consistency model

**Status:** Accepted (coin typed first)  
**Date:** 2026-07-03  
**Context:** Mycelium is offline-first and partition-tolerant. Each device holds a **local sled view** of messages, contacts, and ledger state. There is no global coordinator, so code must not assume **Strong** (linearizable, network-wide) reads — e.g. “fetch every peer’s balance, then spend” or “message delivered everywhere before showing ✓”.

---

## Decision

1. Introduce [`Consistency`](../../crates/mycelium-core/src/consistency.rs) in **mycelium-core**:
   - `Local` — single device; may use propagation caps (SOS).
   - `EventuallyConsistent` — mergeable / anti-entropy; stale reads OK.
   - `Strong` — **not available on mesh**; enum variant documents forbidden assumptions only.
   - `SettlementRequired` — witness-quorum settlement before treating value as spendable.

2. Tag state-changing operations with `StateOp<C: ConsistencyModel>` constants (same module).

3. App-layer crates consume types via **`mycelium-l0-facade`** re-exports (layer boundary ADR 005).

4. **First typed enforcement:** `mycelium-coin` — highest double-spend risk.

---

## Operation classification

| Operation | Consistency | Rationale |
|-----------|-------------|-----------|
| **DirectMessage send** | EventuallyConsistent | Relay + gossip fanout; no global delivery or ordering guarantee. UI “sent” = accepted locally for relay. |
| **DirectMessage receive / ingest** | EventuallyConsistent | Seen-cache dedup; author signature checked per hop, not globally. |
| **EncryptedDirect / Group forward** | EventuallyConsistent | Ciphertext forward; E2E auth is end-to-end, mesh is best-effort transport. |
| **Bloom sync (`SyncBloom`, `SyncIds`, …)** | EventuallyConsistent | Anti-entropy hint; false positives/negatives; not authoritative inventory. |
| **Gossip bulletin publish** | EventuallyConsistent | Scoped topic; TTL prune; merge by local store rules. |
| **Contact upsert / sync** | EventuallyConsistent | Mergeable contact book; pending/accepted status merges locally. |
| **Invite parse / seed enc key** | EventuallyConsistent | TOFU from QR; upgraded when signed v3 / PeerInfo confirms. |
| **PeerInfo / ratchet bundle** | EventuallyConsistent | TOFU upgrade path; not a global directory. |
| **SOS broadcast** | Local (+ propagation policy) | Composed locally; `MaximumPropagation` caps hop fanout; no settlement. |
| **Mini-app install** | Local | Bundle verify + local store write; reproducible hash is local gate. |
| **Mini-app store listing gossip** | EventuallyConsistent | Cached listing; install still re-verifies bundle locally. |
| **Coin transfer (outgoing TX)** | SettlementRequired | Spend only **`balance_muon`** (witness-settled); never `pending_received` or remote `BalanceResponse`. |
| **Coin transfer (incoming TX apply)** | EventuallyConsistent → SettlementRequired | Receiver credits `pending_received` until ≥3 witnesses, then moves to settled balance. |
| **Coin witness** | SettlementRequired | Moves recipient from pending to settled on quorum. |
| **Coin balance query/response** | EventuallyConsistent | Peer **hint** only; must not gate spends (type: `PeerBalanceHint`). |
| **Coin refill (cold→hot)** | SettlementRequired | Cold signs only against its settled local ledger. |
| **Coin hot-wallet cap return** | SettlementRequired | Uses settled balance before broadcasting return TX. |
| **Payment voucher issue** | SettlementRequired | Reserves issuer `voucher_reserved_muon`; reduces spendable until settlement/expiry. |
| **Payment voucher redeem (incoming)** | EventuallyConsistent | Credits recipient `unsettled_voucher_muon` — **not spendable** until merge settlement. |
| **Voucher settlement sync** | SettlementRequired | Relay merges redemption set; retroactive double-spend detection; issuer debit + recipient promote/clawback. |
| **Genesis / faucet credit** | Local | Testnet-only local ledger mutation; not mesh consensus. |

### Forbidden pattern (Strong)

Do **not** implement:

- Global balance aggregation before spend.
- “All peers acknowledged” payment finality on client.
- Cross-device linearizable contact or message ordering.

The type `StrongConsistencyForbidden` has **no mesh constructor** — only ADR/tests reference it.

---

## Coin type system (phase 1)

| Type | Consistency | Role |
|------|-------------|------|
| `AccountBalanceView<EventuallyConsistentModel>` | EventuallyConsistent | UI + peer hints (`confirmed`, `pending`). |
| `AccountBalanceView<SettlementRequiredModel>` | SettlementRequired | `spendable_muon()` = settled only. |
| `send_transaction(tx, auth)` | SettlementRequired | `auth` must match `tx`; uses `apply_outgoing_transaction`. |
| `apply_incoming_transaction` | EventuallyConsistent | Mesh gossip path — no spend token. |
| `apply_outgoing_transaction` | SettlementRequired | Requires matching [`SpendAuthorization`]. |
| `PeerBalanceHint` | EventuallyConsistent | Built from `BalanceResponse`; **no** `Into<SpendAuthorization>`. |
| `VoucherExposureView` | EventuallyConsistent | Recipient unsettled voucher exposure + risk notices; **not** spendable. |
| `PaymentVoucher` / `VoucherRedemption` gossip | EventuallyConsistent → SettlementRequired | Offline accept; settlement at relay merge. |

Outgoing `send_transaction` requires `SpendAuthorization`, forcing callers through settled-balance checks (excluding `voucher_reserved_muon` and `unsettled_voucher_muon`).

---

## Rollout

| Phase | Scope |
|-------|--------|
| **1** | Core enum + coin typed views / spend token |
| **2 (done)** | Node: `NodeCommand::mesh_consistency()`, typed `NodeHandle::mesh_*` send helpers tagged with `StateOp` |
| 3 | App / FFI: expose consistency in metrics or debug API |

---

## Consequences

- Prevents accidental “global balance” spend paths in coin at compile time.
- Remote balance queries cannot authorize transfers without a deliberate unsound `unsafe`/test bypass (none provided).
- Other subsystems adopt the same markers incrementally.

---

## References

- [`crates/mycelium-core/src/consistency.rs`](../../crates/mycelium-core/src/consistency.rs)
- [`crates/mycelium-coin/src/views.rs`](../../crates/mycelium-coin/src/views.rs)
- [`docs/VOUCHER_SETTLEMENT.md`](../VOUCHER_SETTLEMENT.md)
- [`docs/OFFLINE_SETTLEMENT_MODEL.md`](../OFFLINE_SETTLEMENT_MODEL.md)
- [`docs/DOUBLE_SPEND_TRADEOFFS.md`](../DOUBLE_SPEND_TRADEOFFS.md)
