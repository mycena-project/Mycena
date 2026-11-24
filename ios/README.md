# iOS host (planned)

**Status:** Scaffold — SwiftUI + BLE host compile for Simulator; physical-device BLE is best-effort and **not** feature-complete vs Android. Not TestFlight/App Store ready.

Mycena’s iPhone app will be a **native Swift/SwiftUI shell** over the same Rust core as Android, via **UniFFI** and `crates/mycena-ffi/src/mycena.udl`.

Read first: [docs/MOBILE_PLATFORMS.md](../docs/MOBILE_PLATFORMS.md) · [docs/IOS_BLE_CONSTRAINTS.md](../docs/IOS_BLE_CONSTRAINTS.md) · [docs/adr/001-mobile-host-architecture.md](../docs/adr/001-mobile-host-architecture.md)

---

## Layout

```
ios/
├── README.md                 # this file
├── Mycena.xcodeproj/       # SwiftUI app (future)
├── Mycena/
│   ├── BLE/                  # CoreBluetooth mesh host (F1–F3)
│   │   ├── BleMeshHost.swift           # orchestrator (start from MeshHost)
│   │   ├── BleInviteGattServer.swift   # CBPeripheralManager GATT + advertise
│   │   ├── BleBootstrapScanner.swift   # CBCentralManager scan + restoration
│   │   ├── BleMeshHandoff.swift        # invite read → connectTransportInvite
│   │   ├── BleMeshRelay.swift          # unified bearer outbound drain
│   │   └── UniffiMycenaBridge.swift  # Rust FFI facade
│   ├── App/
│   │   └── MycenaApp.swift           # SwiftUI entry
│   ├── Services/
│   │   ├── MeshHost.swift              # initNode + BLE lifecycle
│   │   ├── StorageKeyHelper.swift      # Keychain master key
│   │   ├── MeshNodeCallback.swift      # NodeEventCallback → UI / notifications
│   │   ├── MeshEvents.swift            # NotificationCenter bus
│   │   └── MeshSettings.swift          # UserDefaults host prefs
├── MycenaBindings/         # Hand-written Swift facade (future)
└── rust-build/               # libuniffi_mycena.xcframework (gitignored)
```

Rust artifacts use the same `mycena-ffi` crate as Android.

---

## BLE mesh host (implemented)

`BleMeshHost` mirrors Android `MeshService.updateBleBootstrap()`:

1. **Peripheral (F1):** connectable advertising + GATT invite read + mesh-data write ingest.
2. **Central (F1):** service-filtered scan + GATT invite read handoff.
3. **Relay:** 2 s timer drains unified bearer queue via GATT client sessions.

**Restoration IDs** (State Preservation API — required for background BLE):

- `io.github.mycena_project.ble.peripheral`
- `io.github.mycena_project.ble.central`

**Important:** Background mesh on iOS is **best-effort**, not Android foreground-service parity. See [IOS_BLE_CONSTRAINTS.md](../docs/IOS_BLE_CONSTRAINTS.md) before making reliability claims.

```swift
// App launch (see MycenaApp.swift):
await MeshHost.shared.startNode(bleBootstrap: true)  // false in Simulator
MeshHost.shared.setBleBootstrapEnabled(false)
MeshHost.shared.stop()
```

## Quick start (Tier 0 — build & run in Simulator)

```bash
# 1. Rust FFI + UniFFI Swift + xcframework (~5–10 min first run)
./scripts/build-ios-xcframework.sh

# 2. Generate Xcode project
./scripts/ios-xcodegen.sh

# 3. Open in Xcode (or build from CLI)
open ios/Mycena.xcodeproj
# Simulator: pick iPhone 17 (or any iOS 26 sim). BLE is off in Simulator by design.
```

## Device testing (Tier 1)

1. Sign with your Apple ID in Xcode (Signing & Capabilities).
2. Run on a **physical iPhone** — BLE bootstrap is enabled automatically.
3. Use a second phone (Android or iPhone) nearby; watch **Connected peers** in the shell UI.
4. Accept the **Local Network** privacy prompt (mDNS / LAN discovery).

Regenerate Swift bindings only: `./scripts/regenerate-uniffi-bindings.sh swift`

---

## Prerequisites (Mac)

- Xcode 15+ (Apple Silicon: `aarch64-apple-ios-sim`)
- Rust: `rustup target add aarch64-apple-ios aarch64-apple-ios-sim`
- XcodeGen: `brew install xcodegen` **or** bundled via `scripts/ios-xcodegen.sh` (downloads to `scripts/tools/`)

Generate Xcode project: `./scripts/ios-xcodegen.sh`
Build Rust FFI: `./scripts/build-ios-xcframework.sh`

---

## Implementation checklist

- [x] XcodeGen spec + SwiftUI shell (`ios/project.yml`, `Mycena/App/`)
- [x] Run `xcodegen generate` and open `Mycena.xcodeproj` (`scripts/ios-xcodegen.sh`)
- [x] Cross-compile + link `MycenaFFI` xcframework (`scripts/build-ios-xcframework.sh`)
- [x] Generate Swift from `mycena.udl` (`scripts/regenerate-uniffi-swift.sh`)
- [x] BLE peripheral + central mesh host (`ios/Mycena/BLE/`)
- [x] iOS BLE constraints doc (`docs/IOS_BLE_CONSTRAINTS.md`)
- [x] `MeshHost` → `BleMeshHost` wiring (`Services/MeshHost.swift`)
- [x] Keychain wrapper → 32-byte master key hex; `initNode` + `NodeEventCallback`
- [ ] Onboarding / identity welcome flow
- [ ] Screens: Connect (QR), Chat, Settings
- [ ] Local notifications for chat / SOS
- [ ] CI job (optional): build sim + unit tests

---

## What not to put here

- Protocol or crypto logic (belongs in `crates/`)
- Duplicate of `mycena-app` features in Swift
- Shared UI with Android

---

## Distribution note

Personal sideload: free Apple ID + Xcode (7-day signing).
Wider beta: Apple Developer Program + TestFlight.
See [MOBILE_PLATFORMS.md § iOS distribution](../docs/MOBILE_PLATFORMS.md#ios-distribution-planning-note).
