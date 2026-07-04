# Offline Settlement Model

How MeshCoin settlement works today and how it should evolve for **bounded emergency utility**.

---

## Layered model

```
┌─────────────────────────────────────────────────────────────┐
│  UX: Emergency credits, send/receive, mxcpay requests       │
├─────────────────────────────────────────────────────────────┤
│  CoinNode: gossip propagate, witness, faucet (preview)      │
├─────────────────────────────────────────────────────────────┤
│  LocalLedger: account balances, nonces, witnesses           │
├─────────────────────────────────────────────────────────────┤
│  SettlementPolicy: caps, velocity, tx size                │
├─────────────────────────────────────────────────────────────┤
│  Transport: AppMessage scope mycelium/coin/v1 (gossip)     │
└─────────────────────────────────────────────────────────────┘
```

There is **no global chain state**. Each device holds a **local settlement ledger** of events it has validated.

---

## Settlement event lifecycle

1. **Create** — Sender signs `Transaction` (from, to, amount, fee, nonce, memo).  
2. **Local apply** — `LocalLedger::apply_transaction` checks signature, nonce, funds, **policy caps**, velocity.  
3. **Gossip** — `CoinPayload::Transaction` broadcast on mesh.  
4. **Witness** — Peers add themselves to `witnesses` set; at **≥3** witnesses, recipient balance confirms.  
5. **Reconciliation** — *Partial today*: no global merge; conflicts resolved by local view + voucher settlement at relay merge (see below).

---

## Payment vouchers (offline IOU)

Signed [`PaymentVoucher`](../../crates/mycelium-coin/src/voucher.rs) promises with capped amount + expiry:

1. **Issue** — Issuer reserves `voucher_reserved_muon` from settled balance; signs voucher; gossips.  
2. **Redeem offline** — Recipient gets `unsettled_voucher_muon` (non-spendable); UI shows risk until settlement.  
3. **Merge / settle** — Online relay ingests redemption set, detects partition double-spend **retroactively**, debits issuer, promotes winner to `pending_received`, claws back losers.

Double-spend across partitions is **detected at merge, not prevented offline**. See [VOUCHER_SETTLEMENT.md](./VOUCHER_SETTLEMENT.md).

---

## Trust assumptions (honest)

| Assumption | Reality |
|------------|---------|
| Witnesses are honest | **False** under Sybil — any 3 peer IDs suffice (SD-011) |
| Local ledger is truth | Only for this device’s view |
| Offline send is final | **Delayed** until witnesses; double-spend possible across partitions |
| Internet required | **No** — mesh gossip suffices when peers exist |

**Acceptable for:** low-value emergency credits with caps.  
**Unacceptable for:** high-value or legal-tender replacement.

---

## Bounded double-spend exposure

Maximum loss per compromised partition is bounded by:

- `max_transfer_muon` per event  
- `max_balance_muon` per vault  
- Velocity windows  
- Witness delay window (time to detect conflicting spends on reconnect)

Formula (informal):

```
exposure ≤ min(cap_balance, cap_tx, velocity_hourly_cap) × partition_factor
```

---

## Locality-aware settlement (current + planned)

**Today:** Gossip propagates to mesh neighbors; no geographic enforcement.

**Planned (research):**

- Trust-radius limits (only settle with peers above reputation threshold)  
- Community-scoped balances (credits valid only within scope tag)  
- Locality decay (witness weight drops with hop count / time)  

See [EMERGENCY_SETTLEMENT_RESEARCH.md](./EMERGENCY_SETTLEMENT_RESEARCH.md).

---

## Expiring units (planned)

`PaymentRequest` already has `expires_at_ms` — **not enforced on send** yet.

Future:

- Credit lots with `valid_until_ms`  
- Stale pending credits auto-expire  
- Reduces hoarding and long-term offline inflation  

---

## Hot / cold split (spending cache)

- **Cold vault:** main balance address (user-controlled).  
- **Hot cache:** capped on-device spending pool; auto-refill from cold on Internet; excess returned in chunks respecting transfer cap.

This mirrors **prepaid purse + safe** — not custodial exchange.

---

## No financialization primitives

The protocol intentionally omits:

- Swap / pool / bridge messages  
- Reward distribution / inflation schedule for miners  
- Oracle price feeds  

---

## Configuration reference

`SettlementPolicy::beta_defaults()` in `settlement_policy.rs`:

| Parameter | Default |
|-----------|---------|
| Max balance | 100 MXC |
| Hard max (config) | 300 MXC |
| Max transfer | 25 MXC |
| Max tx bytes | 4096 |
| Tx / hour | 20 |
| Outbound / hour | 50 MXC |

---

## Related

- [VOUCHER_SETTLEMENT.md](./VOUCHER_SETTLEMENT.md)
- [DOUBLE_SPEND_TRADEOFFS.md](./DOUBLE_SPEND_TRADEOFFS.md)
- [OFFLINE_RECONCILIATION_EXPERIMENTS.md](./OFFLINE_RECONCILIATION_EXPERIMENTS.md)
- [MESHCOIN_ARCHITECTURE_REVIEW.md](./MESHCOIN_ARCHITECTURE_REVIEW.md)
