# iOS host (planned)

**Status:** Scaffold only — no Xcode project yet.

Mycelium’s iPhone app will be a **native Swift/SwiftUI shell** over the same Rust core as Android, via **UniFFI** and `crates/mycelium-ffi/src/mycelium.udl`.

Read first: [docs/MOBILE_PLATFORMS.md](../docs/MOBILE_PLATFORMS.md) · [docs/adr/001-mobile-host-architecture.md](../docs/adr/001-mobile-host-architecture.md)

---

## Intended layout (when implementation starts)

```
ios/
├── README.md                 # this file
├── Mycelium.xcodeproj/       # SwiftUI app (future)
├── Mycelium/                 # App target source
│   ├── MyceliumApp.swift
│   ├── Services/
│   │   ├── MeshHost.swift    # init_node, NodeEventCallback
│   │   └── KeychainStorage.swift  → NodeConfig.storage_key_hex
│   ├── UI/                   # SwiftUI screens (parity with android/ui/)
│   └── Generated/            # UniFFI Swift output (gitignored or regen in CI)
├── MyceliumBindings/         # Hand-written Swift facade (like MyceliumBindings.kt)
└── rust-build/               # libuniffi_mycelium.xcframework (gitignored)
```

Rust artifacts will be produced from the repo root (same `mycelium-ffi` crate as Android).

---

## Prerequisites (Mac)

- Xcode 15+ (you have Apple Silicon — use `aarch64-apple-ios-sim`)
- Rust: `rustup target add aarch64-apple-ios aarch64-apple-ios-sim`
- UniFFI Swift generation: `scripts/regenerate-uniffi-bindings.sh swift` *(not wired yet)*

---

## Implementation checklist

- [ ] Xcode project + empty SwiftUI shell
- [ ] Cross-compile `mycelium-ffi` to iOS; link in app
- [ ] Generate Swift from `mycelium.udl`
- [ ] Keychain wrapper → 32-byte master key hex
- [ ] `MeshHost`: call `initNode`, implement `NodeEventCallback`
- [ ] Screens: Setup, Connect (QR), Chat, Settings
- [ ] Foreground mesh (BG policy documented in MOBILE_PLATFORMS.md)
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
