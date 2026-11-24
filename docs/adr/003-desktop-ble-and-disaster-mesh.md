# ADR 003: Desktop BLE host and disaster mesh path

**Status:** Accepted (Phase 1a: UX + stub; Phase 1b: blew host shipped on macOS/Linux)  
**Date:** 2026-07-03  
**Context:** Android ships BLE F1–F3 ([ADR 002](./002-ble-mesh-transport.md)). Desktop users expect the same Settings semantics (immediate apply, honest status) and eventually BLE mesh for laptop ↔ phone scenarios in outages. Disaster-use documentation must not overclaim laptop mesh before hardware path exists.

---

## Decision

1. **Settings parity first** — Desktop network toggles apply immediately and emit `settings-updated`, matching Android. Status shows `connectivity_detail` explaining relay vs user opt-out.

2. **Single Rust node, host BLE bridge** — Same pattern as ADR 001: `mycena-node` owns libp2p + ingest; desktop host implements GATT scan/advertise and calls existing FFI:
   - `register_ble_link(peer, mac)`
   - `ingest_ble_frame(bytes)`
   - `drain_ble_outbound()` / `drain_ble_stream_outbound()`

3. **Phase 1a (shipped):** `DesktopBleHost` stub in `mycena-desktop/src/ble/mod.rs` — logs when `ble_bootstrap` is enabled; no radio activity.

4. **Phase 1b (shipped):** [`blew`](https://github.com/mcginty/blew) on macOS and Linux (primary + secondary):
   - GATT service UUIDs **match Android** (`6e400001`, invite `6e400002`, mesh data `6e400003`)
   - F1: peripheral advertises + invite characteristic; central scans, reads → `register_transport_from_invite` (transport only; QR/paste uses `connect_from_invite` for contacts)
   - F2/F3: MYLC chunk ingest on peripheral writes; relay loop drains Rust outbound queues

   **Note:** ADR originally named `btleplug`, but btleplug is central-only (no GATT server). `blew` provides both central and peripheral on Apple/Linux in one process.

5. **Disaster mesh scope** — Desktop BLE is **one hop class**, not city-wide routing. Multi-hop DTN and disaster profile are Phase 3+ ([DISASTER_MESH_ROADMAP.md](../DISASTER_MESH_ROADMAP.md)).

---

## Why blew (not btleplug-only)

| Option | Pros | Cons |
|--------|------|------|
| **blew in-process** | Central + peripheral; Tokio-native; macOS CoreBluetooth | No Windows; younger crate |
| **btleplug** | Mature central API | **No GATT server** — cannot advertise invite |
| **Platform Swift (macOS native)** | Best macOS integration | Splits host code; slower to ship F1 parity |

**Choice:** `blew` inside `mycena-desktop/src/ble/` for Phase 1b.

---

## macOS permissions (Phase 1b)

- `NSBluetoothAlwaysUsageDescription` in `Info.plist` (see `crates/mycena-desktop/Info.plist`)
- User must grant Bluetooth in System Settings
- On denial: setting stays saved; status detail notes “Bluetooth permission denied”

---

## Module layout

```
crates/mycena-desktop/src/ble/
  mod.rs           — DesktopBleHost spawn/stop
  gatt.rs          — (1b) service/characteristic setup
  scanner.rs       — (1b) central role, F1 invite read
  peripheral.rs    — (1b) advertiser + GATT server
  bridge.rs        — (1b) drain outbound → GATT write; inbound → ingest_ble_frame
```

Host holds `NodeHandle` clone; all node mutations via existing `NodeCommand` channel (thread-safe).

---

## Settings mapping

| Setting | Desktop behavior |
|---------|------------------|
| `max_connectivity` | Internet + rendezvous on; does **not** auto-enable BLE (unlike Android) until user opts in — avoids surprise radio on laptops |
| `ble_bootstrap` | Enables `DesktopBleHost`; when 1b lands, starts scan/advertise |
| `internet_enabled` off | BLE may still run (disaster pocket mesh with phone); no relay |

**Reasonable decision:** BLE on desktop is **explicit opt-in** (`Bluetooth mesh (macOS preview)`), not bundled silently into max connectivity — laptops are often on wired LAN and Bluetooth permission is intrusive.

---

## Alternatives considered

- **Skip desktop BLE entirely** — Rejected; NGOs and field laptops need parity docs and a clear stub until hardware ships.
- **Immediate btleplug before UX** — Rejected; users already confused by save-only toggles; ADR 003 splits 1a/1b.

---

## Consequences

- Positive: Honest disaster roadmap; Android remains primary mesh device today.
- Negative: Mac ↔ Android BLE-only scenarios blocked until Phase 1b.
- Testing: Phase 1b requires physical Mac + Android; CI stays unit tests on framing (`ble_mesh.rs`, chunker tests).

---

## References

- [ADR 001 — Mobile host architecture](./001-mobile-host-architecture.md)
- [ADR 002 — BLE mesh transport](./002-ble-mesh-transport.md)
- [CONNECTIVITY_MODES.md](../CONNECTIVITY_MODES.md)
- [DISASTER_MESH_ROADMAP.md](../DISASTER_MESH_ROADMAP.md)
