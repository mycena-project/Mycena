# SD-070 — Privacy shaping benchmark

**Status:** Experimental, **off by default**. Use these numbers for a cost/benefit decision before enabling traffic shaping for all users.

**Implementation:** `crates/mycelium-core/src/privacy_shaping.rs`  
**Node integration:** `crates/mycelium-node/src/node.rs` (batch queue, padding on forward, scope bucketing on `ScopeAnnounce`, cover traffic gated by `energy.rs`)

---

## Mechanisms

| Feature | Behavior |
|---------|----------|
| **Padding** | `Envelope::payload` padded to 256 B / 1 KiB / 4 KiB / 16 KiB buckets (trailer `MPAD` + original length) |
| **Batching** | Non-`High` priority direct sends held in `BatchingScheduler`; flush at random interval in `[batch_min_ms, batch_max_ms]` |
| **Scope bucketing (SD-004)** | `ScopeAnnounce` lists parent wildcards (e.g. `mycelium/bulletin/berlin/*`) instead of exact leaf scopes |
| **Cover traffic** | Signed dummy `DirectMessage` with body `[shade:cover:v1]`; dropped silently on ingest; interval scaled by energy state |

### Profiles (batch window)

| Profile | `batch_min_ms` | `batch_max_ms` |
|---------|----------------|----------------|
| Fast | 500 | 2 000 |
| Balanced (default) | 2 000 | 8 000 |
| Paranoid | 5 000 | 30 000 |

### Energy gating (`energy.rs`)

| `NodeState` | Cover traffic |
|-------------|---------------|
| Active | Base interval (default 60 s) |
| Intermittent | 2× interval |
| Passive | Disabled |

---

## Measured overhead (debug build, macOS arm64, 2026-07-04)

Reproduce:

```bash
cargo test -p mycelium-core privacy_shaping_benchmark -- --nocapture
```

### CPU (pad + unpad)

| Metric | Value |
|--------|-------|
| Operations | 40 000 round-trips (10 000 × sizes 64, 180, 512, 2048 B) |
| Throughput | ~29 000 ops/s |
| CPU impact | Negligible vs radio TX; dominated by network stack |

### Bandwidth (padding only)

Synthetic mix (equal count per size class):

| Metric | Value |
|--------|-------|
| Original bytes | 28 040 000 |
| Padded bytes | 56 320 000 |
| **Aggregate overhead** | **~101%** (roughly doubles wire payload for this mix) |

Per-size intuition:

| Typical payload | Bucket | Overhead |
|-----------------|--------|----------|
| ~64 B (chat meta) | 256 B | ~4× |
| ~180 B | 256 B | ~42% |
| ~512 B | 1 KiB | ~2× |
| ~2 KiB | 4 KiB | ~2× |
| >16 KiB | none | 0% (no pad) |

**Typical chat (~180 B inner payload):** ~42% padding overhead (estimate API uses this for UI).

### Latency (batching)

| Profile | Added delivery delay |
|---------|----------------------|
| Fast | 0.5–2 s (uniform random) |
| Balanced | 2–8 s |
| Paranoid | 5–30 s |

`Priority::High` messages bypass the batch queue.

### Cover traffic (256 B bucket, 60 s interval, padding on)

| Energy state | Bytes/hour (approx.) |
|--------------|----------------------|
| Active | ~15 KiB/h |
| Intermittent | ~7.5 KiB/h |
| Passive | 0 |

Battery: each cover cell costs one radio TX; at 60 s intervals ≈ 60 extra TX/h on Active — meaningful on BLE-heavy setups, modest on Wi‑Fi.

---

## Recommendation

| Goal | Suggested setting |
|------|-------------------|
| **Default (beta)** | Off |
| **Privacy-curious, Wi‑Fi** | Enabled, Balanced, cover off |
| **Strong timing resistance** | Paranoid + padding; accept 5–30 s latency |
| **Battery-sensitive / BLE mesh** | Avoid cover traffic; use Passive energy mode |

---

## Android UI

Settings → Network privacy → **Traffic shaping (experimental)** with live `battery_impact_summary` from `privacy_shaping_overhead_estimate()`.

---

## Gaps / follow-ups

- Gossip `publish_scoped` payloads not padded yet (direct wire only)
- No daily byte budget cap (see PRIVACY_EXPERIMENTS §3.15)
- Cover cells use identifiable body marker on peers running older builds (ingested as normal low-priority traffic)
- Bloom sync fingerprint normalization (SD-001) still separate
