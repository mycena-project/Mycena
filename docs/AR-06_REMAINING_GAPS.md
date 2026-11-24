# AR-06 scaling — remaining gaps

**Status:** Mitigations shipped (2026-07); P0–P1 software gaps closed 2026-07-05.

## Shipped

- Geohash gossip topic sharding (`mycena-core::topic_shard`)
- Kademlia query rate limit (8/min) in `Libp2pTransport`
- Auto crowd density backoff (≥25 visible peers → Intermittent forwarding, fanout 2, mDNS cap, peer cap ~20)
- `mycena-sim::dense_cluster` — 520-node fanout containment model
- **`MeshProfile::Crowd`** for manual crowd mode at node start
- **`crowd_density_active`** in `NodeMetrics` + Android Peers + Desktop Settings
- **Geohash prefix UX** — Settings + FFI `set_local_geohash_prefix`
- **Wi‑Fi Direct inbound** — `WifiDirectBearerHost` TCP reader → `ingestBleFrame`
- **Gossip mesh hot-swap (P0)** — density backoff applies `MeshProfile::Crowd` gossip params at runtime ([`gossip_mesh.rs`](../crates/mycena-node/src/gossip_mesh.rs), vendored libp2p-gossipsub)
- **Multi-shard SOS listen (P1)** — parent geohash prefixes via `listen_shard_prefixes` (env `MYCENA_SOS_LISTEN_MIN_PREFIX`, default 3)
- **Bulletin geohash post (P1)** — `post_bulletin(..., geohash?)` + `resolve_publish_topics_with_hint`
- **Crisis drop metrics (P1)** — `crisis_dropped_background_*` in NodeMetrics

## Remaining

| Gap | Category | Notes |
|-----|----------|-------|
| **Full 520-node libp2p soak** | 2 — infra | Sim uses analytical model; optional nightly job |
| **GPS-derived geohash** | 1 — product | Manual prefix UX shipped; auto-location is a separate decision |

See [WORK_REMAINING.md](./WORK_REMAINING.md).
