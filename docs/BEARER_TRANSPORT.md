# Bearer transport (unified framing)

**Status:** Implemented (BLE); template for Wi‑Fi Direct / LoRa  
**Last updated:** 2026-07-04

ADR 002 F2 (`MYBL`) and F3 (`MYLP`) previously duplicated inner framing and node outbound queues. Both now use [`bearer_transport`](../crates/mycelium-core/src/bearer_transport.rs).

## Trait

```rust
pub trait BearerTransport {
    fn mtu(&self) -> BearerMtu;
    fn reliability_class(&self) -> ReliabilityClass;
    fn send_frame(&mut self, peer: &str, frame: &[u8]) -> anyhow::Result<()>;
    fn recv_frame(&mut self, peer: &str) -> anyhow::Result<Option<Vec<u8>>>;
    fn drain_outbound(&mut self, peer: &str, max: usize) -> Vec<Vec<u8>>;
}
```

| `ReliabilityClass` | Use |
|--------------------|-----|
| `BestEffort` | F2 side-channel wire messages |
| `Acknowledged` | F3 libp2p direct CBOR request/response |

## Frame formats

| Magic | Role |
|-------|------|
| `MYBR` v1 | Unified envelope (`version`, `kind`, `flags`, `len`, `payload`) |
| `MYBL` | Legacy F2 (still default on encode) |
| `MYLP` | Legacy F3 (still default on encode) |
| `MYLC` | GATT chunk wrapper (unchanged) |

Set `MYCELIUM_BEARER_WIRE_V1=1` to emit `MYBR` on the wire (requires peer support).

## BLE + Wi‑Fi Direct implementation

[`BearerMeshRouter`](../crates/mycelium-node/src/bearer_mesh.rs) selects the live bearer via [`TransportScoreStore`](../crates/mycelium-core/src/transport_score.rs) (rolling latency, loss, throughput, energy) and [`decide_transport_path`](../crates/mycelium-core/src/transport_score.rs): **Internet relay → Wi‑Fi Direct → BLE trickle → custody queue**. The legacy byte threshold (`MYCELIUM_BEARER_WIFI_UPGRADE_BYTES`, default 16 KiB) is only a fallback when scores are cold. [`BearerBleRouter`](../crates/mycelium-node/src/bearer_ble.rs) provides unified ingest (`decode_ingest`).

**Host API:** `drain_bearer_outbound` returns `medium` (`0`=BLE, `1`=Wi‑Fi Direct). See [ADR 007](./adr/007-wifi-direct-bulk-bearer.md).

MyceliumLab uses the same scoring via [`lab/transport_score`](../crates/mycelium-sim/src/lab/transport_score.rs) (CI-gated in `sim_ci_gates`).

Related: [ADR 002](./adr/002-ble-mesh-transport.md)
