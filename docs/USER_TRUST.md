# UserTrust — network contribution (non-financial)

**Status:** Implemented (local-only)  
**Crate:** `mycena-coin/src/user_trust.rs`  
**Related:** `mycena-node/src/peer_trust.rs`, `mycena-coin/src/spam_caution.rs`

---

## Purpose

**UserTrust** tracks how much a device contributes to the mesh — relay forwards, uptime, successful deliveries, and local peer endorsements. It is **intentionally separate** from MXC ledger balance:

| Property | UserTrust | MXC balance |
|----------|-----------|-------------|
| Stored on ledger | **No** | Yes |
| Transferable / tradable | **No** | Limited (settlement) |
| Attack surface | Social gaming of endorsements | Double-spend, hoarding |
| Primary use | UI reputation, relay boost, spam caution | Utility payments |

---

## PeerTrust vs UserTrust (keep both)

| | **PeerTrust** (`mycena-node`) | **UserTrust** (`mycena-coin`) |
|---|---|---|
| Subject | Remote peers (behavior observed) | Self + local endorsements of others |
| Persistence | `peer_trust/` sled tree | `user_trust/` sled tree |
| Signals | Strikes, relay success, spam score | Relay count, uptime, deliveries, endorsements |
| Relay routing | `PeerTrust::relay_selection_score()` | `endorsement_boost_map()` (+0.15 weight) |

**Why separate:** Network reliability (Sybil relays, spam) and payment trust (settlement, vouchers) have different adversaries. Combining them would let financial stake override behavioral evidence or vice versa.

Relay selection in `select_relay_candidates()` combines both:

```
score = peer_trust.relay_selection_score(now)
      + 0.15 * local_endorsement_boost   // 0..1 from 1–5 star endorsements
```

---

## Anti-spam caution (utility credits)

`spam_caution::deposit_for_message_priority()` **burns** settled MXC credits to raise message priority. This is framed as a **caution deposit**, not an investment:

- Default high-priority tier: ~0.02 MXC (`DEFAULT_PRIORITY_DEPOSIT_MUON`)
- Credits are destroyed on use (anti-spam economic cost)
- FFI: `send_chat_priority(to, body, deposit_muon)`

**Note:** Encrypted chat carries `priority` on `WireMessage::EncryptedDirect`. High-priority wire frames use bounded relay fanout (same as envelope path) instead of flooding all peers.

---

## Android / FFI surface

| API | Description |
|-----|-------------|
| `user_trust_summary()` | Tier, score, relay/delivery/uptime/endorsement counts |
| `endorse_peer(peer_id, strength)` | Local 1–5 endorsement; syncs to node relay scoring |
| `send_chat_priority(...)` | Burn + send with priority caution |

Background task in `mycena-ffi` syncs node metrics → UserTrustStore every 30s and ticks uptime every 60s.

---

## Contribution tiers

| Score | Tier |
|-------|------|
| 0–15 | Newcomer |
| 16–40 | Helper |
| 41–80 | Reliable |
| 81+ | Anchor |

Score formula (log-scaled): relay forwards, uptime hours (cap 1 week), deliveries, endorsement count.
