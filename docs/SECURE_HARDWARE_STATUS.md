# Secure hardware & user key storage — status

**Status:** Audit (2026-07-05)
**Audience:** Security reviewers, mobile engineers, release managers
**Related:** [THREAT_MODEL.md](./THREAT_MODEL.md) · [SECURITY.md](./SECURITY.md) · [MOBILE_PLATFORMS.md](./MOBILE_PLATFORMS.md) · [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) (SD-120, SD-131)

---

## Executive summary

| Question | Answer |
|----------|--------|
| **Is there a separate repository for the native Android/iOS app?** | **No.** The mobile hosts live in this monorepo: [`android/`](../android/) (shipping Kotlin/Compose shell) and [`ios/`](../ios/) (SwiftUI shell in progress). Other Mycelium repos ([mycelium-relay](https://github.com/mycena-project/mycelium-relay), [mycelium-web](https://github.com/mycena-project/mycelium-web)) are relay and marketing only — see [ECOSYSTEM.md](./ECOSYSTEM.md). |
| **Does `release_integrity.rs` / `reproducible_build.rs` protect user keys on device?** | **No.** Those modules attest **official build artifacts** (APK, desktop bundles, mini-app bundles) via M-of-N release signing and reproducible-build digests. They do **not** touch end-user identity keys. |
| **Is hardware-bound user key storage done?** | **Partial.** Android: EncryptedSharedPreferences with **StrongBox requested** (software Keystore fallback). iOS: Keychain (`AfterFirstUnlockThisDeviceOnly`) for the 32-byte master key. Identity keys are **not** non-exportable Secure Enclave / StrongBox objects. Root/jailbreak still wins. |

**Do not read [THREAT_MODEL.md](./THREAT_MODEL.md) as implying full hardware keystore integration.** Use this document as the source of truth for what is actually implemented on device.

---

## Repository layout (clarification)

Some checkouts use a parent workspace (e.g. `the maintainer/`) where **`android/` is not at the workspace root**. The native Android app is always:

```text
Mycelium/android/          ← Kotlin host, Gradle, StorageKeyHelper.kt
Mycelium/ios/              ← Swift host (partial)
Mycelium/crates/mycelium-ffi/   ← UniFFI boundary (storage_key_hex in NodeConfig)
Mycelium/crates/mycelium-core/src/at_rest.rs   ← SecretVault (ChaCha20-Poly1305)
```

There is **no** hidden mobile-only GitHub repository for keystore integration.

---

## What protects what

| Component | Protects | Does **not** protect |
|-----------|----------|----------------------|
| [`release_integrity.rs`](../crates/mycelium-core/src/release_integrity.rs) | Integrity of **released binaries** (BLAKE3 + M-of-N Ed25519 release signers) | User Ed25519 / X25519 keys on phone |
| [`reproducible_build.rs`](../crates/mycelium-miniapp/src/reproducible_build.rs) | Mini-app **bundle** recipe attestation in store listings | User keys |
| [`SecretVault` / `at_rest.rs`](../crates/mycelium-core/src/at_rest.rs) | Identity & enc key **files** (`.secrets/*.enc`) given a 32-byte master key | Where that master key lives — **host responsibility** |
| Android `StorageKeyHelper.kt` | Master key **confidentiality at rest** via EncryptedSharedPreferences | Hardware non-exportability of identity keys; root resistance |
| Desktop OS keyring (`keyring` crate) | Master key in platform credential store | Mobile parity; theft with unlocked session |
| iOS Keychain (shipping in shell) | Master key in Keychain, this-device-only | Not Secure Enclave-bound identity keys; app not store-ready |

---

## Android (in monorepo — partial)

**Implementation:** [`android/app/src/main/java/network/mycelium/app/StorageKeyHelper.kt`](../android/app/src/main/java/network/mycelium/app/StorageKeyHelper.kt)

**Flow:**

1. `SecureRandom` generates a 32-byte hex master key on first launch.
2. Key is stored in `EncryptedSharedPreferences` (`mycelium_secure` / `storage_key_hex`).
3. `MasterKey.Builder` (AES256-GCM) uses Android Keystore to encrypt the **preferences file**.
4. `MeshService` passes `storageKeyHex` into UniFFI → `NodeConfig.storage_key` → `SecretVault::open`.

**What this is:**

- Keystore-backed **prefs encryption** (AndroidX Security Crypto).
- At-rest encryption of Rust identity blobs with a host-supplied master key.

**What this is not:**

- Identity keys stored **inside** Android Keystore as non-exportable key objects.
- StrongBox / TEE-only master key (`setIsStrongBoxBacked(true)`).
- User authentication required to use keys (`setUserAuthenticationRequired`).
- Biometric / device-credential gate before mesh unlock.

**Known weakness:** Rooted or compromised app process can extract the master key and decrypt `.secrets/*.enc` — tracked as **SD-120** in [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md).

---

## iOS (in monorepo — not ready)

**Implementation status:** BLE host scaffolding exists under [`ios/Mycelium/`](../ios/Mycelium/). A Keychain helper (`StorageKeyHelper.swift`) stores the 32-byte master key with `AfterFirstUnlockThisDeviceOnly`. The iOS app is still a scaffold — not TestFlight/App Store ready — and identity keys are not Secure Enclave–bound.

**Secure Enclave:** No plan yet for storing Ed25519/X25519 material in Secure Enclave–backed Keychain items (`kSecAttrTokenIDSecureEnclave` / CryptoKit `SecureEnclave.P256.Signing`). Required work for App Store–grade posture.

---

## Desktop

**Implementation:** `SecretVault` prefers OS keyring ([`at_rest.rs`](../crates/mycelium-core/src/at_rest.rs)); refuses weak file-only creation on fresh installs when keyring is unavailable (**SD-131**).

Desktop is **not** the Play/App Store launch blocker, but theft + unlocked session still exposes keys in RAM.

---

## Gap vs store launch requirements

Before **Google Play** or **Apple App Store** production launch (not sideload beta), the following are **open** unless explicitly accepted as risk:

| Requirement | Android today | iOS today | Target |
|---------------|---------------|-----------|--------|
| Master key not plain SharedPreferences | ✅ EncryptedSharedPreferences | ✅ Keychain (this-device-only) | Keep + SE wrap later |
| Identity keys non-exportable from app sandbox | ❌ File vault + sw key | ❌ N/A | Keystore / Secure Enclave key handles |
| Hardware backing (TEE / StrongBox / SE) | Partial (StrongBox requested for prefs MasterKey) | ❌ master in Keychain, not SE | Where device supports it |
| User auth to unlock keys | ❌ | ❌ | Biometric or PIN gate |
| Root/jailbreak degraded mode | ❌ Documented only (SD-120) | ❌ | Detect + warn or refuse |
| Supply-chain integrity | ✅ `release_integrity` | ✅ Same | Keep separate from user keys |

**Beta / sideload:** Current stack is acceptable for **trusted testers** who understand device compromise risk. It is **not** sufficient to claim “hardware-backed keys” in marketing or threat-model prose.

---

## Recommended implementation path (pre-store)

1. **Android:** Replace software-only master key with Android Keystore–generated AES key (or wrap master key in Keystore); prefer StrongBox when available; optional `setUserAuthenticationRequired` for unlock.
2. **Android (stretch):** Store long-term identity keys as Keystore key pairs where API allows, or use [Android Keystore AES-GCM](https://developer.android.com/privacy-and-security/keystore) to wrap `SecretVault` master only — still better than prefs hex.
3. **iOS:** Keychain wrapper exists; next is Secure Enclave–backed keys for signing where feasible.
4. **Rust:** Keep `SecretVault` API (`storage_key: Option<[u8; 32]>`); hosts supply key material from secure hardware APIs — no change to protocol crates required for v1.
5. **Docs:** Update [ROADMAP.md](./ROADMAP.md) “Keys at rest — Done” to “Partial (beta)” after review; align [THREAT_MODEL.md](./THREAT_MODEL.md) §4.1 and §5.3 with this file.

---

## How to read THREAT_MODEL.md with this document

| THREAT_MODEL claim | Actual status |
|--------------------|---------------|
| “OS keyring / **Android Keystore** (`storage_key`)" (§4.1) | Keystore encrypts **prefs**, not identity keys directly — see Android section above |
| “**Keystore-bound** keys without unlock” (§5.3 limitations) | **Overstated** for theft scenario; treat as **planned**, not shipped |
| Diagram node `Vault[At-rest vault / Keystore]` | Vault = ChaCha20 files; Keystore involvement is **indirect** on Android only |

When assessing nation-state or forensic theft scenarios, assume **keys are extractable** from a compromised or rooted device until hardware binding ships.

---

## Code references

```text
User key at rest (Rust):     crates/mycelium-core/src/at_rest.rs
Android master key (Kotlin): android/.../StorageKeyHelper.kt
Node wiring:                 android/.../MeshService.kt → storageKeyHex
Build integrity (NOT user):  crates/mycelium-core/src/release_integrity.rs
Mini-app build attestation:  crates/mycelium-miniapp/src/reproducible_build.rs
Security debt:               SD-120, SD-121, SD-131
```
