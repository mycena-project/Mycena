# External security review — engagement package

**Status:** **Not commissioned.** This file is a briefing pack, not evidence of a completed audit. **No external security review has occurred.**

**GitHub-only technical beta (2026-08-29):** external review is **explicitly deferred** (owner: Mycelium maintainers). It is **not** scheduled. Play Store / wide public beta still requires scheduling per [PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md). Internal freeze review: [SECURITY_RELEASE_REVIEW.md](./SECURITY_RELEASE_REVIEW.md) (not a substitute for this pack’s signed deliverable).

**Threat model version:** [THREAT_MODEL.md](./THREAT_MODEL.md) **v1.1** (July 2026). Reviewer checklist is **§9** (the freeze plan’s “§8 checklist” is the mapping table in TM §8).

---

## Scope

Pre–public Play Store beta of Mycelium Android client + open-source Rust mesh stack.

**In scope:**

- Android app + UniFFI boundary
- `mycelium-node`, `mycelium-core`, `mycelium-coin`, `mycelium-miniapp`
- Default Internet relay path (transport metadata)
- Mini-app WebView sandbox

**Out of scope (documented residuals):**

- Nation-state device exploit chains
- Global Sybil-resistant cryptocurrency
- Metadata privacy on default settings
- Erasure of mesh replicas on peer devices

See [ACCEPTED_SECURITY_RESIDUALS.md](./ACCEPTED_SECURITY_RESIDUALS.md).

---

## Deliverables for reviewer

| # | Artifact | Path |
|---|----------|------|
| 1 | Threat model + review checklist | [THREAT_MODEL.md](./THREAT_MODEL.md) **§9** (scope/residuals/debt/privacy/asset tests; §8 is ID mapping only) |
| 2 | Security debt register (snapshot date: **2026-08-29**) | [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) |
| 3 | Scenario assessment | [BETA_SECURITY_ASSESSMENT.md](./BETA_SECURITY_ASSESSMENT.md) |
| 4 | Known limitations | [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md) |
| 5 | Privacy policy | [docs/legal/privacy-policy.html](./legal/privacy-policy.html) |
| 6 | User-facing security | [security-for-users.md](./security-for-users.md) |

---

## Test evidence to reproduce

```bash
# Production unwrap/expect gate (clippy deny + INVARIANT whitelist)
./scripts/check-no-unwrap-prod.sh
./scripts/check-invariant-whitelist.sh
./scripts/check-unwrap-audit.sh
```

Allowed production `expect()` sites are listed in [UNWRAP_INVARIANT_WHITELIST.tsv](./UNWRAP_INVARIANT_WHITELIST.tsv) and must cite `// INVARIANT:` in code. New PRs adding unwrap/expect outside tests fail CI unless whitelisted in review.

```bash
# Mutex poison recovery
cargo test -p mycelium-core lock_or_recover_continues
cargo test -p mycelium-node --lib peer_trust_survives
cargo test -p mycelium-node --test mutex_poison_recovery

# Mini-app security tests
cargo test -p mycelium-miniapp

# Voucher double-spend at merge
cargo test -p mycelium-sim voucher_double_spend
```

---

## Questions for reviewer

1. Are [THREAT_MODEL.md](./THREAT_MODEL.md) §5 accepted residuals appropriate for public beta?
2. Any **beta-blocker** rows in [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) still open or regressed?
3. Is Play [Data Safety declaration](./PLAY_CONSOLE_DATA_SAFETY.md) accurate?
4. Mini-app bridge: sufficient for preview, or block release?

---

## Expected output

- Written findings (severity + file/line references)
- Sign-off or list of blockers before Play public beta
- Reference: THREAT_MODEL version + git commit SHA reviewed

---

## Contact

Engineering: GitHub Issues on [the maintainer/Mycelium](https://github.com/mycena-project/Mycelium)
Privacy: privacy@example.invalid

---

## Change log

| Date | Change |
|------|--------|
| 2026-08-29 | Status: still not commissioned; explicitly deferred for GitHub-only beta; TM v1.1; checklist §9 |
| Jul 2026 | Initial engagement package |
