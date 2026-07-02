# ADR 001: Mobile host architecture (Android + future iOS)

**Status:** Accepted  
**Date:** 2026-07-02  
**Context:** Mycelium needs a long-term iPhone app. Android is the production beta. We must choose boundaries now so iOS does not force a rewrite later, without slowing Android work.

---

## Decision

1. **Rust crates remain platform-neutral.** No `#[cfg(ios)]` in `mycelium-core`, `mycelium-node`, or `mycelium-app` except where absolutely required for portability (none today).

2. **Mobile hosts talk to Rust only through UniFFI.** The contract is `crates/mycelium-ffi/src/mycelium.udl`. Android uses Kotlin bindings; iOS will use Swift bindings from the **same UDL**.

3. **Desktop is a separate host.** Tauri calls Rust crates directly (no UniFFI). Duplicated bootstrap logic is acceptable short-term; extract `mycelium-host` before iOS implementation (Phase 2 in [MOBILE_PLATFORMS.md](../MOBILE_PLATFORMS.md)).

4. **Native UI is never shared.** Android keeps Kotlin/Compose; iOS gets Swift/SwiftUI. Behavioral parity (chat, invites, bulletin scopes) is tested; layout and navigation may differ.

5. **Platform capabilities stay in the shell language:**
   - Master key storage (EncryptedSharedPreferences / Keychain)
   - Foreground service / background tasks
   - BLE bootstrap, camera, notifications
   - App distribution (APK vs TestFlight)

6. **OS-specific mesh helpers stay out of Rust** until a second platform needs the same abstraction. Example: BLE advertise is Kotlin today; if iOS needs it, consider a thin `PlatformDiscovery` trait in the host layer—not in libp2p.

---

## Alternatives considered

| Option | Rejected because |
|--------|------------------|
| **Compose Multiplatform for UI** | Large rewrite; Android beta would stall; iOS WebView/mini-app story differs |
| **Flutter shell** | Third stack; FFI/WebView bridge complexity; team investment in Compose |
| **Rust-only mobile UI (eg. Slint)** | Poor platform feel; fights iOS HIG and Android Material |
| **Duplicate Rust core for iOS** | Unmaintainable; defeats purpose of shared mesh |
| **UniFFI for desktop too** | Desktop already works natively; extra indirection for no gain |

---

## Consequences

### Positive

- Android path unchanged; existing CI and releases unaffected.
- iOS adds a shell + Swift bindings, not a new protocol stack.
- Clear rule for contributors: “business logic → Rust; OS → Kotlin/Swift.”

### Negative / tradeoffs

- Two UI codebases to maintain for mobile.
- `mycelium-ffi` init and `mycelium-desktop` startup are duplicated until Phase 2 extract.
- iOS background mesh will be weaker than Android by OS policy—not fixable in architecture alone.

---

## Compliance

- New mobile features: implement in `mycelium-app` if domain logic; expose via UDL if mobile needs it; UI only in `android/` or future `ios/`.
- UDL changes require regenerating Kotlin bindings and updating `MyceliumBindings.kt`; document Swift impact in PR.
- Do not add Android-only fields to UDL without marking them optional or usable on iOS.
