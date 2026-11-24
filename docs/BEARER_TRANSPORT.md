# Bearer transport (unified framing)

**Status:** Implemented (BLE + Wi‑Fi Direct bulk + Wi‑Fi Aware discovery + WebRTC data channel)  
**Last updated:** 2026-07-05

ADR 002 F2 (`MYBL`) and F3 (`MYLP`) previously duplicated inner framing and node outbound queues. Both now use [`bearer_transport`](../crates/mycena-core/src/bearer_transport.rs).

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

Set `MYCENA_BEARER_WIRE_V1=1` to emit `MYBR` on the wire (requires peer support).

## BLE + Wi‑Fi Direct + Wi‑Fi Aware

[`BearerMeshRouter`](../crates/mycena-node/src/bearer_mesh.rs) selects the live **data** bearer via [`TransportScoreStore`](../crates/mycena-core/src/transport_score.rs) and [`decide_transport_path`](../crates/mycena-core/src/transport_score.rs): **WebRTC (when ready) → Internet relay → Wi‑Fi Direct → BLE trickle → custody queue**.

[`BearerWebRtcRouter`](../crates/mycena-node/src/bearer_webrtc.rs) queues MYBR frames for **RTCDataChannel** hosts (`BearerMedium::WebRtc`, `medium=3`). SDP/ICE signaling rides [`WireMessage::WebRtcSignal`](../crates/mycena-core/src/transport.rs) over libp2p relay — see [ADR 010](./adr/010-webrtc-data-channel-bearer.md).

[`BearerWifiAwareRouter`](../crates/mycena-node/src/bearer_wifi_aware.rs) implements [`BearerTransport`](../crates/mycena-core/src/bearer_transport.rs) for **discovery only** (`BearerMedium::WifiAware`, `carries_mesh_data = false`). Peer presence uses [`decide_discovery_bearer`](../crates/mycena-core/src/transport_score.rs). See [ADR 009](./adr/009-wifi-aware-discovery-bearer.md).

**Host API:** `drain_bearer_outbound` returns `medium` (`0`=BLE, `1`=Wi‑Fi Direct, `3`=WebRTC). Discovery announcements use `queue_discovery_announcement` / `drain_discovery_announcements`. WebRTC signaling: `post_webrtc_signal`, `drain_webrtc_signaling_inbound`, `drain_webrtc_negotiation_requests`. See [ADR 007](./adr/007-wifi-direct-bulk-bearer.md).

MycenaLab uses the same scoring via [`lab/transport_score`](../crates/mycena-sim/src/lab/transport_score.rs) (CI-gated in `sim_ci_gates`).

Related: [ADR 002](./adr/002-ble-mesh-transport.md)
