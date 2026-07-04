# AR-06 scaling — remaining gaps

**Status:** Mitigations shipped (2026-07); items below are follow-ups for full product intent.

## Shipped

- Geohash gossip topic sharding (`mycelium-core::topic_shard`)
- Kademlia query rate limit (8/min) in `Libp2pTransport`
- Auto crowd density backoff (≥25 visible peers → Intermittent forwarding, fanout 2, mDNS cap, peer cap ~20)
- `mycelium-sim::dense_cluster` — 520-node fanout containment model

## Remaining (priority)

| Gap | Impact | Suggested fix |
|-----|--------|---------------|
| **No mobile geohash UX** | SOS listen requires `MYCELIUM_LOCAL_GEOHASH` or `NodeConfig.local_geohash_prefix`; Settings/FFI not wired | FFI field + Android SOS/settings geohash prefix |
| **Gossip mesh degree not hot-swapped** | Density backoff affects forwarding/mDNS/peers only; gossipsub `mesh_n` set at transport init | Restart with crowd mode, or future gossipsub reconfig |
| **`MeshProfile::Crowd` unused** | Defined but manual crowd mode still uses `Dense` | Wire crowd mode → `Crowd` profile on restart |
| **Bulletin sharding partial** | Only SOS + `mycelium/bulletin/*` with geohash in JSON | Optional geohash on bulletin post API |
| **Multi-shard SOS listen** | Single prefix only | Subscribe parent prefixes or user-selected radius |
| **No density observability** | Users cannot see auto-backoff in UI | Expose `crowd_density_active` in metrics/Settings |
| **Wi‑Fi Direct inbound (Android)** | Bulk send works; TCP read → `ingest_bearer_frame` missing | Inbound relay loop in `WifiDirectBearerHost` |
| **Full 520-node libp2p soak** | Sim uses analytical model (CI-time tradeoff) | Optional nightly soak job |

See also: [ARCHITECTURE_RISKS.md](./ARCHITECTURE_RISKS.md) §6, [CONNECTIVITY_MODES.md](./CONNECTIVITY_MODES.md).
