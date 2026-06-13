# Mycelium

> **Early Beta — expect bugs.** APIs, wire formats, and security posture may change without notice. Read [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md) before relying on Mycelium in high-risk situations.

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE)

**Mycelium** is a decentralized, delay-tolerant mesh messenger for people who need to stay connected when the internet is slow, censored, or gone — community responders, travelers in remote areas, and neighbors coordinating during outages.

**This is not a replacement for emergency services.** Call your local emergency number when life or property is at risk.

- Beta website: [mycelium-beta.vercel.app](https://mycelium-beta.vercel.app)
- Public relay source: [mycelium-relay](https://github.com/mycena-project/mycelium-relay)

**the maintainer family:** [docs/ECOSYSTEM.md](docs/ECOSYSTEM.md) (Mycelium + relay + web).

## Requirements

### Android (primary beta target)

| Requirement | Detail |
|-------------|--------|
| **OS** | Android 8.0 (API 26) or newer |
| **Network** | Wi‑Fi and/or mobile data for relay bootstrap; LAN mesh works offline |
| **Permissions** | Internet, notifications, optional camera (QR), location (BLE bootstrap, opt-in), Bluetooth (nearby discovery, opt-in) |

See [android/README.md](android/README.md) for build instructions.

### Desktop (Tauri)

macOS or Linux with Rust toolchain. Experimental — same mesh stack via FFI.

## Install (simplest path)

**Android:** download the latest debug APK from [GitHub Actions → Android CI](https://github.com/mycena-project/Mycelium/actions/workflows/android-ci.yml) artifacts, or build locally:

```bash
./android/build.sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

**CLI (developers):**

```bash
cargo run -p mycelium-cli
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
│  Mesh layer: mDNS (LAN), relay circuit, BLE, QR  │
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

- **Mesh layer** — libp2p with scoped gossip, bloom anti-entropy, reputation, and bounded relay fanout. See [docs/network-hardening.md](docs/network-hardening.md) and [docs/DISCOVERY.md](docs/DISCOVERY.md).
- **Crypto** — direct messages encrypted when keys are known; signatures on envelopes. No forward secrecy yet. See [docs/SECURITY.md](docs/SECURITY.md).
- **Store-and-forward** — messages persist locally (sled) with TTL and priority; nodes relay for peers when paths exist.

Deeper design: [docs/architecture.md](docs/architecture.md) · [docs/CONNECT.md](docs/CONNECT.md)

## Contributing

We welcome bug reports, docs fixes, and focused PRs. See [CONTRIBUTING.md](CONTRIBUTING.md).

**Security:** report vulnerabilities to **security@example.invalid** — not via public issues. See [SECURITY.md](SECURITY.md).

## License

Copyright (C) 2026 Mycelium Project.

Licensed under [GNU Affero General Public License v3.0 or later](LICENSE) (AGPL-3.0-or-later). Network users interacting with modified versions over a network may request corresponding source per the license.

## More documentation

| Topic | Doc |
|-------|-----|
| User-facing security | [docs/security-for-users.md](docs/security-for-users.md) |
| Known limitations | [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md) |
| MVP smoke test | [docs/mvp-smoke-test.md](docs/mvp-smoke-test.md) |
| Open security debt | [docs/SECURITY_DEBT_REGISTER.md](docs/SECURITY_DEBT_REGISTER.md) |

## CI

Rust: [`.github/workflows/ci.yml`](.github/workflows/ci.yml) · Android: [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml)

## Docker (tests without local Android SDK)

```bash
docker compose --profile rust run --rm rust-tests
make docker-android-ci && ./scripts/docker-android-build.sh
```

See [docker-compose.yml](docker-compose.yml).
