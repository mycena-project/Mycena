# Connecting two Mycelium devices

## Design principles

1. **Parallel paths, not either/or** — When the internet is available, Mycelium still runs **all** discovery layers: same Wi‑Fi (mDNS), public relay bootstrap, and optional relay rendezvous. Internet does **not** disable local mesh. See [CONNECTIVITY_MODES.md](./CONNECTIVITY_MODES.md) for the full mode matrix.
2. **Local first** — On the same Wi‑Fi, devices should connect **without** QR or relay (automatic mDNS dial).
3. **QR for trust + remote** — One QR scan (or pasted invite) establishes a **relay circuit** and an **accepted contact** for chat. Default QR is **signed v3** (account id + transport peer id + enc key + epoch). v1/v2 still parse for legacy codes.
4. **Relay discovery is optional** — Rendezvous helps two devices on different networks find each other **when both are online on the relay** and both opted in. It is not a global phone book.

## What users should do

| Situation | What to do |
|-----------|------------|
| Same Wi‑Fi (home, office) | Open the app on both; wait ~10–30s. Check **Connect → Connected devices**. |
| Different networks | **Connect → Scan** the other device’s QR (or paste invite link). |
| Still no link on different networks | Enable **Settings → Relay discovery (remote)** on **both** devices; wait ~45s. |
| Chat | After connect, open **Chat** — connected peers appear automatically (same source as Connect). |

## Infrastructure (developer)

| Layer | Default | Role |
|-------|---------|------|
| Bootstrap relay | On | Always dials `mycelium-relay.fly.dev`; NAT traversal via `/p2p-circuit`. |
| mDNS | On (LAN) | Discovers peers on LAN; **dials** discovered multiaddrs. |
| Rendezvous | **On** for new installs | Register on relay + **bucketed** selective dial of opted-in peers (~45s tick, ≤3/tick). Turn off in Settings for privacy. |
| Crowd mode | Off | Festivals / dense LAN: cap mDNS dials, dense gossip, lower peer cap (~20). Enable in Settings (desktop + Android); restart for full transport limits. |
| BLE | Off (use **Maximum connectivity** on Android; explicit **Bluetooth mesh** on macOS preview) | **F1–F3 (Android):** GATT invite, WireMessage fallback, libp2p `/ble/` overlay. **Desktop:** Phase 1 stub — [ADR 003](./adr/003-desktop-ble-and-disaster-mesh.md). |

## Invite format

```
mycelium://invite/v3#<accountId>|<peerId>|<encPubkeyHex>|<epoch>|<sigHex>[|<directMultiaddr>]
                                                                   (default QR on desktop + Android)
mycelium://invite/v1#<peerId>                                      (legacy; still parsed)
mycelium://invite/v2#<peerId>|<encPubkeyHex>[|<directMultiaddr>]   (legacy; still parsed)
```

E2E encryption uses the peer’s X25519 key from **`PeerInfo`** on connect (v1). v2 seeds the key immediately from the QR (trust-on-first-use). **v3** binds account id, transport peer id, enc key, and epoch with an Ed25519 signature — malformed or tampered invites are rejected at parse time.

**Identity rotation (SD-190):** each device has a stable **account id** and a **transport PeerId** that rotates on every app cold start (default) and/or every 24h. Contacts store `account_id` and follow **E2E encrypted** signed re-announce — not public gossip. See [TRANSPORT_IDENTITY.md](./TRANSPORT_IDENTITY.md).

Dial target (relay): `/ip4/<relay-ip>/tcp/4001/p2p/<relay>/p2p-circuit/p2p/<peerId>` (see `bootstrap.rs`)

## UI map

- **Connect** tab (was “Network” / “Peers”): scan QR, show QR, paste invite, connected list.
- **SOS** tab: post/list emergency geohash messages; opt-in listen (default off). MeshAid linked for coordination threads.
- **Settings**: relay discovery toggle, **Use internet** (immediate apply), **crowd mode** (festivals), **Bluetooth mesh (macOS preview)**, custom bootstrap (advanced). See [CONNECTIVITY_MODES.md](./CONNECTIVITY_MODES.md).
- **Chat**: contacts and messages only. Sidebar uses connected peers (`get_chat_contacts`); onboarding does **not** wipe local history — use **Settings → PANIC WIPE** for a fresh device.

## Known limits

- Rendezvous only lists peers **currently connected** to the relay and opted in.
- Hotspot / **guest Wi‑Fi** often blocks device-to-device traffic and mDNS — use QR + relay instead.
- **Local mesh mode** activates automatically when the public relay is unreachable; the app prioritizes LAN (`mDNS`) dials. Same-subnet Wi‑Fi still required for zero-QR discovery.
- Desktop has no camera; phone scans Mac’s QR or Mac pastes phone’s invite.
- **Bluetooth** — **F1–F3 (Android):** see [ADR 002](./adr/002-ble-mesh-transport.md). **Desktop:** BLE host stub only until Phase 1b ([ADR 003](./adr/003-desktop-ble-and-disaster-mesh.md)). Disaster-scale multi-hop: [DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md).
- E2E keys learned from peers are **persisted on disk** after connect; chat works across app restarts without re-scanning (Phase B2).
- **Multi-relay pool** (Phase D): clients hash-pick a bootstrap relay for circuits and rotate on disconnect; add regional relays via `bootstrap.txt` or `MYCELIUM_BOOTSTRAP_PEERS`.
- **Festival scale** is not 100k-ready; crowd mode + dense gossip + rendezvous bucketing reduce dial/gossip storms in dense clusters.

## After code changes

Connection fixes live in the **Rust node** (`mycelium-ffi`). You must rebuild **both** clients:

- **Android:** `make android-deploy` (Docker build) — reinstall; do not reuse an old APK.
- **Desktop:** `make desktop-dev` and restart the app.

Check relay: `curl -s https://mycelium-relay.fly.dev/` — when your app is running you should eventually see `connections` ≥ 1 on the relay while the app is open.
