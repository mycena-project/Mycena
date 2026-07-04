# Custody DTN — Store-and-Forward City Routing

Messages can reach a destination **hours later** via physical carriers even when sender and receiver are **never online together**.

---

## Components

| Module | Role |
|--------|------|
| `encounter_log` | sled-backed record of peer meetings (start/end, duration, recency) |
| `custody` | Separate custody queue — **not** the live gossip epidemic queue |
| `Envelope::custody` | Marks DTN messages; uses extended `max_hops` (255) + 48h TTL |
| `Envelope::last_custody_from` | Prevents immediate custody ping-pong on handoff |

---

## Spray-and-wait copy budget

Unlike [`ProbabilisticForwardingPolicy`](../crates/mycelium-node/src/forwarding.rs) (probabilistic fanout per hop), custody uses **bounded network copies**:

- `max_custody_spray` default **3** — max duplicate copies the **origin** releases into the mesh
- Origin **sprays** on encounter; after spray budget, origin **transfers** like a carrier
- Carriers **transfer** custody (handoff) using `hop_count` / `max_hops`; spray budget does not limit relay hops
- `last_custody_from` blocks immediate return to the previous holder

Live epidemic forwarding is **skipped** for `envelope.custody == true`.

---

## TTL / hop binding

Custody queue respects existing envelope fields:

- `ttl_secs` + `created_at_ms` — entry dropped when expired
- `hop_count` / `max_hops` — no further custody forward when exhausted

Directed messages without live path are **enqueued** to custody (when `custody_enabled`).

---

## Encounter-driven scheduling

On `PeerUp`:

1. `EncounterLogStore::begin_encounter`
2. `CustodyQueue::plans_for_encounter` — push up to 2 custody copies to new neighbor

Periodic `forward_once` tick also runs `forward_custody_once` using encounter-ranked connected peers.

---

## Residual limits (honest)

- No global copy ledger — spray budget is **envelope-local** (best-effort Sybil bound)
- Encounter log is **device-local** (privacy: 7-day TTL prune)
- City-scale delivery requires sufficient carrier density — see sim scenario

---

## Simulation

```bash
cargo test -p mycelium-sim city_custody
```

`custody_scenarios::city_custody_delivery` — 50–200 nodes, grid-scattered carriers with random walk, fixed sender/receiver corners, BLE-range links, churn; asserts delivery with **zero** direct sender↔receiver radio links.

---

## Related

- [DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md) Phase 3
- [OFFLINE_SETTLEMENT_MODEL.md](./OFFLINE_SETTLEMENT_MODEL.md)
