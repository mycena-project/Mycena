# MyceliumLab

Spatial mesh simulation environment built on `mycelium-sim` for regression testing delivery, latency, memory, and battery overhead across scenario presets.

## CLI

```bash
cargo run -p mycelium-sim --bin myceliumlab -- list
cargo run -p mycelium-sim --bin myceliumlab -- run --preset city_walk --seed 42 --nodes 200 --ci-mode
cargo run -p mycelium-sim --bin myceliumlab -- run --preset stadium --nodes 10000 --csv lab_results.csv
cargo run -p mycelium-sim --bin myceliumlab -- ci-gate --csv lab_ci.csv
```

## Presets

| Preset | Description | Default scale |
|--------|-------------|---------------|
| `city_walk` | Lévy-flight pedestrians, relay + Wi‑Fi + BLE | 100–1000 nodes |
| `stadium` | Static clusters, dense BLE/Wi‑Fi, no relay | 10 000+ nodes |
| `earthquake` | Internet outage + 30% node loss at 30% sim time | 100–1000 nodes |
| `network_split` | City splits into two clusters (6h full / 6min CI), then merge | 100–1000 nodes |

German aliases: `stadtspaziergang`, `stadion`, `erdbeben`, `netzwerk-split`.

## Models

- **Mobility:** Random Waypoint, Lévy Flight, Static Cluster (jitter)
- **Radio:** BLE (~35–45 m), Wi‑Fi (~120–180 m), relay (internet path)
- **Battery:** idle + bearer-specific joule accounting per tick
- **Adversaries:** configurable fraction with per-peer drop rate (per mille)
- **Churn:** random online/offline toggles per hour

## Output (CSV)

Columns: `timestamp_ms`, `git_commit`, `scenario`, `seed`, `nodes`, `duration_ms`, `messages_attempted`, `delivery_rate`, `avg_latency_ms`, `p95_latency_ms`, `ble_joules`, `wifi_joules`, `relay_joules`, `idle_joules`, `memory_bytes`, `delivered`, `dropped_loss`, `dropped_offline`.

Append with `--csv path` for commit-to-commit regression charts.

## CI gates

`myceliumlab ci-gate` (and `cargo test -p mycelium-sim myceliumlab_ci_gates`) enforce minimum delivery rates (seed=42, shortened CI durations):

| Preset | Minimum delivery |
|--------|------------------|
| city_walk | 85% |
| stadium | 70% |
| earthquake | 50% |
| network_split | 60% |

## Implementation

- Core: `crates/mycelium-sim/src/lab/`
- Transport: existing `SimulationRunner` + spatial link rebuild each tick
- Messages: direct delivery to in-range peers only (honest single-hop metric)
- Stadium at 10k+ nodes uses grid-accelerated link discovery; ≤2500 nodes use pairwise O(n²)
