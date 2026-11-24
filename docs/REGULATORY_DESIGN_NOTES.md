# Regulatory Design Notes — MeshCoin Emergency Settlement

**Disclaimer:** This document is engineering-oriented design intent, **not legal advice**. Counsel must review before public claims or jurisdictional launch.

---

## Positioning statement (defensive)

Mycena MeshCoin is designed as:

1. **A technology preview** for emergency offline commerce continuity  
2. **A non-investment utility** with no expectation of profit  
3. **A bounded-value system** with protocol-enforced balance and velocity caps  
4. **A local/mesh-scoped settlement aid**, not a global payment network  
5. **Not legal tender** and not marketed as currency replacement  

---

## Features that reduce regulatory surface

| Control | Rationale |
|---------|-----------|
| Hard balance ceiling | Discourages wealth storage; aligns with prepaid/utility models |
| Per-transfer cap | Limits single-event economic exposure |
| Velocity limits | Reduces structuring/laundering utility |
| No yield / appreciation mechanics | Weakens “investment contract” framing |
| No secondary market primitives | No exchange = no securities-exchange analog |
| Experimental disclaimers | Sets user expectation; no profit promise |
| Preview faucet only | No open mint; remove before production (SD-161) |
| Hot-wallet spending cache | Limits on-device exposure (not cold storage marketing) |

---

## Red flags to avoid in marketing

- “Invest,” “returns,” “yield,” “APY,” “moon,” “HODL”
- Price charts, market cap, trading pairs
- “Decentralized bank” or “be your own bank”
- Uncapped “wallet” for savings
- Global remittance narratives
- Anonymity-for-payments messaging

---

## Red flags in current implementation (mitigate)

| Risk | Status | Mitigation |
|------|--------|------------|
| Witness quorum gameable (Sybil) | Open (SD-011) | Preview-only; reputation-weighted witnesses (roadmap) |
| Local mint / faucet | Open (SD-161) | Remove or gate `testnet` feature |
| Balance gossip metadata | Open (SD-002) | Rate limit; encrypt scope |
| Hot keys on device | Open (SD-160) | Cap + cold split + disclaimers |
| Crypto terminology in UI | Partially fixed | Continue string migration |
| Payment QR phishing | Open (mini-app AS-07) | Native confirmation dialogs |

---

## Jurisdiction-agnostic comparisons (for counsel packets)

**Prepaid transit:** User loads capped balance; spends with operator; no investment expectation.  
**Closed-loop vouchers:** Redeemable only in defined ecosystem.  
**Disaster relief IOUs:** Community issuance with later reconciliation; not traded on exchanges.  
**Loyalty points:** Non-transferable or bounded transfer; no cash equivalence promised.

MeshCoin aims closer to **prepaid emergency utility credits on a mesh** than to **convertible virtual currency**.

---

## AML attractiveness — deliberate reductions

- Low caps → low value density per identity  
- Velocity limits → burst laundering harder  
- No exchange → no off-ramp in protocol  
- Local gossip → incomplete global graph for layering  
- Optional future: community-scoped issuance, trust-radius limits  

**Remaining exposure:** P2P transfers still resemble value transfer; caps reduce impact, not classification.

---

## Recommended public copy (template)

> Emergency local credits for offline resilience. Not money. Not an investment. Balances are capped. For neighborhood commerce during outages and disasters — not for savings or speculation.

---

## Pre-launch legal checklist

- [ ] Counsel review of terms + privacy + store listing  
- [ ] Confirm no profit-expectation language  
- [ ] Remove or gate testnet faucet  
- [ ] Document witness model limitations in user-facing FAQ  
- [ ] Age/geography restrictions if required  
- [ ] Incident process for scam QR / fake payment requests  

See also [ABUSE_RESPONSE_PLAYBOOK.md](./ABUSE_RESPONSE_PLAYBOOK.md).
