# Mycelium — Security & privacy (for users)

This page explains what the app protects, what it does not, and how to use safety features.

## How Mycelium works

Mycelium is a **delay-tolerant mesh network**. Messages can travel device-to-device without constant internet. They may be **stored and forwarded** by other nodes until they reach the recipient.

That is useful offline or in poor connectivity — but it is **not** the same as a central server you trust.

## End-to-end encryption (chat)

Direct chats can be **end-to-end encrypted** when both devices have exchanged encryption keys.

- You may see **“encrypted”** or a key icon when protection is active.
- If keys are not yet available, the app may send **without E2E** and warn you (or show that encryption is pending).

**E2E protects message content** on the path between devices. It does **not** hide that you are using the network or talking to a given peer ID.

## Groups

Group chats use a **shared group key**. Anyone with the group invite (QR / link) can read and send group messages.

- Treat group invites like **passwords**: only share with people you trust.
- Leaving or deleting a group on your device does not remove history on other members’ devices.

## Bulletin board & mail

Bulletin posts are intended for **scoped public channels** (like a local board). Mail is store-and-forward to a peer ID. Assume recipients or relays may see metadata (who, when, size) even when content is protected elsewhere.

## Wallet (MeshCoin)

Wallet keys and balances are stored on your device. Protect your phone or computer like a physical wallet. **Panic wipe** (below) deletes local wallet data.

## Panic wipe

**Settings → Danger zone → Panic wipe** immediately:

- Stops the node
- Deletes local database (messages, identity keys, wallet, mini-app data)
- Clears app preferences (Android also clears the encrypted storage key)
- Restarts the app

Use this if you need to **quickly remove sensitive data from this device**.

Panic wipe does **not**:

- Delete messages already on other devices
- Remove data from backups outside Mycelium
- Guarantee forensic erasure of flash storage (OS-level secure delete is outside the app)

## Mini-app store

Apps listed in the store are signed by a developer peer ID when possible. Only install apps you trust. The network may cache listings from peers you have never met.

## Keys on your device

Identity and encryption keys are stored **encrypted on disk** (OS keychain on desktop where available; Android uses a key in encrypted preferences). This protects against casual file access but **not** against a fully compromised operating system.

## What we do not provide yet

- **Forward secrecy** (compromised keys do not retroactively expose old messages)
- Guaranteed anonymity or traffic analysis resistance
- Legal advice — see your jurisdiction and [`LICENSE`](../LICENSE)

For developers and threat models, see [`network-hardening.md`](network-hardening.md) and [`AGENT_CONTINUATION.md`](AGENT_CONTINUATION.md).
