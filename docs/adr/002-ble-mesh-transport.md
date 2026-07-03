# ADR 002: Bluetooth as a mesh transport layer

**Status:** Accepted (F0–F2 Android shipped; F3 Android shipped)  
**Date:** 2026-07-03  
**Context:** Users want **maximum connectivity** — all discovery and transport paths active so the mesh works when Wi‑Fi or internet fail. Today BLE is **opt-in discovery only** (Android); libp2p traffic uses TCP/QUIC on LAN and relay circuits. Real-world scenarios (protests, festivals, guest Wi‑Fi blocking mDNS, no shared router) can make **BLE the only link** between two phones meters apart.

---

## Decision (phased)

We will treat Bluetooth as **three layers**, rolled out in order. Chat/mail/bulletin/gossip always use the **same Rust node**; hosts add paths, not duplicate app logic.

| Phase | Name | What ships | Carries libp2p? |
|-------|------|------------|-----------------|
| **F0** | Maximum connectivity preset | Settings toggle: internet + rendezvous + BLE discovery **on** | No (existing paths only) |
| **F1** | BLE → mesh handoff (Level 0 **B3**) | GATT exchange of signed invite v3; scanner calls `connect_invite` | Indirect (dials relay/LAN after BLE) |
| **F2** | BLE data bridge (DTN side-channel) | GATT/L2CAP carries signed `Envelope` / gossip frames when no TCP path | Parallel to libp2p; node ingests same pipeline |
| **F3** | libp2p-native BLE transport | Custom `Transport` in swarm (Noise + Yamux over BLE); multiaddr `/ble/...` | Yes |

**F3 is optional long-term.** F1 + F2 deliver most user value before we commit to libp2p BLE maintenance (no upstream crate; see [libp2p PR #971](https://github.com/libp2p/rust-libp2p/pull/971)).

---

## Why not flip BLE into libp2p immediately?

1. **rust-libp2p has no maintained Bluetooth transport** — experimental RFCOMM work was never merged.
2. **BLE throughput and MTU** — GATT ~512 B/transaction; fine for invites and DTN, poor for bulk gossip without framing and batching.
3. **Platform APIs differ** — Android GATT server + scanner; iOS Core Bluetooth; desktop BlueZ — ADR 001 keeps first implementation in **host code** until F3 justifies Rust abstraction (`PlatformBleTransport` FFI callbacks or `btleplug` on Linux desktop).
4. **Dual-role complexity** — simultaneous central + peripheral, connection collision (see lessons from [iroh-ble-transport](https://github.com/mcginty/iroh-ble-transport), [FIPS BLE](https://github.com/k0sti/fips)).

---

## Architecture (target)

```
┌─────────────────────────────────────────────────────────────┐
│  App (chat / mail / bulletin) — unchanged                    │
├─────────────────────────────────────────────────────────────┤
│  mycelium-node (libp2p swarm)                                │
│    TCP / QUIC / mDNS / relay / kad                           │
│    + optional BleSideChannelIngest (F2)                      │
├─────────────────────────────────────────────────────────────┤
│  Host layer (Android / iOS / desktop)                        │
│    F0: advertise + scan (today)                              │
│    F1: GATT invite read → connect_invite()                   │
│    F2: GATT/L2CAP framed WireMessage / Envelope              │
└─────────────────────────────────────────────────────────────┘
```

**Parallel paths, not either/or** (same principle as [CONNECT.md](../CONNECT.md)): when internet works, still run LAN + BLE; when Wi‑Fi is hostile, BLE may be the only way to **learn** a dial target or **carry** small payloads.

---

## F1 — BLE handoff (shipped Android)

**Advertiser (peripheral):**

- Connectable BLE + GATT service `6e400001-…` (existing UUID)
- Characteristic `invite` — read-only, signed v3 invite URI (rotated with transport epoch)
- No peer id in manufacturer data (privacy; [PRIVACY_EXPERIMENTS.md](../PRIVACY_EXPERIMENTS.md) E0.3)

**Scanner (central):**

- On `FIRST_MATCH`, GATT connect → read invite → `mycelium.connectInvite(uri)`
- Dedupe by account id; skip if already connected
- Fallback: notification-only if GATT fails (current behavior)

**Does not require Wi‑Fi.** Dial target is relay circuit from invite; if both devices later share LAN, mDNS upgrades the path.

---

## F2 — BLE data bridge (shipped Android)

When libp2p has **no connected path** to a peer but BLE GATT link exists:

- Frame: `length | noise-handshake? | signed Envelope bytes` (reuse existing crypto)
- Size cap per message (e.g. 4 KiB); large payloads still prefer TCP
- Node exposes `NodeCommand::IngestBleFrame(Vec<u8>)` — same ingest guard as gossip

Use cases: chat line, SOS, proximity match intent when guest Wi‑Fi blocks mDNS and relay is down (airplane mode + BLE only).

---

## F3 — libp2p BLE overlay (shipped Android)

BLE links register as libp2p peers with `/ble/<device>/gatt/<uuid>/p2p/<peer-id>` multiaddrs. The node runs the same CBOR direct request/response protocol as TCP, framed as `MYLP` stream bytes over GATT (chunked with `MYLC`). When a BLE link is active, `send_direct` and encrypted chat use the overlay instead of the F2 side-channel queue.

- **Rust:** `ble_multiaddr`, `ble_stream`, `BleTransportRegistry` integrated into `Libp2pTransport`
- **FFI:** `register_ble_link`, `ingest_ble_stream_frame`, `drain_ble_stream_outbound`, `ble_peer_addrs`
- **Android:** handoff calls `registerBleLink`; relay drains F3 then F2 queues
- **Fallback:** F2 `MYBL` WireMessage frames when no F3 link exists

Full Noise+Yamux inside the libp2p swarm over BLE remains optional future work if gossip-over-BLE is required at scale.

---

## Settings / UX

| Setting | F0 behavior |
|---------|-------------|
| **Maximum connectivity** | Enables internet, rendezvous, BLE discovery; does **not** disable privacy toggles without user consent |
| **Use internet** | Master off → local mesh only (existing) |
| **Relay discovery** | Requires internet |
| **Bluetooth** | F0: discovery + notify; F1+: auto-handoff |

Status strings:

- “Bluetooth: discovery” (F0)
- “Bluetooth: linked” (F1, GATT handoff done)
- “Bluetooth: carrying messages” (F2 fallback)
- “Bluetooth: libp2p linked” (F3, `/ble/` peer visible)

---

## Risks

| Risk | Mitigation |
|------|------------|
| BLE fingerprinting / tracking | No stable IDs in adv; rotating invite; low-power scan duty cycle |
| Spam over BLE | Signed invites; ingest guard; rate limit GATT connects |
| Battery | Low-power adv/scan; stop scan when ≥ N peers connected |
| iOS background | Core Bluetooth rules; F1 may require foreground or “Uses Bluetooth LE accessories” |
| Desktop | F1+ optional BlueZ (Linux); macOS Core Bluetooth in Tauri helper |

---

## Success criteria

| Phase | Exit test |
|-------|-----------|
| F0 | One toggle enables all current paths; documented in CONNECT.md |
| F1 | Two Android phones, **no Wi‑Fi**, BLE only → Connect tab shows peer within 60s after opt-in |
| F2 | Same setup → encrypted chat line delivered over BLE with relay and Wi‑Fi disabled |
| F3 | libp2p peer list includes `/ble/` addrs; direct chat over BLE overlay without TCP |

---

## References

- [LEVEL0_ROADMAP.md](../LEVEL0_ROADMAP.md) Phase B3 (handoff)
- [CONNECT.md](../CONNECT.md) parallel paths
- [DISCOVERY.md](../DISCOVERY.md)
- [MOBILE_PLATFORMS.md](../MOBILE_PLATFORMS.md) — BLE host-side F1–F3 on Android
