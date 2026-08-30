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

1. **Device A** opens **Share Mycelium**. The app requests **Nearby Wi‑Fi devices** (API 33+) or **fine location** (API 26–32) before starting a local hotspot. Denial is explained; Quick Share still works. Location must be ON on API 26–32 for LocalOnlyHotspot on most phones.
2. **Transport order:** LocalOnlyHotspot → (API 28 and older only) brief STA Wi‑Fi disconnect + retry → Wi‑Fi Direct group owner. API 29+ skips the disconnect: `WifiManager.disconnect` / `enableNetwork` are no-ops for third-party apps.
3. The HTTP server binds **after** a non-loopback IPv4 is observed on the share link (Direct group-owner address from the P2P API when available; otherwise a newly appeared SoftAP/wlan/p2p interface). The advertised URL is never `localhost`.
4. QR codes on Device A:
   - Wi‑Fi join (`WIFI:…`) only when SSID is non-blank
   - Install URL (`http://<share-ipv4>:8765/`)
   - Mesh invite (when the node produced a sidecar) — Device B scans this in **Contacts** after install
5. Landing page (`/`): APK download, SHA-256, unknown-apps / Samsung Auto Blocker / Play Protect instructions (no bypass), optional bootstrap file. `GET /mycelium.apk.sha256` returns the same hash. Empty sidecar is **404**, not an empty 200.
6. **Device B** installs via the system installer (no Mycelium yet). Allow the browser to install unknown apps if asked. If Samsung or Play Protect blocks, change those settings and retry — the app cannot override them.
7. **Join mesh (do not rely on Downloads):** Chrome’s download is not listable under scoped storage (API 29+ / target 34). Device B should scan Device A’s **Step 3 invite QR**, or on first launch pick `mycelium-bootstrap.myc` via the file picker. Downloads auto-import remains best-effort only.
8. Sharing **stops** when Device A leaves the Share screen (ViewModel cleared), taps Stop, or the process dies. There is **no** foreground service for APK share. Keep the screen open (the UI holds a keep-screen-on flag while serving).

**Artifact:** Phone-to-phone share serves a cache copy of the **Technical Beta APK** currently installed on the sharing phone. That APK is a complete universal `assembleRelease` package (armeabi-v7a, arm64-v8a, x86_64), signed with the local Technical Beta key — **not** the Play Store artifact, **not** a debug APK, **not** official.

Share **fails closed** (actionable UI, no silent fallback) if:

- the install is a **development/debug** APK (`FLAG_DEBUGGABLE` / `BuildConfig.TECHNICAL_BETA=false`);
- the install is a **Play split** (base APK only);
- the package file is **missing or empty**.

The advertised HTTP URL is never `localhost`. Quick Share uses `<cache-path>`, not filesystem `root-path`.

Build the Technical Beta APK with `./scripts/build-technical-beta-apk.sh` (see [TECHNICAL_BETA_ARTIFACT.md](./TECHNICAL_BETA_ARTIFACT.md)). `./android/build.sh` remains the **Development APK** (`assembleDebug`). GitHub `reproducible-build.sh` still copies debug until that pipeline is changed separately. Play AAB is out of this freeze path.

The landing page states that the file is for technical-beta/offline testing and is not the Play Store artifact.

**No desktop required.** Same LAN mode uses ConnectivityManager Wi‑Fi IPv4 when both phones already share a network.

**Permissions:** Bluetooth is **not** requested for hotspot/Direct. Quick Share (system sheet) may need Bluetooth on the device; that is a system flow.

See [PHONE_TO_PHONE_MANUAL_TEST.md](./PHONE_TO_PHONE_MANUAL_TEST.md) for hardware steps (P2P-MT-*).

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

- **Malicious sharer** could serve a **modified APK** (different signing key). Recipients should compare SHA-256 with the sharing phone / [TECHNICAL_BETA_ARTIFACT.md](./TECHNICAL_BETA_ARTIFACT.md). This path is **not** a Play-reviewed or official M-of-N build.
- **Gossip poisoner** can spam fake chunks; reassembly + hash + listing signature prevent install unless the attacker holds the **developer key** or fools the user into sideload ack.
- **Downgrade attacks** blocked unless explicitly allowed in manual install policy.
- **HTTP on hotspot is unencrypted** — acceptable for APK bytes in physical proximity; do not reuse for secrets.

### Operator checklist

1. Sign miniapp listings with the developer libp2p key before epidemic seeding.
2. Keep bundled revocations up to date in `mycelium-miniapp`.
3. Prefer **VerifiedListing** epidemic path; never bypass `install_verified` for gossip-delivered bytes.
4. Document local APK hash for your community if you run repeated offline installs.

## Reinstall and signing keys (HV-16 / HV-17)

| From \ To | Result |
|-----------|--------|
| Same Technical Beta key + `adb install -r` | App data **kept** |
| Uninstall, then install | App data **lost** (`allowBackup=false`) |
| Debug ↔ Technical Beta ↔ Play | **Cannot** update in place (different trust roots); uninstall first |

## Related docs

- [ADR 007 — Wi‑Fi Direct bulk bearer](adr/007-wifi-direct-bulk-bearer.md)
- [MINIAPP_SECURITY_ARCHITECTURE.md](MINIAPP_SECURITY_ARCHITECTURE.md)
- [CONNECTIVITY_MODES.md](CONNECTIVITY_MODES.md)
- [MOBILE_PLATFORMS.md](MOBILE_PLATFORMS.md)

## Remaining gaps

- iOS: no offline IPA sideload; Network framework bearer planned for miniapp bulk between iOS peers.
- **No foreground service for APK share** (intentional for this freeze). Leaving Share or process death stops `:8765`. Documented in [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md).
- Unified HTTP router shared as a crate (desktop + Android HTML currently parallel).
- UI: explicit “Share miniapp to cluster” + progress for bearer bulk transfers.
- Two-phone hardware matrix still required ([HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md) HV-10/HV-11; [PHONE_TO_PHONE_MANUAL_TEST.md](./PHONE_TO_PHONE_MANUAL_TEST.md)); this document describes code, not a completed G3 drill.
