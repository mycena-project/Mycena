# Mycelium Security Model

## Transport security

All node-to-node connections use the Noise protocol (XX handshake pattern)
with Ed25519 identity keys. Traffic is encrypted in transit.

## Message authentication

Every message carries an Ed25519 signature from the originating node.
Relay nodes verify signatures before forwarding. Messages with invalid
or missing signatures are dropped (for encrypted direct messages) or
logged as warnings (for legacy unencrypted messages during transition).

Signature scheme v1 covers: message ID, sender peer ID, recipient peer ID,
payload, timestamp, TTL, priority, hop count, max hops.

## End-to-end encryption

Direct messages use X25519 for key agreement and ChaCha20-Poly1305 AEAD.

- **v1 (preferred):** X3DH session setup via `PeerInfo` prekey bundle, then Signal-style
  Double Ratchet per conversation ([ADR 004](./adr/004-double-ratchet-e2e.md)).
  Provides forward secrecy for messages after session establishment.
- **v0 (legacy fallback):** Static ephemeral ECDH per message when a peer has no ratchet
  bundle yet or runs old software.

Group messages use a pre-shared symmetric key with ChaCha20-Poly1305.

**Limitation**: v0 traffic and the first encrypted message before prekey exchange lack
forward secrecy. Compromise of the long-term encryption key can decrypt those messages.
v1 ratchet sessions limit retroactive exposure to pre-bootstrap ciphertext only.

## At-rest encryption

Identity keys and encryption keys are stored encrypted at rest using
ChaCha20-Poly1305 with a master key from:

1. Android Keystore-backed preferences (Android, preferred)
2. OS keyring (Desktop)
3. Random per-device file-based key (fallback)

The fallback is weaker than the Keystore/keyring options but stronger
than plaintext. Upgrade your OS to ensure keyring availability.

## Known limitations (beta)

- **Forward secrecy (partial):** v1 Double Ratchet protects post-handshake 1:1 traffic;
  v0 fallback and pre-PeerInfo messages remain decryptable if the enc key is compromised.
- **MeshCoin consensus**: The 3-witness model has no Sybil defense.
  MeshCoin is experimental and has no real monetary value.
- **Metadata**: Message sizes, timing, and routing patterns are visible
  to relay nodes even though content is encrypted.

## Reporting vulnerabilities

Please report security issues privately — see [SECURITY.md](../SECURITY.md) at the repository root.
