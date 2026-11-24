# Security Policy

## Reporting a Vulnerability

**Do not** open public GitHub issues for security vulnerabilities.

Report them privately to **github.com/mycena-project/Mycena/security/advisories/new** with:

- affected component (app, relay, mini-app runtime) and version or commit hash
- clear reproduction steps or proof-of-concept
- impact assessment (confidentiality, integrity, availability)

We aim to acknowledge receipt within **72 hours**, triage severity, and coordinate a responsible disclosure timeline. We may request additional information or offer a coordinated disclosure date.

Preferred channels (in order):

1. Email: **github.com/mycena-project/Mycena/security/advisories/new** (guaranteed private path)
2. GitHub Security Advisories: **Security → Report a vulnerability** on this repository — **only if** private vulnerability reporting is enabled in repository settings. If that form is missing, use email.

## Scope

In scope:

- Mycena Android app, desktop client, and Rust workspace crates
- Default public relay (`mycena-relay`) when operated from the companion repository
- Cryptographic handling, mesh transport, store-and-forward, and mini-app sandbox

Out of scope:

- Third-party dependencies (report upstream; we still appreciate heads-up)
- Social-engineering attacks against individual users
- Denial-of-service at scale against public relay infrastructure (report anyway; we track abuse)

## Safe Harbor

We support good-faith security research. Do not access data that is not yours, degrade service for other users, or publicly disclose before we have had a reasonable window to fix.

## Transparency

Known limitations and open security debt are documented publicly:

- [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md)
- [docs/SECURITY_DEBT_REGISTER.md](docs/SECURITY_DEBT_REGISTER.md)
- [docs/BETA_SECURITY_ASSESSMENT.md](docs/BETA_SECURITY_ASSESSMENT.md)
- [docs/SECURITY_RELEASE_REVIEW.md](docs/SECURITY_RELEASE_REVIEW.md) — internal G7 freeze review (**not** an external audit)

## License Note

This project is licensed under [AGPL-3.0-or-later](LICENSE). Network-facing modifications must offer corresponding source to users per the license terms.
