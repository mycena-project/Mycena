# ADR 004: Double Ratchet for 1:1 direct E2E

**Status:** Accepted (v1 shipped; v0 legacy fallback retained)  
**Date:** 2026-07-03  
**Context:** Direct chat used static X25519 ECDH per message (`encrypt_for`). Device compromise exposed all historical ciphertext with that peer. Mycelium is offline-first — messages may arrive days later and out of order; skipped message keys must be bounded, not assuming live connectivity like Signal's online model.

---

## Decision

1. **Two wire protocols** — `WireMessage::EncryptedDirect::e2e_version` and `Envelope::e2e_version`:
   - **v0 (legacy):** ephemeral X25519 ECDH → HKDF → ChaCha20-Poly1305 per message ([`encrypt_for`](../../crates/mycelium-core/src/crypto/mod.rs)).
   - **v1 (ratchet):** X3DH session setup + Double Ratchet ([`crypto/ratchet.rs`](../../crates/mycelium-core/src/crypto/ratchet.rs)).

2. **X3DH-style bootstrap** — Prekey bundle advertised in `WireMessage::PeerInfo` (registration id, signed prekey + sig, optional one-time prekey). Verified with identity-keyed BLAKE3 MAC (TOFU model). Local prekeys persisted in `ratchet/` sled tree.

3. **Wire framing** — Ratchet payloads prefixed with `MYRT2\0` + bincode:
   - `Init` — X3DH material + first ratchet ciphertext (includes app plaintext).
   - `Message` — subsequent DH + symmetric ratchet steps.

4. **Offline / out-of-order** — Up to **512** skipped message keys per session (`MAX_SKIP_KEYS`), LRU-evicted. No assumption that peers are online when a gap is filled.

5. **Migration** — Node encrypt path prefers v1 when a peer bundle or existing session exists; otherwise v0. Decrypt accepts both. Mesh relay forwards ciphertext unchanged (`e2e_version` preserved).

6. **Persistence** — `RatchetSessionStore` (sessions, peer bundles, local prekeys) under `{db_path}/ratchet/`.

---

## Bootstrap sequence

```text
QR scan → ContactHello (enc pubkey, contact book)
       → libp2p/BLE connect → PeerInfo (enc pubkey + prekey bundle)
       → first EncryptedDirect v1 (Init) or v0 if bundle not yet cached
       → subsequent EncryptedDirect v1 (Message)
```

The **first** encrypted message after QR may still be **v0** if sent before `PeerInfo` arrives (~one connection round-trip). All messages after bundle exchange use v1 forward secrecy within the session.

---

## Non-goals (v1)

- Group ratchet / MLS (groups remain PSK ChaCha20-Poly1305).
- Signed prekey rotation schedule (bundle refreshed on each `PeerInfo`).
- ContactHello carrying prekeys (PeerInfo is the bundle channel).
- Full libsignal compatibility.

---

## Consequences

| Area | Change |
|------|--------|
| **Security** | Compromise of long-term enc key does not retroactively decrypt v1 session traffic after DH ratchet steps; v0 and pre-bootstrap messages remain vulnerable. |
| **Storage** | Extra sled DB; session state grows with skip cache (bounded). |
| **Interop** | Old peers without prekeys continue on v0 indefinitely. |
| **Docs** | `KNOWN_LIMITATIONS.md`, `SECURITY.md` updated; SD-100 partially mitigated. |

---

## References

- [`crates/mycelium-core/src/crypto/ratchet.rs`](../../crates/mycelium-core/src/crypto/ratchet.rs)
- [`crates/mycelium-node/src/ratchet_store.rs`](../../crates/mycelium-node/src/ratchet_store.rs)
- Signal Double Ratchet (conceptual model, not wire-compatible)
