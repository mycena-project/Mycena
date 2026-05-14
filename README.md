# Mycelium

Decentralized, delay-tolerant mesh networking stack (Rust): node, apps, FFI
(Android/Desktop), and simulation tooling.

> **Legal notice:** This repository documents technical capabilities and operating
> guidance. It is not legal advice. **Licensing:** proprietary — see [`LICENSE`](LICENSE).
> The copyright holder may later publish parts under an open-source license; see
> [`RELICENSE-INTENT.md`](RELICENSE-INTENT.md).

## Run (CLI)

```bash
cargo run -p mycelium-cli
```

Start two terminals on the same LAN and exchange peer IDs:

- list known peers: `/peers`
- direct message: `/chat <peer_id> hello`
- scoped broadcast: `/broadcast mycelium/chat hello`

## Smoke test

For a full MVP validation flow (chat, bulletin, mail, REST API), see:

- [`docs/mvp-smoke-test.md`](docs/mvp-smoke-test.md)

## Android

Build, UniFFI binding generation, emulator/device install, and E2E checks:

- [`android/README.md`](android/README.md)

## CI (GitHub Actions)

Primary Rust checks (`fmt`, `clippy -D warnings`, `cargo test`) run on push/PR
(see [`.github/workflows/ci.yml`](.github/workflows/ci.yml)).

Same org hygiene as other private repos: CodeQL (Rust, **no SARIF upload** —
private repos), dependency review on PRs, Gitleaks secret scan, quality gate,
Dependabot (GitHub Actions + Cargo) with a lightweight Dependabot-only `cargo check`
smoke workflow. See [`.github/workflows/`](.github/workflows/).

## Git (eigenständiges privates Repo)

**Kein Monorepo:** Nur dieses Verzeichnis ist das Git-Root (`.git` hier), wie
eure anderen Projekte auch. Remote und Push: [`docs/GIT_PRIVATE_REMOTE.md`](docs/GIT_PRIVATE_REMOTE.md).

## Docker (tests without local Android SDK)

Rust workspace tests + clippy:

```bash
docker compose --profile rust build rust-tests
docker compose --profile rust run --rm rust-tests
```

The image uses a **slim** Rust base (smaller pull than `rust:…-bookworm`); the first `build` is mostly downloading that layer plus `apt` deps for Tauri/GTK. Use `docker compose --progress=plain --profile rust build rust-tests` if you want build logs.

Full Android CI image (downloads SDK inside Docker):

```bash
docker compose --profile android build android-ci
```

See [`docker-compose.yml`](docker-compose.yml) and [`docs/AGENT_CONTINUATION.md`](docs/AGENT_CONTINUATION.md).

## Network hardening

Scoped dissemination, bloom anti-entropy, signing, GC, hop limits, reputation:

- [`docs/network-hardening.md`](docs/network-hardening.md)
