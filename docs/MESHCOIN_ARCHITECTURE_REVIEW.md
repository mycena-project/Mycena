# MeshCoin Architecture Review — Emergency Settlement Repositioning

**Date:** May 2026  
**Scope:** `crates/mycena-coin`, FFI wallet surface, Android/desktop UX  
**Verdict:** Suitable as **bounded preview utility** after repositioning; **not** production financial infrastructure.

---

## Executive summary

MeshCoin today is a **local account ledger** with **Ed25519 signed transfers**, **mesh gossip**, and a **3-peer witness heuristic**. It was shaped like a “coin” (MXC, wallet, faucet, hot wallet) but lacks global consensus. That mismatch created **speculative and regulatory risk** without delivering cryptocurrency-grade guarantees.

**Repositioning:** Emergency settlement layer with **hard caps** (implemented May 2026), terminology migration, and honest offline tradeoff documentation.

---

## Current architecture (as-built)

```text
mycena-app (gossip dispatch)
       ↓
CoinNode ──→ LocalLedger (+ SettlementPolicy, VelocityTracker)
       ↓
CoinTransport (FFI → libp2p gossip, scope mycena/coin/v1)
```

| Component | Role |
|-----------|------|
| `ledger.rs` | Balances, nonces, witnesses, apply rules |
| `node.rs` | Broadcast, incoming handle, faucet |
| `hot_wallet.rs` | Capped spending cache, refill |
| `payment.rs` | mxcpay: QR requests |
| `settlement_policy.rs` | **NEW** caps & velocity |
| `velocity.rs` | **NEW** hourly outbound window |

---

## Speculative & regulatory risks (identified)

### Critical / high

| Risk | Why it matters | Mitigation |
|------|----------------|------------|
| **Witness Sybil (SD-011)** | 3 arbitrary peers confirm credit | Preview disclaimer; reputation witnesses (P1) |
| **No global truth** | Double-spend across partitions | Caps; pending UI; reconciliation R&D |
| **Hot keys on device (SD-160)** | Theft = spend | Cache cap; cold split; not “bank” |
| **Local faucet mint (SD-161)** | Trustless inflation locally | Remove for release |
| **Crypto terminology** | Implies investment asset | String migration (in progress) |

### Medium

| Risk | Notes |
|------|-------|
| Balance gossip metadata (SD-002) | Economic surveillance |
| Cross-peer replay (SD-061) | Nonce not global |
| Payment expiry not enforced | Stale QR attacks |
| Fixed 1000 muon fee | Minor; centralize config |

### Low (acceptable for preview)

| Risk | Notes |
|------|-------|
| Memo 64-byte limit | Fine |
| mxcpay URI scheme | Branding; keep technical |

---

## Dangerous terminology audit

| Location | Before | After / target |
|----------|--------|----------------|
| Android `wallet_title` | Wallet | Emergency credits |
| Android notice | MeshCoin experimental | Offline resilience credits |
| `terms.html` | MeshCoin technology preview | Align with positioning doc |
| Desktop `Wallet.svelte` | Wallet (muon) | Needs same pass |
| Code crate name | `mycena-coin` | Keep internal; public “settlement” |
| Gossip scope | `mycena/coin/v1` | Future: `settlement/v1` alias |

---

## AML-attractive patterns

| Pattern | Attractiveness | Control |
|---------|---------------|---------|
| P2P transfers | Medium | Caps + velocity |
| Unbounded balance | **Was high** | **Now capped** |
| Fast confirmation | Medium | Witness delay + pending state |
| QR pay requests | Medium (phishing) | Native confirm dialogs |
| No KYC | Inherent to mesh P2P | Product positioning + caps |
| Exchange | **None** | ✓ |

---

## Unnecessary crypto patterns to remove or defer

1. **“Faucet” user-facing** → “preview credits”  
2. **Hot/cold wallet crypto analogy** → “spending cache / main vault”  
3. **Witness count as “consensus” marketing** → “local confirmations”  
4. **Desktop wallet without experimental banner** → parity with Android  
5. **Future:** Rename gossip scope only with migration plan  

**Keep (utility):** Signed spends, local ledger, QR payment requests for merchant flow.

---

## Complexity to remove (roadmap)

| Item | Recommendation |
|------|----------------|
| Duplicate fee constants | Single `SETTLEMENT_FEE_MUON` |
| Unenforced payment expiry | Enforce next sprint |
| Genesis credit in production paths | Gate `cfg(testnet)` |
| Balance query on gossip | Opt-in + rate limit |

---

## Simplified target architecture

```text
┌──────────────────────────────────────────┐
│ Emergency Credits UX (capped, no charts) │
├──────────────────────────────────────────┤
│ Settlement API (send, balance, request)  │
├──────────────────────────────────────────┤
│ Policy engine (caps, velocity, freeze)   │
├──────────────────────────────────────────┤
│ Local settlement ledger + witnesses      │
├──────────────────────────────────────────┤
│ Optional: reconciliation on reconnect    │
├──────────────────────────────────────────┤
│ Mesh gossip (scoped, rate-limited)       │
└──────────────────────────────────────────┘
```

**Remove from near-term scope:** Exchange, staking, pools, bridges, price oracles.

---

## Implementation priorities

### P0 (beta-safe) — done / in progress

- [x] `SettlementPolicy` defaults in `apply_transaction`  
- [x] Velocity tracker  
- [x] Android EN string repositioning  
- [x] Positioning + regulatory + offline model docs  
- [ ] Enforce `PaymentRequest` expiry on send  
- [ ] Desktop wallet disclaimer + terminology  

### P1 (strongly recommended)

- Witness reputation weighting (not just count)  
- Remove or gate `request_faucet_coins`  
- Balance query rate limits (SD-002)  
- Freeze / quarantine API (policy flag on address)  
- Anomaly scoring (velocity + circular flow heuristic)  

### P2 (research)

- Community-scoped credits  
- Merkle reconciliation on reconnect  
- Trust-radius limits  
- Credit lot expiry  

---

## Beta-safe configuration defaults

From `SettlementPolicy::beta_defaults()`:

| Parameter | Value |
|-----------|-------|
| Max vault balance | 100 MXC |
| Hard ceiling (configurable) | 300 MXC |
| Max single settlement | 25 MXC |
| Max serialized event | 4096 bytes |
| Settlements per hour | 20 |
| Outbound per hour | 50 MXC |
| Witness quorum | 3 (unchanged; disclose risk) |
| Hot cache default | 50 MXC (≤ vault cap) |
| Faucet preview | 10 MXC once |

---

## Defensive public positioning

**One sentence:**  
*Mycena emergency credits help neighbors trade small amounts offline during outages — capped, non-investment utility, not a cryptocurrency.*

**Do not say:** decentralized bank, digital gold, invest, yield, market cap.

Full copy: [MESHCOIN_POSITIONING.md](./MESHCOIN_POSITIONING.md), [REGULATORY_DESIGN_NOTES.md](./REGULATORY_DESIGN_NOTES.md).

---

## Test coverage note

New tests:

- `settlement_policy_rejects_oversized_transfer`  
- `settlement_policy::beta_defaults_match_prompt`  

Existing: transfer, double-spend nonce, witnesses, hot cap, refill.

---

## Conclusion

MeshCoin can evolve into a **credible emergency settlement layer** if we **stop imitating cryptocurrency product shapes** and **enforce bounded-risk mechanics in code and copy**. The May 2026 policy module is the first protocol-level commitment; witness security and reconciliation remain the largest technical debts before any “real money” discussion — which this product direction explicitly avoids.
