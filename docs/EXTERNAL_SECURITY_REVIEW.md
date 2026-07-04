# External security review — engagement package

**Status:** Ready to send to reviewer  
**Threat model version:** [THREAT_MODEL.md](./THREAT_MODEL.md) v1.0 (July 2026)

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
| 1 | Threat model + review checklist | [THREAT_MODEL.md](./THREAT_MODEL.md) §8 |
| 2 | Security debt register (snapshot date: **July 2026**) | [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) |
| 3 | Scenario assessment | [BETA_SECURITY_ASSESSMENT.md](./BETA_SECURITY_ASSESSMENT.md) |
| 4 | Known limitations | [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md) |
| 5 | Privacy policy | [docs/legal/privacy-policy.html](./legal/privacy-policy.html) |
| 6 | User-facing security | [security-for-users.md](./security-for-users.md) |

---

## Test evidence to reproduce

```bash
# Production unwrap/expect gate
./scripts/check-no-unwrap-prod.sh

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
| Jul 2026 | Initial engagement package |
