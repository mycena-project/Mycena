# Transport identity rotation (SD-190)

**Status:** Implemented  
**Last updated:** 2026-07-04

Stable **account identity** (contacts, payments, signed invites) is separate from rotatable **transport PeerId** (libp2p routing, LAN/relay observability).

## Keys

| Key | Vault secret | Purpose |
|-----|--------------|---------|
| Account Ed25519 | `account_ed25519` | `account_id`, invite v3 signatures, rotation signatures |
| Transport Ed25519 | `ed25519_identity` | libp2p PeerId, envelope mesh signatures |
| E2E X25519 | `enc_x25519` | Chat ratchet (unchanged across transport rotation) |

## When transport rotates

On node bootstrap (`bootstrap_identities`):

1. **Every cold start** (default) — `MYCELIUM_TRANSPORT_ROTATE_ON_START=0` to disable.
2. **Or every N seconds** since last rotation — default 86400 (`MYCELIUM_TRANSPORT_ROTATION_SECS`).

First install does not rotate (initial `transport_meta` stamp only).

## Re-announce (hen–egg)

After rotation the node sends `IdentityRotationGossip` to each contact with an E2E session or enc key:

- **Channel:** Double-Ratchet / legacy E2E `EncryptedDirect` (inner prefix `MYCEL/IDROT/E2E\0`).
- **Not** public gossip (`mycelium/identity/v1`) — avoids linking account ↔ new PeerId for LAN/relay observers.

Contacts verify the account signature, remap `account_id → new_peer_id`, migrate chat history tree, group members, and node ratchet/enc-key maps.

## QR / invites

Signed invite v3 binds `account_id + transport_peer_id + enc_key + epoch`. After rotation, share a fresh QR or wait for E2E re-announce from an existing contact.

Related: [CONNECT.md](./CONNECT.md) · [ARCHITECTURE_RISKS.md](./ARCHITECTURE_RISKS.md) AR-03
