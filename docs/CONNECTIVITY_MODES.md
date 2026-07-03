# Connectivity modes

**Status:** Active  
**Audience:** Users, support, and developers wiring Settings UI.

Mycelium runs **parallel paths** — LAN, internet relay, rendezvous, and (on Android) BLE — not a single on/off switch. Settings control which paths are **allowed**; runtime status shows which paths are **working right now**.

See also: [CONNECT.md](./CONNECT.md), [DISCOVERY.md](./DISCOVERY.md), [DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md), [ADR 002](./adr/002-ble-mesh-transport.md), [ADR 003](./adr/003-desktop-ble-and-disaster-mesh.md).

---

## Settings vs runtime status

| Layer | Setting | Applies | What it does |
|-------|---------|---------|--------------|
| **Internet** | `Use internet (relay + remote peers)` | Immediately (desktop + Android) | Enables bootstrap relay dial, NAT traversal, remote circuits. Off → **local mesh only** (LAN mDNS; no rendezvous). |
| **Rendezvous** | `Relay discovery (opt-in)` | Immediately | Registers on public relay + bucketed dial of other opted-in peers (~45s tick, ≤3/tick). Requires internet on. |
| **Maximum connectivity** | Preset toggle | Immediately | Turns internet + rendezvous on. On Android also enables BLE mesh (F1–F3). On macOS enables BLE preference + host stub (Phase 1). |
| **Crowd mode** | Festival / dense events | Kademlia policy immediately; full transport caps on **restart** | Caps mDNS fanout, dense gossip, lower peer target (~20). |
| **BLE bootstrap** | Android: part of max connectivity; macOS: separate preview toggle | Immediately (preference); transport when host active | Android F1–F3. Desktop stub logs intent until btleplug GATT ships (ADR 003). |
| **Display name, energy, bootstrap peers** | Save / advanced | On save (desktop) | Identity and custom relay list — not connectivity hot path. |

**Status bar labels** (desktop + Android):

| Label | Meaning |
|-------|---------|
| **Internet + mesh** | Internet enabled and public relay reachable — LAN + bootstrap + optional rendezvous. |
| **Local mesh (internet off)** | User turned off internet in Settings — LAN only by choice. |
| **Local mesh (relay offline)** | Internet enabled in Settings but bootstrap relay unreachable — LAN only until relay returns. |

The subtitle (`connectivity_detail`) explains *why* the label was chosen.

---

## Mode matrix (reasonable defaults)

These are the **product decisions** for how modes combine. Implementation should not invent new semantics without updating this doc.

| User intent | Internet | Rendezvous | Max connectivity | BLE | Expected behavior |
|-------------|----------|------------|------------------|-----|-------------------|
| **Normal (default)** | On | On (new installs) | Off | Off | LAN auto-connect; relay for NAT/remote; rendezvous helps rediscover contacts when both online. |
| **Privacy / LAN party** | Off | Off (forced) | Off | Off | Same Wi‑Fi only; no relay registration; QR still works on LAN for trust. |
| **Maximum reach** | On | On | On | On (Android) | All paths; BLE handoff + side-channel on phones. |
| **Festival / protest dense** | On | User choice | Off | User choice | Enable **crowd mode**; rendezvous optional (privacy vs discoverability tradeoff). |
| **Disaster / no infrastructure** | Off | Off | On (Android) | On | Phones discover via BLE (F1); carry traffic when TCP absent (F2/F3). Desktop: LAN only until Phase 1b BLE. |
| **Mac at home, phone elsewhere** | Mac off → phone cannot rendezvous **to** Mac | — | — | — | Contact book durable; paths need Mac internet on, same LAN, or phone-side BLE mesh to another hop. |

**Decision:** Internet off is a **hard opt-out** — rendezvous and remote bootstrap stop immediately. This matches user expectation (“no internet”) and avoids leaking registration to relay.

**Decision:** Rendezvous is **opt-in** but default-on for new installs — remote rediscovery without re-scanning QR, with bucketed dial to limit storms (see Phase D).

**Decision:** `max_relay_fanout = 3` is **relay candidate count per tick**, not end-to-end mesh hops. Default envelope `max_hops = 8` limits gossip relay depth; city-wide hundreds of hops is **not** a current goal ([DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md)).

---

## Discovery scenarios

| Scenario | Works today? | Path |
|----------|--------------|------|
| Two devices, same Wi‑Fi, internet up | Yes | mDNS → direct TCP |
| Two devices, same Wi‑Fi, internet down | Yes | mDNS → direct TCP |
| Mac + Android, different cities, both internet + rendezvous | Yes | QR once → contact; rendezvous redial when both online |
| Mac internet **off**, Android on relay | **No** remote discovery to Mac | Mac LAN-only; need same Wi‑Fi or Mac internet on |
| Two Android, no Wi‑Fi/cell, BLE range | Yes (F1–F3) | BLE invite / MYBL / BLE libp2p overlay |
| Mac + Android, no Wi‑Fi, BLE only | Yes (F1–F3 desktop + Android) | BLE invite handoff; transport only until QR for chat |
| Contact added via QR, peer offline | Yes (durable contact) | Chat queues; deliver when path returns |

---

## Platform parity

| Feature | Android | Desktop (macOS) |
|---------|---------|-----------------|
| Internet toggle immediate apply | Yes | Yes (Phase 1 UX) |
| Rendezvous immediate apply | Yes | Yes |
| Status detail string | Partial | Yes (`connectivity_detail`) |
| BLE F1–F3 | Shipped | Shipped (macOS/Linux blew host) |
| QR scan | Yes | Paste / show QR |

---

## Developer notes

- Desktop emits `settings-updated` and `metrics-updated` with `internet_enabled`, `rendezvous_enabled`, `connectivity_mode`, `connectivity_detail`.
- `ConnectivityMode` in Rust (`Internet` vs `MeshOnly`) reflects **relay reachability**, not the user's internet toggle alone.
- Custom bootstrap peers: one per line in Settings; empty = default `mycelium-relay.fly.dev`.
