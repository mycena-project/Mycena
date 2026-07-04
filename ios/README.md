# iOS host (planned)

**Status:** BLE mesh host implemented (Swift); Xcode app shell still pending.

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
│   │   ├── BleMeshRelay.swift          # F2/F3 outbound drain
│   │   └── UniffiMyceliumBridge.swift  # Rust FFI facade
│   ├── Resources/
│   │   └── Info.ble.plist.snippet      # UIBackgroundModes + BT usage string
│   ├── Services/             # (future) MeshHost.swift, KeychainStorage.swift
│   └── UI/                   # (future) SwiftUI screens
├── MyceliumBindings/         # Hand-written Swift facade (future)
└── rust-build/               # libuniffi_mycelium.xcframework (gitignored)
```

Rust artifacts use the same `mycelium-ffi` crate as Android.

---

## BLE mesh host (implemented)

`BleMeshHost` mirrors Android `MeshService.updateBleBootstrap()`:

1. **Peripheral (F1):** connectable advertising + GATT invite read + mesh-data write ingest.
2. **Central (F1):** service-filtered scan + GATT invite read handoff.
3. **Relay (F2/F3):** 2 s timer drains Rust outbound queues via GATT client sessions.

**Restoration IDs** (State Preservation API — required for background BLE):

- `network.mycelium.ble.peripheral`
- `network.mycelium.ble.central`

**Important:** Background mesh on iOS is **best-effort**, not Android foreground-service parity. See [IOS_BLE_CONSTRAINTS.md](../docs/IOS_BLE_CONSTRAINTS.md) before making reliability claims.

```swift
// Wire when node is ready and user enabled BLE bootstrap in settings:
let bleHost = BleMeshHost()
bleHost.start(enabled: true)
// … on teardown:
bleHost.stop()
```

Merge `Mycelium/Resources/Info.ble.plist.snippet` into the app `Info.plist`.

---

## Prerequisites (Mac)

- Xcode 15+ (Apple Silicon: `aarch64-apple-ios-sim`)
- Rust: `rustup target add aarch64-apple-ios aarch64-apple-ios-sim`
- UniFFI Swift: `scripts/regenerate-uniffi-bindings.sh swift`

---

## Implementation checklist

- [ ] Xcode project + empty SwiftUI shell
- [ ] Cross-compile `mycelium-ffi` to iOS; link `MyceliumFFI` xcframework
- [ ] Generate Swift from `mycelium.udl`
- [x] BLE peripheral + central mesh host (`ios/Mycelium/BLE/`)
- [x] iOS BLE constraints doc (`docs/IOS_BLE_CONSTRAINTS.md`)
- [ ] Keychain wrapper → 32-byte master key hex
- [ ] `MeshHost`: call `initNode`, implement `NodeEventCallback`, call `BleMeshHost`
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
