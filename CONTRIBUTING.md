# Contributing to Mycelium

This project is in **Public Technical Beta — Feature Development Frozen, Release Validation Ongoing** ([docs/DEVELOPMENT_FREEZE.md](docs/DEVELOPMENT_FREEZE.md)). Feature development is paused; P0 stabilization continues. Hardware validation is incomplete. G9 is **FAIL**. The public release gate (G10) is **not approved**. It is an **early** beta, maintained by a **small team**. Expect bugs. It is **not** a Signal replacement, **not** a security certification, and is **not** suitable as primary comms against nation-state adversaries — read [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md) and [docs/BETA_SECURITY_ASSESSMENT.md](docs/BETA_SECURITY_ASSESSMENT.md) first.

**Feature freeze:** new features, new transports, major architecture, and experimental product work are **deferred**. Allowed: security fixes, P0 crashes, release-breaking and build defects, documentation corrections, hardware-validation evidence. Dependency upgrades, refactors, and API changes need **written justification**. Direct pushes to `main` remain allowed for a small team; freeze exceptions should be described in the commit/PR body.

Force-push and deleting `main` stay blocked. Prefer a pull request for larger or security-sensitive changes; CI (`pr-gate`, `fuzz-gate`) still runs on push/PR.

## Before You Start

- Read [README.md](README.md) for scope and architecture
- Read [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md) before proposing security-critical changes
- **Security vulnerabilities:** do **not** file a public issue — email **security@example.invalid** (see [SECURITY.md](SECURITY.md))

## Reporting Issues

Use GitHub Issues with the right category:

| Type | Use when |
|------|----------|
| **Bug** | Reproducible defect in app, node, or build |
| **Feature** | **Deferred during freeze** — do not expect merge unless it is a freeze exception |
| **Security** | **Never public** — use [SECURITY.md](SECURITY.md) |

Include: platform (Android version / desktop OS), commit or release, steps to reproduce, expected vs actual behavior, and logs if safe to share.

## Development Setup

### Rust workspace

```bash
cargo fmt --all
cargo clippy --workspace --all-targets -- -D warnings
./scripts/check-layer-boundaries.sh
cargo test --workspace
```

Docker (no local Android SDK): see [README.md](README.md#docker-tests-without-local-android-sdk).

### Android

See [android/README.md](android/README.md).

## Code Style

- **Rust:** `rustfmt` defaults; `clippy -D warnings` must pass
- **Kotlin:** match existing Compose patterns; no wildcard imports
- **Svelte / desktop UI:** keep components small; follow existing file layout under `crates/mycelium-desktop/ui/`

## Commit Conventions

Use clear, imperative subjects:

```
feat(android): add contact request badge
fix(node): drop unsigned direct after grace period
docs: clarify relay bootstrap in README
chore(ci): bump gitleaks action
```

Keep commits reviewable (prefer one logical change per PR). Reference issues in the body when applicable (`Fixes #123`).

## Pull Requests

1. Branch from `main` (fork if you are an external contributor)
2. Keep PRs focused — one logical change per PR when possible
3. Ensure CI is green (fmt, clippy, tests, fuzz PR smoke; Android CI if you touched FFI or Android)
4. Describe **what** and **why**; note user-visible risk (low / medium / high)
5. AGPL-3.0-or-later applies to contributions — you grant rights needed to distribute under the project license
6. Do not merge your own PR if a second reviewer is available

Reviewers may ask for tests or documentation updates. We appreciate patience during beta.

## Relay Repository

The public bootstrap relay is maintained in [mycelium-relay](https://github.com/mycena-project/mycelium-relay). Relay-only changes may belong there; app/node changes here.

## Questions

Non-security questions: open a GitHub Discussion or Issue. Security: **security@example.invalid** only.
