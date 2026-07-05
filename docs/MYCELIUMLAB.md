# MyceliumLab

Spatial mesh simulation environment built on `mycelium-sim` for regression testing delivery, latency, memory, and battery overhead across scenario presets.

## CLI

```bash
cargo run -p mycelium-sim --bin myceliumlab -- list
cargo run -p mycelium-sim --bin myceliumlab -- run --preset city_walk --seed 42 --nodes 200 --ci-mode
cargo run -p mycelium-sim --bin myceliumlab -- run --preset dense_cluster --nodes 10000 --csv lab_results.csv
cargo run -p mycelium-sim --bin myceliumlab -- ci-gate --csv lab_ci.csv
```

## Presets (Round 2 + extensions)

| Preset | German alias | Description | Default scale |
|--------|--------------|-------------|---------------|
| `city_walk` | `stadtspaziergang` | Lévy-flight pedestrians, relay + Wi‑Fi + BLE | 100–1000 nodes |
| `stadium` | `stadion` | Static multi-cluster, dense BLE/Wi‑Fi, no relay | 10 000+ nodes |
| `earthquake` | `erdbeben` | **Sudden internet total outage + 30% node loss** at 30% sim time | 100–1000 nodes |
| `network_split` | `netzwerk-split` | City splits into two clusters (6h full / 6min CI), then merge | 100–1000 nodes |
| `dense_cluster` | `dichter-cluster` | **Single tight cluster**, grid link discovery at 10k+ | 10 000+ nodes (CI: 1500–3000) |
| `sybil_swarm` | `sybil-schwarm` | **50% adversarial peers** (700‰ drop rate), relay + mesh | 100–1000 nodes |

### Round 2 coverage

| Requirement | Preset | Notes |
|-------------|--------|-------|
| Stadtspaziergang | `city_walk` | Implemented |
| Stadion | `stadium` | 3 static clusters, 10k default |
| Erdbeben (Internet weg + 30% Knoten weg) | `earthquake` | `InternetOutage` + `RemoveNodesFraction(0.30)` at same timestamp |
| Netzwerk-Split | `network_split` | Geographic partition + merge |
| 10 000+ dichter Cluster | `dense_cluster` | Single 100 m world, grid O(n) links |
| 50% Sybil / böswillig | `sybil_swarm` | Adversaries **active in CI** (unlike other presets) |

Related scenarios outside MyceliumLab (also CI-gated):

| Module | Gate |
|--------|------|
| `custody_scenarios` | City DTN delivery must succeed (80 nodes) |
| `commuter_custody_scenarios` | Delivery-probability routing ≤ latency vs recency-only |
| `eclipse_scenarios` | Diversity preserves legit relay in 100% of rounds |
| `dense_cluster` (analytical) | Gossip shard fanout containment at 520 nodes |

## Models

- **Mobility:** Random Waypoint, Lévy Flight, Static Cluster (jitter)
- **Radio:** BLE (~28–45 m), Wi‑Fi (~70–180 m), relay (internet path)
- **Battery:** idle + bearer-specific joule accounting per tick
- **Adversaries:** configurable fraction with per-peer drop rate (per mille); Sybil preset keeps 50% in CI
- **Churn:** random online/offline toggles per hour

## Output (CSV)

Columns: `timestamp_ms`, `git_commit`, `scenario`, `seed`, `nodes`, `duration_ms`, `messages_attempted`, `delivery_rate`, `avg_latency_ms`, `p95_latency_ms`, `ble_joules`, `wifi_joules`, `relay_joules`, `idle_joules`, `memory_bytes`, `delivered`, `dropped_loss`, `dropped_offline`.

Append with `--csv path` for commit-to-commit regression charts.

## CI gates

GitHub Actions (`.github/workflows/ci.yml`):

```bash
cargo test -p mycelium-sim sim_ci_gates -- --test-threads=1
```

Also via CLI: `myceliumlab ci-gate`

### MyceliumLab minimum delivery (seed=42, `ci_mode`, ideal links)

| Preset | Minimum delivery | CI node count (`--nodes 0`) |
|--------|------------------|----------------------------|
| `city_walk` | **85%** | 100 |
| `stadium` | **70%** | 500 |
| `earthquake` | **50%** | 100 |
| `network_split` | **60%** | 100 |
| `dense_cluster` | **55%** | 1500 |
| `sybil_swarm` | **35%** | 100 (50% adversaries active) |

### Custody / trust / bearer gates

| Gate | Criterion |
|------|-----------|
| **TransportScore** | Internet → Wi‑Fi → BLE → custody fallback; lab relay/BLE mapping; Wi‑Fi wins after observed bulk transfers |
| Custody city delivery | Message delivered via `CustodyQueue` (80-node city sim) |
| Commuter custody | Delivery-probability delivers in ≤ steps and ≤ latency vs recency-only |
| Eclipse relay | Score-only picks 0 legit peers; diversity picks legit in every round |
| Local dense Sybil | 200 legit + 500 co-located fakes; diverse legit→legit delivery **≥55%** (score-only must stay **≥10pp** lower) |
| SOS flood | 120 Sybil peers × 8 SOS each; crisis **off** until **≥3** trusted independent sources nearby |
| Dense cluster shards | Shard fanout ratio < 0.15 at 520 nodes; density backoff triggers |

**Custody cold start:** When no `PeerTrust` map is present and scoring is `RecencyOnly`, carrier spray uses stable alphabetical order (DTN bootstrap). Production custody with co-encounter history uses `DeliveryProbabilityAndRecency` by default.

### Nightly (10k dense cluster)

GitHub Actions (`.github/workflows/myceliumlab-nightly.yml`, 03:00 UTC):

```bash
cargo test -p mycelium-sim dense_cluster_10k_smoke -- --ignored --nocapture
```

PR CI caps `dense_cluster` at 1500–3000 nodes for runtime; nightly validates the full 10k scenario.

### Local-only smoke (not in default PR CI)

```bash
cargo test -p mycelium-sim dense_cluster_10k_smoke -- --ignored
# or:
cargo test -p mycelium-sim dense_cluster_10k_smoke -- --nocapture
```

The `dense_cluster_10k_smoke` test runs 10 000 nodes (~30–60s locally).

## Implementation

- Core: `crates/mycelium-sim/src/lab/`
- CI thresholds: `crates/mycelium-sim/src/lab/gates.rs`
- CI orchestration: `crates/mycelium-sim/src/sim_ci_gates.rs`
- Lab bearer scoring: `crates/mycelium-sim/src/lab/transport_score.rs` (uses `TransportScoreStore` like production)
- Transport: existing `SimulationRunner` + spatial link rebuild each tick
- Messages: direct delivery to in-range peers only (honest single-hop metric)
- Stadium / dense_cluster at 10k+ nodes uses grid-accelerated link discovery; ≤2500 nodes use pairwise O(n²)
