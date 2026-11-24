# ADR 005: Layer cargo boundaries (L0–L3)

**Status:** Accepted
**Date:** 2026-07-03
**Context:** Mycena grew as a flat workspace where `mycena-app` depended directly on `mycena-core` and embedded the entire mini-app platform. That made it hard to enforce which crates may touch raw key material, ratchet internals, or mesh transport details.

---

## Decision

Introduce explicit **layer boundary crates** with dependency rules:

| Layer | Crate | Role | May depend on |
|-------|--------|------|----------------|
| **L0** | `mycena-core` | Crypto, wire formats, at-rest vault, mesh primitives | External crates only |
| **L0 facade** | `mycena-l0-facade` | Stable app-facing API over L0 (`EncKey`, invites, bootstrap, vault helpers) | `mycena-core` |
| **L1** | `mycena-node` | libp2p node, ratchet store, transport runner | `mycena-core`, `mycena-l0-facade` |
| **L1 facade** | `mycena-l1-facade` | Stable messaging/sync API over L1 (`NodeHandle`, `WireMessage`, …) | `mycena-node` |
| **L2** | `mycena-miniapp` | Mini-app manifests, bundles, store, bridge helpers | `mycena-l0-facade` only |
| **L3** | `mycena-app` | Chat, contacts, API, bridge host glue | `mycena-l0-facade`, `mycena-l1-facade`, `mycena-miniapp`, `mycena-coin` |

### L0 facade (`mycena-l0-facade`)

- **`EncKey`**: opaque wrapper around `EncryptionKeypair`; exposes `generate`, `public_hex`, `decrypt_direct`, `decrypt_group`, `clone`. Does **not** expose `secret_bytes` or `from_secret_bytes` publicly.
- Re-exports protocol modules: `data`, `invite`, `contact_hello`, `bootstrap`, `ble_mesh`, `relay_status`, `sos`, `energy`, `identity_rotation`, `e2e_direct_wrap`, `account_identity`, and `at_rest::{clear_keyring_master, parse_storage_key_hex}`.
- Hosts `secrets` (vault open, enc key load).
- **`PeerEncKeyStore`** lives in `mycena-node` (private); not part of the L0 facade.
- Ratchet/session code in **`mycena-node`** loads `mycena_core::crypto::EncryptionKeypair` via `secrets::load_or_create_encryption_keypair` — not through `EncKey`.

### L1 facade (`mycena-l1-facade`)

- Re-exports the app-safe node surface: `NodeHandle`, `NodeCommand`, `NodeConfig`, `NodeRunner`, `NodeMetrics`, `DirectMessage`, `WireMessage`, `ConnectivityMonitor`, identity bootstrap helpers, and `load_or_create_keypair`.
- Does **not** re-export `storage`, `ratchet_store`, `transport`, or mesh behaviour modules.

### L2 mini-app (`mycena-miniapp`)

- All mini-app platform sources live here (manifest, bundle, store, policy, etc.).
- **`bridge_host.rs` remains in `mycena-app`** because it depends on `AppNode`, `AppStorage`, and `CoinNode`; it is re-exported under `mycena_app::miniapp::bridge_host`.

### Dependency rules

1. **App-layer crates** (`mycena-app`, `mycena-miniapp`, `miniapp-sdk`, `mycena-coin`) must **not** depend on `mycena-core` or `mycena-node` directly.
2. **`mycena-node`** uses `mycena-core` for ratchet/transport internals and loads encryption keys as `EncryptionKeypair` (L1-only); app code uses opaque `EncKey` from the L0 facade.
3. **`EncKey`** does not expose raw key material or core keypair accessors; app/miniapp cannot reach ratchet setup APIs through the facade.
4. **`PeerEncKeyStore`** is `pub(crate)` inside `mycena-node` only.
5. **`DirectMessage` / `WireMessage`** are exported from `mycena-l1-facade` for app dispatch code.
6. Host binaries (`mycena-desktop`, `mycena-cli`, `mycena-relay`) use `mycena-l0-facade` and `mycena-l1-facade` only — no direct `mycena-core` / `mycena-node` dependency.
7. **`scripts/check-layer-boundaries.sh`** runs in `make ci` and fails if app/host-layer crates (including CLI and relay) depend on or import `mycena-core` / `mycena-node` directly, or reference removed `as_core_keypair` / `node-internal`.
8. **`make check-app-layer`** runs `cargo check` on app/host crates without `mycena-node`, verifying the L0 facade stays free of node-only APIs.

---

## Consequences

- Clear compile-time enforcement of key-material boundaries for new app code.
- Mini-app SDK and tooling can depend on `mycena-miniapp` without pulling the full app stack or node internals.
- Node and core remain coupled for ratchet evolution; session derivation stays in L1.
- App may still transitively resolve `mycena-core` via facades; the goal is **API boundary**, not hiding core from the linker.

---

## References

- [ADR 004: Double Ratchet E2E](./004-double-ratchet-e2e.md)
- [`mycena-l0-facade`](../../crates/mycena-l0-facade/src/lib.rs)
- [`mycena-l1-facade`](../../crates/mycena-l1-facade/src/lib.rs)
- [`mycena-miniapp`](../../crates/mycena-miniapp/src/lib.rs)
