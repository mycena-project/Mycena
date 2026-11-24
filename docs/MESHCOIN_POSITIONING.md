# MeshCoin — Emergency Settlement Layer Positioning

**Status:** Product & protocol repositioning (May 2026)  
**Audience:** Engineering, legal review, public beta messaging  
**Not:** A cryptocurrency, investment product, or speculative asset class.

---

## What MeshCoin is

MeshCoin (MXC) is a **bounded, offline-first emergency settlement layer** inside Mycena. It lets nearby devices exchange **small, temporary utility credits** when normal payment rails or connectivity fail.

Think of it as:

- Emergency vouchers for local commerce continuity
- Transit-style prepaid credits on a mesh
- Temporary IOUs between trusted neighbors
- Disaster-recovery settlement units with hard caps

**Not** as:

- Bitcoin, Ethereum, or investment assets
- A store of wealth
- A trading or yield instrument
- A global value transfer network

---

## Design principles

| Principle | Implementation direction |
|-----------|-------------------------|
| Low value only | Balance cap ~100 MXC (€100 utility equivalent), hard max 300 MXC |
| Small transfers | Per-event cap (default 25 MXC) |
| Anti-hoarding | Caps + velocity limits; no yield |
| Anti-speculation | No swaps, pools, bridges, APY, mining rewards |
| Offline utility | Local ledger + mesh gossip + delayed witness quorum |
| Bounded risk | Double-spend exposure accepted within caps, not unlimited |
| Honest preview | Experimental UX; no profit expectation |

---

## Terminology map (public)

| Avoid (crypto culture) | Prefer (utility framing) |
|------------------------|----------------------------|
| Token | Local credit |
| Wallet | Balance vault / emergency credits |
| Blockchain | Settlement ledger |
| Transaction | Local settlement event |
| Asset | Local value unit |
| Coin (user-facing) | Credit / emergency credit |
| Mining / staking | *(none)* |
| Market / price | *(none)* |

Internal code may retain `MXC` / `muon` as technical units during migration.

---

## Comparable systems (intent, not legal equivalence)

- **Prepaid transit balances** — capped, non-transferable utility, no appreciation
- **Arcade / venue credits** — spend-only within a venue ecosystem
- **Local disaster vouchers** — time- and place-bounded relief commerce
- **Temporary ration or relief IOUs** — community-scoped, reconciled later
- **Barter ledger aids** — record-keeping for offline trade, not global money

---

## What we explicitly do not build

- DEX / swap APIs
- Liquidity pools
- External token bridges
- Staking, APY, interest, mining rewards
- Price charts, trading UI, “to the moon” messaging
- Uncapped balances or whale wallets
- Global perfect consensus as a financial network

---

## Beta-safe defaults (code)

See `crates/mycena-coin/src/settlement_policy.rs`:

- `DEFAULT_MAX_BALANCE_MXC` = 100  
- `HARD_MAX_BALANCE_MXC` = 300  
- `DEFAULT_MAX_TX_MXC` = 25  
- Velocity: 20 events/hour, 50 MXC outbound/hour  

---

## Related documents

- [REGULATORY_DESIGN_NOTES.md](./REGULATORY_DESIGN_NOTES.md)
- [OFFLINE_SETTLEMENT_MODEL.md](./OFFLINE_SETTLEMENT_MODEL.md)
- [MESHCOIN_ARCHITECTURE_REVIEW.md](./MESHCOIN_ARCHITECTURE_REVIEW.md)
- [EMERGENCY_SETTLEMENT_RESEARCH.md](./EMERGENCY_SETTLEMENT_RESEARCH.md)
- [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) — SD-002, SD-011, SD-160, SD-161
