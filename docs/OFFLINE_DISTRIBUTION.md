# Offline distribution (phone-to-phone)

Mycelium supports **offline app and miniapp distribution** without Play Store, desktop relay, or internet. This document covers the end-to-end flows, shared conventions, and **security implications**.

## Shared conventions

| Constant | Value | Purpose |
|----------|-------|---------|
| HTTP port | **8765** | APK / bootstrap sidecar (Android hotspot, desktop LAN) |
| APK path | `/mycelium.apk` | Installed package bytes |
| Bootstrap sidecar | `/mesh/bootstrap.myc` | JSON mesh join credentials |
| Wi‑Fi Direct bearer | **7765** | Bulk MYBR frames between mesh peers (miniapp bundles) |

Rust helpers live in `crates/mycelium-app/src/offline_share.rs`. Desktop uses `crates/mycelium-desktop/src/share_server.rs`; Android uses `WifiDirectSharer` + `LocalHotspotHelper`.

## Phone-to-phone APK flow (Android)

1. **Device A** opens **Share app** → local-only hotspot (or Wi‑Fi Direct fallback) + HTTP server on `:8765`.
2. If hotspot fails while connected to home Wi‑Fi, the app briefly disconnects and retries, then falls back to Wi‑Fi Direct. Home Wi‑Fi reconnects when sharing stops.
2. QR codes show:
   - Wi‑Fi join (`WIFI:…`) for device B’s camera app
   - Install URL (`http://192.168.43.x:8765/`) for browser download
3. Landing page serves APK and auto-downloads **`mycelium-bootstrap.myc`** (signed invite + sharer peer id).
4. **Device B** installs APK (no Mycelium required beforehand).
5. On first launch, `OfflineBootstrap` imports the sidecar from Downloads:
   - Auto display name (`guest-…`)
   - Bootstrap peers / invite URI
   - Skips manual onboarding
6. `MeshService` starts and dials the sharer’s mesh.

**No desktop required.** Same LAN mode works when both phones already share Wi‑Fi.

## Miniapp epidemic propagation (mesh)

When a device carrying signed miniapps enters a cluster (village, neighborhood):

1. **`propagate_installed_miniapps()`** (Rust / FFI) publishes:
   - Signed **listings** on `mycelium/appstore/v1`
   - **Bundle chunks** (16 KiB) on `mycelium/appstore/bundles/v1`
2. Receivers reassemble chunks, verify **listing signature + bundle hash**, then install via `install_verified(VerifiedListing)`.
3. Large transfers between connected peers also use **Wi‑Fi Direct bearer** (`queue_bearer_bulk`) with inbound TCP → `ingest_ble_frame` (Android).

Implementation: `crates/mycelium-app/src/miniapp_epidemic.rs`.

## Security implications (critical)

Offline distribution means **the install source must not be trusted by default**.

### APK sideload

- Android package signature is the only trust anchor for the **host app** itself.
- Users should only install from people they know; there is no app-store review in this path.
- Bootstrap sidecar only contains **dial targets** (invite URI); it does not execute code.

### Miniapp bundles

| Control | Why it matters offline |
|---------|------------------------|
| **Signed store listing** (`AppStoreListing.verify_signature`) | Listing hash must match developer Ed25519 key in manifest. Epidemic install **rejects** chunks without a verified listing. |
| **Bundle content hash** | Blake3 hash in listing must match reassembled bytes. |
| **`mycelium-bundle.sig.json`** (SD-041) | Sideload / manual install requires developer signature sidecar; epidemic path uses **VerifiedListing** only. |
| **Revocation gossip** | `mycelium/appstore/revocations/v1` can block known-bad app ids cluster-wide. |
| **No unsigned epidemic install** | `try_install_epidemic_bundle` refuses bundles without verified listing — gossip alone is insufficient. |

### Threat model notes

- **Malicious sharer** could serve a **modified APK** (different signing key). Recipients must verify out-of-band that the APK is an official build (hash check, known signer).
- **Gossip poisoner** can spam fake chunks; reassembly + hash + listing signature prevent install unless the attacker holds the **developer key** or fools the user into sideload ack.
- **Downgrade attacks** blocked unless explicitly allowed in manual install policy.
- **HTTP on hotspot is unencrypted** — acceptable for APK bytes in physical proximity; do not reuse for secrets.

### Operator checklist

1. Sign miniapp listings with the developer libp2p key before epidemic seeding.
2. Keep bundled revocations up to date in `mycelium-miniapp`.
3. Prefer **VerifiedListing** epidemic path; never bypass `install_verified` for gossip-delivered bytes.
4. Document local APK hash for your community if you run repeated offline installs.

## Related docs

- [ADR 007 — Wi‑Fi Direct bulk bearer](adr/007-wifi-direct-bulk-bearer.md)
- [MINIAPP_SECURITY_ARCHITECTURE.md](MINIAPP_SECURITY_ARCHITECTURE.md)
- [CONNECTIVITY_MODES.md](CONNECTIVITY_MODES.md)
- [MOBILE_PLATFORMS.md](MOBILE_PLATFORMS.md)

## Remaining gaps

- iOS: no offline IPA sideload; Network framework bearer planned for miniapp bulk between iOS peers.
- Foreground service for long-running hotspot share on Android.
- Unified HTTP router shared as a crate (desktop + Android HTML currently parallel).
- UI: explicit “Share miniapp to cluster” + progress for bearer bulk transfers.
- Hotspot share before mesh node ready: bootstrap sidecar requires running node for signed v3 invite.
