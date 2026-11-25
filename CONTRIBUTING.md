# Contributing to Mycena

Mycena is a **public technical beta**. The stabilisation phase (feature freeze from August to November 2026, see [docs/DEVELOPMENT_FREEZE.md](docs/DEVELOPMENT_FREEZE.md)) is complete, and work now follows the [roadmap](docs/ROADMAP.md). Hardware validation is still incomplete. It is an **early** beta, maintained by **one person** (see [GOVERNANCE.md](GOVERNANCE.md)). Expect bugs. It is **not** a Signal replacement, **not** a security certification, and is **not** suitable as primary comms against nation-state adversaries — read [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md) and [docs/BETA_SECURITY_ASSESSMENT.md](docs/BETA_SECURITY_ASSESSMENT.md) first.

**What helps most right now:** bug reports, test results from different phones (see [docs/HARDWARE_TEST_MATRIX.md](docs/HARDWARE_TEST_MATRIX.md)), translations, and documentation fixes. For larger changes, open an issue first so we can check it fits the [roadmap](docs/ROADMAP.md). Dependency upgrades, refactors and API changes need a short written justification in the PR.

Force-push and deleting `main` stay blocked. Prefer a pull request for larger or security-sensitive changes; CI (`pr-gate`, `fuzz-gate`) still runs on push/PR.

## Before You Start

- Read [README.md](README.md) for scope and architecture
- Read [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md) before proposing security-critical changes
- **Security vulnerabilities:** do **not** file a public issue — use [private vulnerability reporting](https://github.com/mycena-project/Mycena/security/advisories/new) (see [SECURITY.md](SECURITY.md))

## Reporting Issues

Use GitHub Issues with the right category:

| Type | Use when |
|------|----------|
| **Bug** | Reproducible defect in app, node, or build |
| **Feature** | Ideas welcome; please check the [roadmap](docs/ROADMAP.md) first and open an issue before writing code |
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
- **Svelte / desktop UI:** keep components small; follow existing file layout under `crates/mycena-desktop/ui/`

## Commit Conventions

Use clear, imperative subjects:

```text
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
5. AGPL-3.0-or-later applies to contributions, and you agree to the [Contributor License Agreement](CLA.md) (one line in your first pull request)
6. Do not merge your own PR if a second reviewer is available

Reviewers may ask for tests or documentation updates. We appreciate patience during beta.

## Relay Repository

The public bootstrap relay is maintained in [mycena-relay](https://github.com/mycena-project/mycena-relay). Relay-only changes may belong there; app/node changes here.

## Questions

Non-security questions: open a GitHub Issue. Security: [private vulnerability reporting](https://github.com/mycena-project/Mycena/security/advisories/new) only.
