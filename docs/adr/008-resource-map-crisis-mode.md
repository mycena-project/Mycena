# ADR 008: Resource map payload class and Crisis Mode

**Status:** Accepted  
**Date:** 2026-07-04  
**Context:** Disaster mesh work needs explicit **Crisis Mode** (pause non-essential sync, prioritize SOS/emergency forwarding) and a first-stage **offline resource map** (water, food, shelter, medicine, power as geohash-tagged entries). Resource pins resemble SOS bulletins but have different freshness, abuse, and UI semantics than chat or coin gossip.

See also: [ADR 006](./006-mesh-consistency-model.md), [DISASTER_MESH_ROADMAP.md](../DISASTER_MESH_ROADMAP.md), [`crisis_mode.rs`](../../crates/mycena-core/src/crisis_mode.rs), [`resource_map.rs`](../../crates/mycena-core/src/resource_map.rs).

---

## Decision

### 1. Crisis Mode (runtime policy)

- **Activation:** manual (`ForceOn` / `ForceOff` / `FollowAuto`) or auto when internet is unreachable, user listens for SOS/resource map, and nearby SOS activity is observed (geohash prefix match).
- **When active:**
  - Skip bloom anti-entropy (`SyncBloom`) and periodic peer sync ticks.
  - Drop inbound **background** gossip scopes (coin, app store) at ingest.
  - Extend [`ForwardingPolicy`](../../crates/mycena-node/src/forwarding.rs) with [`TrafficClass`](../../crates/mycena-node/src/forwarding.rs): Background held, Essential gets higher fanout; essential traffic bypasses energy gating in crisis.
  - Shorter forward tick, larger forward budget, higher emergency `max_hops`, no cover traffic, essential messages skip privacy batching.
- **Disaster mesh profile** ([`MeshProfile::Disaster`](../../crates/mycena-core/src/mesh_profile.rs)) pairs with crisis tuning but remains a separate transport preset.

### 2. Resource map as its own payload class

Resource map entries are **not** normal chat/bulletin gossip:

| Aspect | Normal bulletin gossip | Resource map |
|--------|------------------------|--------------|
| Consistency | EventuallyConsistent | **Local** + MaximumPropagation |
| State op | `OP_GOSSIP_BULLETIN_PUBLISH` | **`OP_RESOURCE_MAP_BROADCAST`** |
| Scope | arbitrary subscribed scope | `mycena/resource-map/v1` |
| Payload | `AppMessage` / encrypted bulletin | JSON [`ResourceMapEntry`](../../crates/mycena-core/src/resource_map.rs) |
| Freshness | bulletin `expires_at_ms` | per-entry **`ttl_secs`** + local expiry filter |
| Semantics | durable thread / forum | ephemeral disaster pin; stale = harmful |

**Why separate from SOS?** Same transport priority and ingest rate limits, but categories, TTL policy, and future volunteer-coordination merges differ. Separate scope + op keeps ADR 006 classification honest and allows independent evolution (e.g. category-specific caps).

**Why not reuse generic bulletin?** Generic gossip assumes mergeable, eventually consistent fanout. Resource pins are **locally composed**, **TTL-bound**, and **propagation-capped** like SOS — treating them as EventuallyConsistent would invite UI to show unverified pins as authoritative inventory.

### 3. Mesh consistency (ADR 006 alignment)

- **`OP_RESOURCE_MAP_BROADCAST`** → `LocalModel` (same class as SOS).
- Compose + sign locally; mesh applies **MaximumPropagation** hop/fanout caps in crisis/disaster profiles.
- Receivers merge into local bulletin store but **filter expired** entries before display/API.
- No settlement, no Strong consistency, no cross-device ordering guarantee.

### 4. Phase 1 scope (this ADR)

**In scope:**

- Core types, crisis runtime params, node forwarding/ingest hooks.
- App/FFI: `post_resource_map`, `list_resource_map_entries`, crisis manual/auto APIs.
- Resource map subscribed together with SOS listen toggle.

**Out of scope (later):**

- ~~Volunteer task assignment~~ — phase 2 shipped (`mycena/volunteer/v1`, 2026-07-05)
- Dedup by place+time, moderation queues.
- Map UI / offline tiles; category-specific trust weights.
- Automatic resource-map-driven crisis activation (SOS-only for auto today).

---

## Consequences

- Clients must not render resource pins without TTL check.
- Background sync pauses in crisis — mini-app/coin state may be stale until crisis ends; acceptable trade-off.
- Future volunteer coordination should add new ops/scopes rather than overloading `ResourceMapEntry`.

---

## Rollout

1. Core + node (this change).
2. Android/Desktop crisis toggle + resource report UI.
3. Field drill metrics: crisis_active, essential forward rate, background drop count.
