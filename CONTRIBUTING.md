# Contributing to Mycelium

Thank you for helping improve Mycelium. This project is in **early public beta** — expect rough edges, incomplete docs, and fast-moving APIs.

## Before You Start

- Read [README.md](README.md) for scope and architecture
- Read [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md) before proposing security-critical changes
- **Security vulnerabilities:** do **not** file a public issue — email **security@example.invalid** (see [SECURITY.md](SECURITY.md))

## Reporting Issues

Use GitHub Issues with the right category:

| Type | Use when |
|------|----------|
| **Bug** | Reproducible defect in app, node, or build |
| **Feature** | New capability or UX improvement |
| **Security** | **Never public** — use [SECURITY.md](SECURITY.md) |

Include: platform (Android version / desktop OS), commit or release, steps to reproduce, expected vs actual behavior, and logs if safe to share.

## Development Setup

### Rust workspace

```bash
cargo fmt --all
cargo clippy --workspace --all-targets -- -D warnings
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

Reference issues in the body when applicable (`Fixes #123`).

## Pull Requests

1. Fork and branch from `main`
2. Keep PRs focused — one logical change per PR when possible
3. Ensure CI is green (fmt, clippy, tests; Android CI if you touched FFI or Android)
4. Describe **what** and **why**; note user-visible risk (low / medium / high)
5. AGPL-3.0-or-later applies to contributions — you grant rights needed to distribute under the project license

Reviewers may ask for tests or documentation updates. We appreciate patience during beta.

## Relay Repository

The public bootstrap relay is maintained in [mycelium-relay](https://github.com/mycena-project/mycelium-relay). Relay-only changes may belong there; app/node changes here.

## Questions

Non-security questions: open a GitHub Discussion or Issue. Security: **security@example.invalid** only.
