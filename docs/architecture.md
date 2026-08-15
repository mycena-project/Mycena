# Mycelium Architecture

Current layout (2026-08). The stack is **Android-first** with a shared Rust mesh core. This is not a CLI-only MVP.

## Workspace crates

| Crate | Role |
|-------|------|
| `mycelium-core` | Crypto, wire formats, at-rest vault, protocol IDs (L0) |
| `mycelium-l0-facade` | Stable app-facing re-exports of L0 (`EncKey`, invites, wipe helpers) |
| `mycelium-node` | libp2p node, gossip, custody DTN, ingest (L1) |
| `mycelium-l1-facade` | Stable node API (`NodeHandle`, `WireMessage`) |
| `mycelium-app` | Chat, contacts, loopback HTTP API, mini-app bridge host (L3) |
| `mycelium-miniapp` | Manifests, bundles, wasmtime sandbox (L2) |
| `mycelium-coin` | Experimental local MeshCoin ledger (preview only) |
| `mycelium-ffi` | UniFFI for Android (and future iOS) |
| `mycelium-cli` | Operator CLI + loopback API |
| `mycelium-desktop` | Tauri desktop host |
| `mycelium-relay` | Public libp2p relay + token-gated `/status` |
| `mycelium-sim` | MyceliumLab simulations / CI gates |

Layer rules: [adr/005-layer-cargo-boundaries.md](./adr/005-layer-cargo-boundaries.md). Enforced by `scripts/check-layer-boundaries.sh` (includes CLI and relay).

## Mobile hosts

Android (Kotlin/Compose + UniFFI) is the **shipping** beta host. iOS is a SwiftUI shell over the same UniFFI contract — Simulator/device scaffolding exists; it is **not** App Store ready. Desktop uses native Rust in Tauri (not UniFFI).

- [MOBILE_PLATFORMS.md](./MOBILE_PLATFORMS.md)
- [adr/001-mobile-host-architecture.md](./adr/001-mobile-host-architecture.md)
- [../ios/README.md](../ios/README.md)

## Runtime flow

1. Host boots an ed25519 identity (at-rest vault; Android Keystore/StrongBox-backed master when available).
2. libp2p listens (TCP/QUIC); mDNS discovers LAN peers; optional BLE / Wi-Fi Direct / relay circuit.
3. Direct chat is fail-closed until an encryption public key is known; gossip is signed at the envelope layer.
4. Store-and-forward uses sled with TTL; custody DTN is experimental.
5. Loopback HTTP API (CLI) requires a bearer token; panic wipe only deletes the configured node directory.

## Tradeoffs

- **libp2p** — composable discovery; large dependency surface (see [DEPENDENCY_RISK.md](./DEPENDENCY_RISK.md)).
- **sled 0.34** — embedded KV; effectively unmaintained; migration planned.
- **mDNS** — LAN-only; Internet path needs relay or QR.
- **MeshCoin / WASM / WebRTC** — preview or experimental; not beta security claims.

Deeper: [CONNECT.md](./CONNECT.md) · [THREAT_MODEL.md](./THREAT_MODEL.md) · [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md)
