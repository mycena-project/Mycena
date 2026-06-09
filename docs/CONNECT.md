# Connecting two Mycelium devices

## Design principles

1. **Parallel paths, not either/or** — When the internet is available, Mycelium still runs **all** discovery layers: same Wi‑Fi (mDNS), public relay bootstrap, and optional relay rendezvous. Internet does **not** disable local mesh.
2. **Local first** — On the same Wi‑Fi, devices should connect **without** QR or relay (automatic mDNS dial).
3. **QR for trust + remote** — One QR scan (or pasted `mycelium://invite/v1#…`) establishes a **relay circuit** and (on peer-ID invites) an **accepted contact** for chat.
4. **Relay discovery is optional** — Rendezvous helps two devices on different networks find each other **when both are online on the relay** and both opted in. It is not a global phone book.

## What users should do

| Situation | What to do |
|-----------|------------|
| Same Wi‑Fi (home, office) | Open the app on both; wait ~10–30s. Check **Connect → Connected devices**. |
| Different networks | **Connect → Scan** the other device’s QR (or paste invite link). |
| Still no link on different networks | Enable **Settings → Relay discovery (remote)** on **both** devices; wait ~45s. |
| Chat | After connect, use **Chat** (peer-ID QR also adds an accepted contact). |

## Infrastructure (developer)

| Layer | Default | Role |
|-------|---------|------|
| Bootstrap relay | On | Always dials `mycelium-relay.fly.dev`; NAT traversal via `/p2p-circuit`. |
| mDNS | On (LAN) | Discovers peers on LAN; **dials** discovered multiaddrs. |
| Rendezvous | **On** for new installs | Register on relay + dial other opted-in peers (~45s tick). Turn off in Settings for privacy. |
| BLE | Off | Proximity notification only — does **not** auto-connect peers today. |

## Invite format

```
mycelium://invite/v1#<peerId>
```

Dial target: `/dns4/mycelium-relay.fly.dev/tcp/4001/p2p/<relay>/p2p-circuit/p2p/<peerId>`

## UI map

- **Connect** tab (was “Network” / “Peers”): scan QR, show QR, paste invite, connected list.
- **Settings**: relay discovery toggle, custom bootstrap (advanced).
- **Chat**: contacts and messages only.

## Known limits

- Rendezvous only lists peers **currently connected** to the relay and opted in.
- Hotspot / guest Wi‑Fi may block mDNS; use QR.
- Desktop has no camera; phone scans Mac’s QR or Mac pastes phone’s invite.
- **Bluetooth** does not pair devices yet (Wi‑Fi / relay / QR only).

## After code changes

Connection fixes live in the **Rust node** (`mycelium-ffi`). You must rebuild **both** clients:

- **Android:** `make android-deploy` (Docker build) — reinstall; do not reuse an old APK.
- **Desktop:** `make desktop-dev` and restart the app.

Check relay: `curl -s https://mycelium-relay.fly.dev/` — when your app is running you should eventually see `connections` ≥ 1 on the relay while the app is open.
