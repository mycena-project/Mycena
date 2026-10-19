# Mycelium discovery (transport layer)

Three complementary ways to find **other devices** on the network (not the same as **contacts** for chat).

## Layers

| Layer | UI | Meaning |
|-------|-----|---------|
| Transport | **Peers / Network** tab | libp2p links (LAN, relay, manual multiaddr) |
| Social | **Chat** tab | Accepted contacts; messages from unknown peers → pending request |

## LAN (Phase C)

- **mDNS** on the same Wi‑Fi (Android uses a Wi‑Fi multicast lock).
- **BLE bootstrap** (optional, Settings): connectable GATT + signed invite v3; scanner auto-dials via `connect_transport_invite` (F1 transport handoff — **no chat contact**). When libp2p has no path, **F2** carries signed `WireMessage` frames over GATT mesh-data characteristic.

## Internet relay

- Default bootstrap: `relay.example.invalid` (libp2p TCP/UDP 4001; health and rendezvous at `http://relay.example.invalid:8080`).
- Nodes listen on `/p2p-circuit` at the relay for inbound NAT traversal.
- **Rendezvous (opt-in):** Settings → *Relay discovery*
  - **Off (default):** you are not listed on `GET /rendezvous`; you do not auto-dial rendezvous peers.
  - **On:** the node POSTs a **signed** `RendezvousProof` (libp2p key, 5-minute timestamp window) to `/rendezvous/register`; every ~45s it fetches `GET /rendezvous` and dials other opted-in peers. Unsigned `{peer_id}` bodies are rejected.

## Manual linking

- QR / invite: `mycelium://invite/v1#<PeerID>` (short; dialled via relay circuit). Adds an **accepted contact** for chat.
- Multiaddr paste on Peers tab (transport dial only).
- BLE mesh handoff (F1): reads the same invite over GATT but only opens a **transport path** — does not add a chat contact.

## Privacy notes

- Rendezvous on the public relay is an **alpha** feature: opted-in users on the same relay can see each other's peer IDs.
- Chat content is only delivered to **accepted** contacts; unknown senders create a **pending** request.
