# MeshCoin / emergency credits — UI copy audit

**Reference:** [MESHCOIN_POSITIONING.md](./MESHCOIN_POSITIONING.md)  
**Principle:** Utility credits for offline mesh services — **not** crypto asset framing.

---

## Applied in this pass (English `values/strings.xml`)

| Key | Before | After |
|-----|--------|-------|
| `nav_wallet` | Wallet | Emergency credits |
| `more_subtitle` | …wallet… | …network contribution… |
| `more_wallet_sub` | mesh coins | Offline utility credits… |
| `more_emergency_credits_sub` | *(new)* | Spend credits on mesh utility… |
| `settings_open_wallet` | Open wallet | Open emergency credits |
| `miniapp_grant_payments_title` | Wallet access? | Emergency credits access? |
| `miniapp_payment_confirm_message` | …muon… | …credits… |
| `fresh_identity_*` / `settings_panic_*` | wallet data | credit ledger data |
| `wallet_cold_wallet_*` | cold wallet | main credit store |
| `wallet_hot_wallet` | Spending cache (hot) | Spending cache |
| `network_contribution_*` | *(new)* | Network contribution card |

German (`values-de/strings.xml`) — **still recommended:**

| Key | Current issue | Suggested |
|-----|---------------|-----------|
| `wallet_experimental_notice` | "MeshCoin experimentell" | Match EN utility notice |
| `wallet_title` | Geldbeutel | Notfall-Credits |
| `nav_wallet` | Geldbörse | Notfall-Credits |
| `wallet_hot_wallet` | Hot Wallet | Ausgabe-Cache |
| `miniapp_payment_confirm_message` | Muon | Credits |

---

## Remaining strings to reframe (all locales)

| Key | Issue | Utility alternative |
|-----|-------|---------------------|
| `wallet_scan_mxcpay` | mxcpay brand | Scan payment request QR |
| `wallet_faucet` | "preview credits" OK | Keep or "Request test credits" |
| `wallet_history` | Transaction history | Settlement history |
| `wallet_tx_sent` | Transaction broadcast | Settlement sent |
| `wallet_fee` | Network fee | Service fee |
| `wallet_receive_title` | Your address | Your settlement address |
| Internal code `walletAddress()` | OK internally | UI: "Settlement address" (already) |

---

## Onboarding / navigation tone

**Prefer leading with:**

1. **Network contribution** (More tab card) — relays, uptime, tier
2. **Emergency credits** — secondary row, utility subtitle
3. **Peers screen** — show PeerTrust reputation, optional endorse action (future)

**Avoid:**

- Balance-first home screens
- "Wallet" as primary nav label
- muon/MXC in user-visible amounts (use "credits" + optional MXC in parens)
- Hot/cold wallet crypto jargon → "spending cache" / "main credit store"

---

## Mini-app bridge copy

Bridge methods may retain `walletConfirmedMuon` internally; user-facing confirm dialogs should say **credits**, not muon.

---

## Checklist for new UI strings

- [ ] Does it sound like an investment or trading product? → rewrite
- [ ] Is "coin/token/wallet" user-visible? → prefer credit / settlement / utility
- [ ] Is balance the hero metric? → prefer contribution tier or peer trust
