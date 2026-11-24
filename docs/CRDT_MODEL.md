# CRDT merge model (Mycena mesh)

**Status:** Implemented (core types + local storage integration)  
**Last updated:** 2026-07-05

Mycena is offline-first and partition-tolerant. [`consistency.rs`](../crates/mycena-core/src/consistency.rs) tags operations by guarantee; this document specifies **how** eventually-consistent state converges.

Implementation: [`crates/mycena-core/src/crdt/`](../crates/mycena-core/src/crdt/)

---

## Why Hybrid Logical Clocks (HLC)?

Pure wall-clock LWW fails when devices lack NTP (common on ad-hoc mesh without internet). Two phones can both believe they are “newer” and fork contact names or status.

**HLC** combines:

1. Physical time (`wall_ms`) — coarse ordering when clocks are roughly aligned  
2. Logical counter — disambiguates events on the same millisecond  
3. Actor hash — stable tie-break per device  

Each local write calls `HlcClock::tick(now_ms())`. On merge, `observe(remote_ts)` advances the local clock so future writes stay causally ordered.

---

## Contact book — LWW-Register per field

| Field | CRDT | Notes |
|-------|------|-------|
| `display_name` | `LwwRegister<String>` | Higher HLC wins |
| `status` | `LwwRegister<ContactStatusWire>` | pending / accepted / blocked |
| `added_at_ms` | `LwwRegister<u64>` | First-seen metadata |
| `account_id` | `LwwRegister<Option<String>>` | SD-190 account binding |
| `previous_peer_ids` | `OrSet<String>` | Transport rotation history |

Wire type: [`ContactDocument`](../crates/mycena-core/src/crdt/contact.rs).  
App storage merges via `AppStorage::merge_contact_document` and assigns HLC on every upsert.

**Not used:** single global LWW on the whole contact object (would drop concurrent field edits).

---

## Group membership — OR-Set

| Field | CRDT | Notes |
|-------|------|-------|
| `members` | `OrSet<String>` | Add generates unique tag; remove observes all live tags |
| `name` | `LwwRegister<String>` | HLC LWW |
| `key`, `id` | Immutable | Invite identity; merge rejected if mismatch |

OR-Set avoids 2P-Set “remove forever” problems and supports re-adding a member after removal. **Tag compaction** (`compact_tombstones`) drops remove markers once no add map references them — tombstones do not grow without bound after sync.

---

## Marketplace stock — PN-Counter (not LWW)

### Problem with naive LWW on quantity

Two sellers each have **1 unit** of the same listing. Offline, both record a sale locally (`qty → 0`). With LWW, whichever device syncs last “wins” and the network silently believes **0 sold** or **1 sold** — the double-sale is hidden.

### PN-Counter model

Each actor records:

- **Positive** increments (restock, initial inventory)  
- **Negative** increments (sales, reservations fulfilled)

Merge takes **max** per actor per sign (standard PN-Counter). Global value:

```text
on_hand = sum(positive) − sum(negative)
```

| Merged value | Behavior |
|--------------|----------|
| `≥ 0` | `StockQuantity::Available(n)` — normal UI |
| `< 0` | `StockQuantity::Conflict` — **visible resolver required** |

Conflict payload includes per-actor positive/negative maps and `oversold_by` so MeshMarket (or operator UI) can show:

> “Two offline sales exceeded stock — resolve manually.”

### Explicit resolution only

`StockResolution::SetOnHand { quantity, actor, at_ms }` re-bases the counter after human review. **There is no automatic LWW fallback.**

This is intentional: marketplace inventory is a **conservation** constraint (units cannot be created by merge), unlike display names where last write is acceptable.

---

## What does not auto-merge

| Data | Strategy |
|------|----------|
| Chat message bodies | Append-only / dedupe by id (separate from this module) |
| Coin / voucher balances | SettlementRequired — witness quorum, not CRDT |
| Marketplace stock conflicts | PN-Counter + manual `StockResolution` |
| Strong global order | Forbidden on mesh (`Consistency::Strong`) |

---

## Storage keys (app layer)

| Tree | Content |
|------|---------|
| `contacts:data` | `Contact` + embedded `ContactDocument` |
| `groups:data` | `Group` + embedded `GroupDocument` |
| `marketplace:stock` | `MarketplaceStock` PN-Counter state |
| `meta:crdt` | `actor_id`, persisted `HlcClock` |

---

## Mesh sync (wired)

| CRDT | Transport | Scope / body tag | Op constant |
|------|-----------|------------------|-------------|
| `ContactDocument` | Direct (pairwise) | `[contact:crdt]` | `OP_CONTACT_UPSERT` |
| `GroupDocument` | Gossip | `mycena/group-meta/{id}` · `[crdt:group:{id}]` | `OP_GROUP_MEMBERSHIP` |
| `MarketplaceStock` | Gossip | `mycena/marketplace/stock/v1` · `[crdt:stock:{listing_id}]` | `OP_MARKETPLACE_STOCK` |

**Contacts:** After signed `ContactHello`, each peer pushes their contact-book entry for the other party (`merge_contact_document` on receive). Docs about `local_peer_id` are ignored.

**Groups:** `record_group_peer_seen` publishes the merged `GroupDocument` after OR-Set updates (group chat ingress + local sends).

**Marketplace:** MeshMarket calls `window.mycena.marketplaceStock.*` (bridge → `AppStorage` PN-Counter). Every init/sale/restock/resolve gossips the full counter; receive path calls `merge_marketplace_stock` and surfaces `StockQuantity::Conflict` in the UI.

Bridge methods: `marketplace.stock.get|init|record_sale|record_restock|resolve` (requires mini-app `Storage` permission).

---

## Related

- [ADR 006](./adr/006-mesh-consistency-model.md) (consistency tags)  
- [`BEARER_TRANSPORT.md`](./BEARER_TRANSPORT.md) (sync transport)  
- MeshMarket miniapp: PN-Counter wired via bridge + gossip (listing metadata still in scoped KV + bulletins)
