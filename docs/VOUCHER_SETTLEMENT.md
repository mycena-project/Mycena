# Voucher Settlement — Offline Payments with Retroactive Double-Spend Detection

MeshCoin vouchers are **issuer-signed payment promises** for offline commerce. They are **not** globally consistent until an online-capable relay merges redemption views.

---

## Voucher fields

| Field | Purpose |
|-------|---------|
| `max_amount_muon` | Hard cap on redemption |
| `expires_at_ms` | Default 24h (`VOUCHER_DEFAULT_TTL_MS`) |
| Issuer Ed25519 signature | Proves issuer intent |
| `voucher_id` | Blake3 digest of issuer + amount + issue time |

---

## Risk levels (`VoucherRiskLevel`)

| Level | Meaning | Spendable? |
|-------|---------|------------|
| **Unsettled** | Accepted locally; not yet merged globally | No |
| **Settled** | Relay found no conflicting redemption | Promoted to `pending_received` |
| **DoubleSpendDetected** | Same voucher redeemed to multiple recipients | Losers clawed back |
| **Expired** | Past `expires_at_ms` | No |
| **Revoked** | Invalid signature / policy | No |

---

## Recipient UX (required honesty)

Until settlement completes, show:

> **Unsettled voucher payment — risk X.XX MXC until online settlement**

After merge detects conflict on a losing redemption:

> **Double-spend detected at settlement — X.XX MXC may be clawed back**

`unsettled_voucher_muon` is **excluded** from `authorize_spend` / `SpendAuthorization` (ADR 006 SettlementRequired).

---

## Double-spend model: detect at merge, do not prevent offline

1. Issuer signs voucher offline and hands it to recipient(s).
2. Recipient (or relay) calls `accept_redemption_offline` — **always succeeds locally** if signature + expiry + cap OK.
3. Local ledger credits `unsettled_voucher_muon` (non-spendable).
4. Partitions may each accept a **different** recipient for the **same** voucher.
5. When partitions merge, relay calls `settle_voucher`:
   - **One recipient** → `Settled`, promote winner to `pending_received`.
   - **Multiple recipients** → `DoubleSpendDetected`; **earliest `redeemed_at_ms` wins**; others clawed back.

### Residual risk (explicit)

| Phase | Merchant exposure |
|-------|-------------------|
| Offline accept | Full voucher amount until settlement |
| Issuer overcommit (TX + voucher) | **Prevented locally** via `voucher_reserved_muon` hold at issue time |
| After merge, loser | Clawback of unsettled credit; goods already delivered are **not** automatically reversed |
| After merge, winner | Issuer debited; credit moves to pending (witness path unchanged) |

**We do not prevent partition double-spend upfront.** We detect it at settlement and claw back ledger credits best-effort.

---

## Gossip payloads

- `CoinPayload::PaymentVoucher` — register issuer promise
- `CoinPayload::VoucherRedemption` — redemption claim
- `CoinPayload::VoucherSettlementSync` — relay merges redemption set + triggers `settle_voucher`

---

## Simulation

See `mycena-sim::coin_scenarios::voucher_partition_double_spend` — Alice → Bob (partition A) and Carol (partition B); merge detects double-spend, Bob wins, Carol clawed back.

---

## Related

- [DOUBLE_SPEND_TRADEOFFS.md](./DOUBLE_SPEND_TRADEOFFS.md)
- [adr/006-mesh-consistency-model.md](./adr/006-mesh-consistency-model.md)
- [OFFLINE_SETTLEMENT_MODEL.md](./OFFLINE_SETTLEMENT_MODEL.md)
