# Mycelium discovery (transport layer)

Three complementary ways to find **other devices** on the network (not the same as **contacts** for chat).

## Layers

| Layer | UI | Meaning |
|-------|-----|---------|
| Transport | **Peers / Network** tab | libp2p links (LAN, relay, manual multiaddr) |
| Social | **Chat** tab | Accepted contacts; messages from unknown peers → pending request |

## LAN (Phase C)

- **mDNS** on the same Wi‑Fi (Android uses a Wi‑Fi multicast lock).
- **BLE bootstrap** (optional, Settings): connectable GATT + signed invite v3; scanner auto-dials via `connect_invite` (F1). When libp2p has no path, **F2** carries signed `WireMessage` frames over GATT mesh-data characteristic.

## Internet relay

- Default bootstrap: `mycelium-relay.fly.dev`.
- Nodes listen on `/p2p-circuit` at the relay for inbound NAT traversal.
- **Rendezvous (opt-in):** Settings → *Relay discovery*
  - **Off (default):** you are not listed on `GET /rendezvous`; you do not auto-dial rendezvous peers.
  - **On:** `POST /rendezvous/register` publishes your peer ID; every ~45s the node fetches the list and dials other opted-in peers.

## Manual linking

- QR / invite: `mycelium://invite/v1#<PeerID>` (short; dialled via relay circuit).
- Multiaddr paste on Peers tab.
- Adding a peer from QR also creates an **accepted contact** when the payload is a peer ID.

## Privacy notes

- Rendezvous on the public relay is an **alpha** feature: opted-in users on the same relay can see each other's peer IDs.
- Chat content is only delivered to **accepted** contacts; unknown senders create a **pending** request.
