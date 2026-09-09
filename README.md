# Mycelium

> **Public Technical Beta — Feature Development Frozen, Release Validation Ongoing** (2026-08-29). Feature development is intentionally paused. P0 stabilization continues. Hardware validation is **partial** (Pixel 6 only). This is **not** a security audit or certification. The public release gate has **not** been approved. Policy: [docs/DEVELOPMENT_FREEZE.md](docs/DEVELOPMENT_FREEZE.md). Limitations: [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md).

> **Expect bugs.** APIs and wire formats should not change without a documented freeze exception. Security posture is **preview-grade**.

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE)

**Mycelium** is a decentralized, delay-tolerant mesh messenger for **LAN and nearby-device** coordination when the internet is slow or down — neighbors, field crews, and technical early adopters.

**This is an early public technical beta, not a replacement for Signal, emergency services, or operational security training.** Call your local emergency number when life or property is at risk. Do **not** use Mycelium as primary comms in a hostile country or against targeted surveillance. Read [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md) and [docs/BETA_SECURITY_ASSESSMENT.md](docs/BETA_SECURITY_ASSESSMENT.md) before relying on it.

**Hardware validation matrix:** **Pixel 6 partial** (HV-01–05, HV-12 short, HV-13, HV-16). All other rows remain unrun. Do not treat “Android 8+” as a tested support list. Details: [docs/HARDWARE_TEST_MATRIX.md](docs/HARDWARE_TEST_MATRIX.md).

| Scenario | Honest fit ([assessment](docs/BETA_SECURITY_ASSESSMENT.md)) |
|----------|-------------------------------------------------------------|
| LAN / internet outage among trained neighbors | **Limited** |
| Disaster or censorship via the default Internet relay | **Limited** / **No** |
| Hostile country or targeted surveillance | **No** |

MeshCoin (MXC) in the CLI/API is an **experimental preview**, not money. WASM mini-apps use wasmtime 36 when `runtime: "wasm"`; many bundles still run in a WebView. The loopback HTTP API requires a bearer token printed at startup (`MYCELIUM_API_TOKEN` to pin it).

- Beta website: [mycelium-beta.vercel.app](https://mycelium-beta.vercel.app)
- Public relay source: [mycelium-relay](https://github.com/mycena-project/mycelium-relay)

**the maintainer family:** [docs/ECOSYSTEM.md](docs/ECOSYSTEM.md) (Mycelium + relay + web).

## Requirements

### Android (primary beta target)

| Requirement | Detail |
|-------------|--------|
| **OS** | Android 8.0 (API 26) or newer (**intended** floor; **not** freeze-tested) |
| **Network** | Wi‑Fi and/or mobile data for optional relay bootstrap; LAN mesh is **intended** to work offline (**not** freeze-matrix verified) |
| **Permissions** | Internet, notifications, optional camera (QR), location (BLE bootstrap, opt-in), Bluetooth (nearby discovery, opt-in) |

See [android/README.md](android/README.md) for build instructions.

**Future iOS:** native Swift shell over the same UniFFI contract — groundwork only; see [docs/MOBILE_PLATFORMS.md](docs/MOBILE_PLATFORMS.md) and [ios/README.md](ios/README.md).

### Desktop (Tauri)

macOS or Linux with Rust toolchain. Experimental — same Rust mesh stack, linked **natively** in Tauri (not UniFFI). See [docs/MOBILE_PLATFORMS.md](docs/MOBILE_PLATFORMS.md) for Android vs desktop vs future iOS hosts.

## Install (simplest path)

**Android:** There is **no freeze-approved GitHub Release** for `0.1.75-rc.1` (not tagged, not published).

| Artifact | How to build | Signing | Use |
|----------|--------------|---------|-----|
| **Development APK** | `./android/build.sh` / `make android-apk` | Android debug key | Developers only |
| **Technical Beta APK** | `./scripts/build-technical-beta-apk.sh` | Local Technical Beta keystore | Offline / phone-to-phone / freeze hardware |
| **Play AAB** | `android-release-play.yml` | Play upload key | Out of this freeze |

This Technical Beta artifact is for offline testing and is **not** the Play Store artifact. Hashes: [docs/TECHNICAL_BETA_ARTIFACT.md](docs/TECHNICAL_BETA_ARTIFACT.md). Candidate notes: [docs/RELEASE_CANDIDATE.md](docs/RELEASE_CANDIDATE.md).

```bash
./scripts/build-technical-beta-apk.sh
adb install -r artifacts/technical-beta/mycelium-0.1.75-technical-beta-android.apk
```

CI / `./android/build.sh` still produce a **debug-signed Development APK**. That is **not** the same signing key as Technical Beta. Phone-to-phone share will refuse a debug install.

**Desktop (experimental):** build locally (`cargo tauri build -p mycelium-desktop`); there is **no** freeze-published desktop bundle. GitHub Release desktop artifacts, if any, are **not** freeze-certified.

> **License (AGPL-3.0-or-later):** Mycelium is free software. If you modify and run it (or a [bootstrap relay](https://github.com/mycena-project/mycelium-relay)) as a **network service**, you must offer corresponding source to users. See [LICENSE](LICENSE) and [RELEASING.md](RELEASING.md).

**CLI (developers):**

```bash
# Interactive node + HTTP API on :7760 (loopback, bearer token printed at start)
cargo run -p mycelium-cli -- --api-port 7760

# Headless server (API only, no stdin)
cargo run -p mycelium-cli -- --headless --api-port 7760

# Groups / panic wipe (offline). Wallet is experimental preview — not money.
cargo run -p mycelium-cli -- group create "Neighborhood"
cargo run -p mycelium-cli -- panic --yes --db .mycelium-node
```

Start two terminals on the same LAN, exchange peer IDs, then `/chat <peer_id> hello` or `/broadcast mycelium/chat hello`.

## Architecture (short)

```
┌─────────────┐   Noise + Ed25519    ┌─────────────┐
│  Mycelium   │◄──── libp2p ────────►│  Mycelium   │
│   Node      │   gossip / direct    │   Node      │
└──────┬──────┘                      └──────┬──────┘
       │         store-and-forward          │
       │    (sled, TTL, hop limits)         │
       ▼                                    ▼
┌──────────────────────────────────────────────────┐
│  Mesh layer: mDNS (LAN), optional Internet relay, BLE (opt-in; **not hardware-verified**), QR  │
│  Bootstrap: mycelium-relay.fly.dev (optional)    │
└──────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────┐
│  Crypto: X25519 + ChaCha20-Poly1305 (E2E chat)   │
│  Ed25519 message auth · groups use PSK           │
│  Keys at rest: Android Keystore / OS keyring       │
└──────────────────────────────────────────────────┘
```

- **Mesh layer** — libp2p with scoped gossip, bloom anti-entropy, reputation, and bounded relay fanout. BLE is opt-in and **not hardware-verified** ([KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md)). See [docs/network-hardening.md](docs/network-hardening.md) and [docs/DISCOVERY.md](docs/DISCOVERY.md).
- **Crypto** — direct messages encrypted when keys are known; signatures on envelopes. Partial forward secrecy via v1 Double Ratchet for 1:1 after prekey exchange; v0/groups remain static-key. See [docs/SECURITY.md](docs/SECURITY.md).
- **Store-and-forward** — messages persist locally (sled) with TTL and priority; nodes relay for peers when paths exist.

Deeper design: [docs/architecture.md](docs/architecture.md) · [docs/CONNECT.md](docs/CONNECT.md)

## Contributing

**Feature development freeze:** new features, new transports, and major architecture are **deferred**. Stabilization, hardware validation, and release-gate work continue. The public release gate has **not** passed. See [docs/DEVELOPMENT_FREEZE.md](docs/DEVELOPMENT_FREEZE.md) and [CONTRIBUTING.md](CONTRIBUTING.md). Bug reports, security reports, docs corrections, and freeze-allowed P0/security/build fixes are welcome.

**Security:** report vulnerabilities to **security@example.invalid** — not via public issues. See [SECURITY.md](SECURITY.md).

## License

Copyright (C) 2026 Mycelium Project.

Licensed under [GNU Affero General Public License v3.0 or later](LICENSE) (AGPL-3.0-or-later). Network users interacting with modified versions over a network may request corresponding source per the license.

## More documentation

| Topic | Doc |
|-------|-----|
| User-facing security | [docs/security-for-users.md](docs/security-for-users.md) |
| Known limitations | [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md) |
| Feature freeze (policy; not G10) | [docs/DEVELOPMENT_FREEZE.md](docs/DEVELOPMENT_FREEZE.md) |
| G10 evaluation (**not approved**) | [docs/FINAL_DEVELOPMENT_FREEZE.md](docs/FINAL_DEVELOPMENT_FREEZE.md) |
| Release candidate | [docs/RELEASE_CANDIDATE.md](docs/RELEASE_CANDIDATE.md) |
| Public repository audit | [docs/PUBLIC_REPOSITORY_AUDIT.md](docs/PUBLIC_REPOSITORY_AUDIT.md) |
| Release freeze (gates) | [docs/RELEASE_FREEZE_PLAN.md](docs/RELEASE_FREEZE_PLAN.md) |
| MVP smoke test | [docs/mvp-smoke-test.md](docs/mvp-smoke-test.md) |
| Open security debt | [docs/SECURITY_DEBT_REGISTER.md](docs/SECURITY_DEBT_REGISTER.md) |

## CI

Rust: [`.github/workflows/ci.yml`](.github/workflows/ci.yml) · Android: [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml) · **Release (tag `v*.*.*`):** [`.github/workflows/release.yml`](.github/workflows/release.yml) — APK + desktop + SHA256SUMS. See [RELEASING.md](RELEASING.md).

## Docker (tests without local Android SDK)

```bash
docker compose --profile rust run --rm rust-tests
make docker-android-ci && ./scripts/docker-android-build.sh
```

See [docker-compose.yml](docker-compose.yml).
