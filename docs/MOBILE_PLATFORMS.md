# Mobile platform strategy

**Status:** Groundwork (Android shipping; iOS planned)  
**Last updated:** 2026-07-02

This document records how Mycelium splits **shared Rust** from **native mobile shells**, so Android development can continue unchanged while iOS is added with minimal rework.

Related: [architecture.md](./architecture.md) · [SECURITY.md](./SECURITY.md) · [DISCOVERY.md](./DISCOVERY.md) · [ADR 001](./adr/001-mobile-host-architecture.md)

---

## Goals

1. **One mesh core** — protocol, crypto, persistence, and app logic stay in Rust crates.
2. **Native shells** — Android (Kotlin/Compose) and future iOS (Swift/SwiftUI) own OS integration only.
3. **Android-first** — current beta velocity on Android is unchanged; iOS groundwork must not block it.
4. **UniFFI as the mobile API contract** — `mycelium.udl` is the stable boundary for both Kotlin and Swift.
5. **No shared mobile UI** — Compose and SwiftUI stay separate; parity is behavioral, not pixel-identical.

---

## Architecture (today)

```
┌─────────────────────────────────────────────────────────────┐
│  Shared Rust (platform-neutral)                              │
│  mycelium-core → mycelium-node → mycelium-app → mycelium-coin│
└───────────────────────────────┬─────────────────────────────┘
                                │
          ┌─────────────────────┼─────────────────────┐
          │                     │                     │
          ▼                     ▼                     ▼
   mycelium-ffi            mycelium-desktop      mycelium-cli
   (UniFFI cdylib)         (Tauri, native Rust)  (headless + HTTP)
          │                     │
          ▼                     ▼
   android/                 crates/mycelium-desktop/ui/
   Kotlin + Compose         Svelte
```

**Important:** Desktop does **not** use UniFFI. It links the same Rust crates directly inside the Tauri process. Only **mobile** hosts use `mycelium-ffi`.

---

## Responsibility split

| Concern | Shared Rust | Native host (Android / iOS) |
|--------|-------------|-----------------------------|
| libp2p mesh, gossip, store-and-forward | `mycelium-node` | — |
| Chat, mail, bulletin, contacts, mini-apps | `mycelium-app` | — |
| At-rest encryption (given 32-byte master key) | `mycelium-core` (`SecretVault`) | Generate/store master key |
| Node bootstrap (runner, storage paths, coin) | `mycelium-ffi` today *(see Phase 2)* | Call `init_node` / supply `NodeConfig` |
| Foreground / background lifecycle | — | Service (Android) / BG modes (iOS) |
| System notifications | `NotificationSink` hook | Show notification UI |
| Events to UI | `NodeEventCallback` (UniFFI) | Broadcast / Flow (Android) · delegate (iOS) |
| QR / camera | — | Platform camera APIs |
| BLE “app nearby” hint | — | Kotlin today; Core Bluetooth on iOS |
| mDNS on Wi‑Fi | Rust (libp2p) | Android: multicast lock; iOS: network entitlement |
| Compose / SwiftUI | — | All screens and navigation |
| Mini-app WebView sandbox | Bridge in Rust | WebView + process isolation per platform |

---

## Binding layer

| Host | Mechanism | Contract | Regenerate |
|------|-----------|----------|------------|
| **Android** | UniFFI → Kotlin | `crates/mycelium-ffi/src/mycelium.udl` | `scripts/regenerate-uniffi-kotlin.sh` |
| **iOS (planned)** | UniFFI → Swift | Same UDL | `scripts/regenerate-uniffi-bindings.sh swift` *(stub)* |
| **Desktop** | Direct Rust in Tauri | Tauri commands in `mycelium-desktop/src/lib.rs` | N/A |

Hand-written facades sit above generated code:

- Android: `android/uniffi-bindings/.../MyceliumBindings.kt`
- iOS (planned): `ios/MyceliumBindings/` Swift wrapper

**Rule:** Do not add platform-specific logic to `mycelium-core` or `mycelium-app`. If Rust needs a host capability, extend a **trait** (`NotificationSink`, future `PlatformLog`) or pass data through `NodeConfig` / callbacks.

---

## What stays Android-only (for now)

These are **intentionally** not in shared Rust:

| Feature | Location | iOS equivalent (later) |
|---------|----------|------------------------|
| Foreground mesh service | `MeshService.kt` | `BGProcessingTask` / limited BG fetch |
| Encrypted prefs master key | `StorageKeyHelper.kt` | Keychain wrapper |
| BLE bootstrap advertise/scan | `bootstrap/BleBootstrap*.kt` | Core Bluetooth |
| Wi‑Fi multicast lock | `bootstrap/WifiMulticastLock.kt` | iOS mDNS without extra lock |
| Mesh event bus | `MeshEventBus.kt` | `NotificationCenter` / async stream |
| Mini-app isolated process | `miniapp/MiniAppProcessActivity.kt` | App extension or in-process WebView policy |
| APK sideload / share | Release pipeline | TestFlight / App Store *(different model)* |

---

## Phased alignment (minimal Android impact)

### Phase 1 — Groundwork *(current)*

- [x] Document host vs shared split (this file + ADR 001)
- [x] Neutralize UDL wording (`storage_key_hex` = host-provided, not “Android only”)
- [x] `scripts/regenerate-uniffi-bindings.sh` — Kotlin today, Swift hook for later
- [x] `ios/README.md` — scaffold and checklist, no Xcode project yet
- [x] Fix README: desktop uses native Rust, not UniFFI
- [x] Gate `tracing-android` dependency to Android targets only

**No changes** to `MeshService`, Gradle, or Android UX in this phase.

### Phase 2 — Shared host bootstrap crate *(before iOS coding)*

Extract duplicated node startup from `mycelium-ffi/src/lib.rs` and `mycelium-desktop/src/lib.rs` into a new crate, e.g. `mycelium-host`:

- `HostConfig` (db path, listen addr, storage key, settings)
- `HostRuntime::start()` → `(AppNode, NodeHandle, inbox streams)`
- FFI and Tauri become thin adapters

**Benefit:** One place to fix startup bugs; iOS calls the same bootstrap via UniFFI.  
**Risk:** Medium refactor — do when iOS work starts, with Android regression tests green.

### Phase 3 — iOS shell MVP

1. Add Rust targets: `aarch64-apple-ios`, `aarch64-apple-ios-sim`, `x86_64-apple-ios` (Intel sim optional)
2. Build `libuniffi_mycelium.a` / `.xcframework`
3. `uniffi-bindgen --language swift` → `ios/Generated/`
4. SwiftUI app: onboarding, Connect (QR), Chat, Settings
5. Keychain → `NodeConfig.storage_key_hex`
6. Free Apple ID or TestFlight for distribution (see below)

### Phase 4 — Parity and platform polish

Bulletin, mail, mini-apps, BLE, background policy — prioritized by user need, not feature checklist equality.

---

## iOS distribution (planning note)

| Method | Viral “pass app phone-to-phone” | Apple Developer $99/yr |
|--------|----------------------------------|-------------------------|
| APK-style sideload | ❌ | — |
| Free Apple ID + Xcode (7-day cert) | Personal device only | Free |
| TestFlight | Link/QR, 10k testers | Required |
| App Store | Best reach | Required |

**Product implication:** Mesh *messages* can spread offline today (QR invite, bulletin). Mesh *app binary* virality is an **Android strength**; iOS needs store/TestFlight or prepared sideload tooling.

---

## Adding a new mobile host — checklist

1. Read [ADR 001](./adr/001-mobile-host-architecture.md).
2. Implement master-key storage → 64-char hex → `NodeConfig.storage_key_hex`.
3. Call `init_node` once per process; keep node alive (service / BG task).
4. Implement `NodeEventCallback` → native event bus → UI refresh.
5. Regenerate bindings from `mycelium.udl`; add hand-written facade module.
6. Map notifications: chat, mail, bulletin, SOS, contact requests.
7. QR: camera permission + invite URI handling (`mycelium://invite/...`).
8. Do **not** fork `mycelium-app` or duplicate protocol logic in the shell language.

---

## Non-goals

- Flutter / React Native / KMM shared UI
- Single codebase for Android + iOS screens
- iOS feature parity before Android beta is stable
- Background mesh on iOS identical to Android foreground service

---

## File index

| Area | Path |
|------|------|
| UniFFI contract | `crates/mycelium-ffi/src/mycelium.udl` |
| FFI implementation | `crates/mycelium-ffi/src/lib.rs` |
| Notification hook | `crates/mycelium-app/src/notify.rs` |
| At-rest vault | `crates/mycelium-core/src/at_rest.rs` |
| Android host | `android/app/src/main/java/network/mycelium/app/` |
| Android build | `android/README.md` |
| iOS scaffold | `ios/README.md` |
| Binding regen | `scripts/regenerate-uniffi-bindings.sh` |
