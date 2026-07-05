# Pre–public release — open gaps

**Last updated:** July 2026 (audit pass + AR-06 software gaps)  
**Context:** [THREAT_MODEL.md](./THREAT_MODEL.md) · [BETA_SECURITY_ASSESSMENT.md](./BETA_SECURITY_ASSESSMENT.md) · [WORK_REMAINING.md](./WORK_REMAINING.md)

Legend: **DO NOW** · **DOCUMENT** · **EXTERNAL** · **BLOCKS PLAY** · **DONE**

---

## Release & compliance

| Gap | Status | Notes |
|-----|--------|-------|
| Privacy policy web URL | **DO NOW** after push | Enable Pages → `https://example.invalid/Mycelium/privacy-policy.html` |
| Play Console Data Safety | **DO NOW** (human) | [PLAY_CONSOLE_SUBMIT_CHECKLIST.md](./PLAY_CONSOLE_SUBMIT_CHECKLIST.md) |
| Relay no-IP-retention ops | **DOCUMENT** | [RELAY_OPERATOR_GUIDE.md §9](./RELAY_OPERATOR_GUIDE.md#9-logging-privacy-and-audit-sd-003-sd-210) |
| Git commit + push | **DONE** (this pass) | — |
| CI privacy sync check | **DONE** | `.github/workflows/ci.yml` |
| Residual waiver template | **DONE** | [ACCEPTED_SECURITY_RESIDUALS.md](./ACCEPTED_SECURITY_RESIDUALS.md) |

---

## Security review

| Gap | Status | Notes |
|-----|--------|-------|
| External security review | **EXTERNAL** | Package: [EXTERNAL_SECURITY_REVIEW.md](./EXTERNAL_SECURITY_REVIEW.md) |
| Red-team mini-apps | **EXTERNAL** | — |
| Metadata / Sybil residuals | **DOCUMENT** | [ACCEPTED_SECURITY_RESIDUALS.md](./ACCEPTED_SECURITY_RESIDUALS.md) |

---

## Engineering hardening (this pass)

| Item | Status |
|------|--------|
| SD-003 minimal `/health` JSON | **DONE** | `mycelium-relay` |
| SD-002 balance query default off | **DONE** | `CoinNode::respond_to_balance_queries = false` |
| CLI `mycelium_core` import | **DONE** | → `mycelium_l0_facade::privacy_shaping` |
| Mutex poison recovery + tests | **DONE** | — |
| Threat model + policy + Pages workflow | **DONE** | — |

---

## Play beta gate

**Conditional OK** per [BETA_SECURITY_ASSESSMENT.md](./BETA_SECURITY_ASSESSMENT.md) when:

1. Pages URL live  
2. Data Safety submitted  
3. Residuals signed in [ACCEPTED_SECURITY_RESIDUALS.md](./ACCEPTED_SECURITY_RESIDUALS.md)  
4. External review scheduled (not necessarily complete for closed beta)

**Do not** run activist/journalist outreach until Phase C/D or review sign-off.

---

## Suggested next actions (human)

1. GitHub **Settings → Pages → GitHub Actions** (one-time)  
2. Confirm Pages deploy workflow green after push to `main`  
3. Paste privacy URL + complete Data Safety in Play Console  
4. Email reviewer with [EXTERNAL_SECURITY_REVIEW.md](./EXTERNAL_SECURITY_REVIEW.md)  
5. Sign [ACCEPTED_SECURITY_RESIDUALS.md](./ACCEPTED_SECURITY_RESIDUALS.md)
