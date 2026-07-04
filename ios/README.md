# iOS host (planned)

**Status:** SwiftUI shell + BLE host; link UniFFI xcframework to run on device.

Mycelium’s iPhone app will be a **native Swift/SwiftUI shell** over the same Rust core as Android, via **UniFFI** and `crates/mycelium-ffi/src/mycelium.udl`.

Read first: [docs/MOBILE_PLATFORMS.md](../docs/MOBILE_PLATFORMS.md) · [docs/IOS_BLE_CONSTRAINTS.md](../docs/IOS_BLE_CONSTRAINTS.md) · [docs/adr/001-mobile-host-architecture.md](../docs/adr/001-mobile-host-architecture.md)

---

## Layout

```
ios/
├── README.md                 # this file
├── Mycelium.xcodeproj/       # SwiftUI app (future)
├── Mycelium/
│   ├── BLE/                  # CoreBluetooth mesh host (F1–F3)
│   │   ├── BleMeshHost.swift           # orchestrator (start from MeshHost)
│   │   ├── BleInviteGattServer.swift   # CBPeripheralManager GATT + advertise
│   │   ├── BleBootstrapScanner.swift   # CBCentralManager scan + restoration
│   │   ├── BleMeshHandoff.swift        # invite read → connectTransportInvite
│   │   ├── BleMeshRelay.swift          # unified bearer outbound drain
│   │   └── UniffiMyceliumBridge.swift  # Rust FFI facade
│   ├── App/
│   │   └── MyceliumApp.swift           # SwiftUI entry
│   ├── Services/
│   │   └── MeshHost.swift              # node + BleMeshHost lifecycle
├── MyceliumBindings/         # Hand-written Swift facade (future)
└── rust-build/               # libuniffi_mycelium.xcframework (gitignored)
```

Rust artifacts use the same `mycelium-ffi` crate as Android.

---

## BLE mesh host (implemented)

`BleMeshHost` mirrors Android `MeshService.updateBleBootstrap()`:

1. **Peripheral (F1):** connectable advertising + GATT invite read + mesh-data write ingest.
2. **Central (F1):** service-filtered scan + GATT invite read handoff.
3. **Relay:** 2 s timer drains unified bearer queue via GATT client sessions.

**Restoration IDs** (State Preservation API — required for background BLE):

- `network.mycelium.ble.peripheral`
- `network.mycelium.ble.central`

**Important:** Background mesh on iOS is **best-effort**, not Android foreground-service parity. See [IOS_BLE_CONSTRAINTS.md](../docs/IOS_BLE_CONSTRAINTS.md) before making reliability claims.

```swift
// MeshHost wires BleMeshHost when BLE bootstrap is enabled:
MeshHost.shared.startNode(bleBootstrap: true)
MeshHost.shared.setBleBootstrapEnabled(false)
MeshHost.shared.stop()
```

Generate Xcode project: `cd ios && xcodegen generate` (requires [XcodeGen](https://github.com/yonaskolb/XcodeGen)).

Build Rust FFI: `./scripts/build-ios-xcframework.sh`

---

## Prerequisites (Mac)

- Xcode 15+ (Apple Silicon: `aarch64-apple-ios-sim`)
- Rust: `rustup target add aarch64-apple-ios aarch64-apple-ios-sim`
- UniFFI Swift: `scripts/regenerate-uniffi-bindings.sh swift`

---

## Implementation checklist

- [x] XcodeGen spec + SwiftUI shell (`ios/project.yml`, `Mycelium/App/`)
- [ ] Run `xcodegen generate` and open `Mycelium.xcodeproj`
- [ ] Cross-compile + link `MyceliumFFI` xcframework (`scripts/build-ios-xcframework.sh`)
- [ ] Generate Swift from `mycelium.udl`
- [x] BLE peripheral + central mesh host (`ios/Mycelium/BLE/`)
- [x] iOS BLE constraints doc (`docs/IOS_BLE_CONSTRAINTS.md`)
- [x] `MeshHost` → `BleMeshHost` wiring (`Services/MeshHost.swift`)
- [ ] Keychain wrapper → 32-byte master key hex; `initNode` + `NodeEventCallback`
- [ ] Screens: Setup, Connect (QR), Chat, Settings
- [ ] CI job (optional): build sim + unit tests

---

## What not to put here

- Protocol or crypto logic (belongs in `crates/`)
- Duplicate of `mycelium-app` features in Swift
- Shared UI with Android

---

## Distribution note

Personal sideload: free Apple ID + Xcode (7-day signing).  
Wider beta: Apple Developer Program + TestFlight.  
See [MOBILE_PLATFORMS.md § iOS distribution](../docs/MOBILE_PLATFORMS.md#ios-distribution-planning-note).
