# Mini-App Red-Team Results (June 2026)

**Exercise:** Automated + static verification against [MINIAPP_ATTACK_SCENARIOS.md](./MINIAPP_ATTACK_SCENARIOS.md)  
**Build:** post `9730a7a` hardening commit

## Summary

| Scenario | Result | Notes |
|----------|--------|-------|
| AS-01 Trojan sideload | **BLOCKED** | Sideload requires valid `mycelium-bundle.sig.json` (SD-041) |
| AS-02 Listing hash swap | **BLOCKED** | `install_verified` rejects hash mismatch vs cached listing |
| AS-03 Downgrade | **BLOCKED** | Preview blocks downgrade unless user explicitly allows |
| AS-04 XSS inline script | **BLOCKED** | CSP nonce-only; `scan_bundle` rejects inline `<script>` |
| AS-05 Identity harvest | **OPEN** | `identity.get` still ungated (SD-042 follow-up) |
| AS-20 Malicious offline payload | **MITIGATED** | AS-01 fix + bundled official apps ship signed |

## Automated evidence

```bash
cargo test -p mycelium-app --lib install_policy
cargo test -p mycelium-app --lib miniapp_security
cargo test -p mycelium-app bundled_listings
miniapp-sdk lint miniapps/dist/*.mxa   # bundle_signature_ok: true
```

## Manual steps not run in CI

1. Evil.mxa sideload without signature → install rejected (verified via `install_policy` tests).
2. Inline script bundle → `scan_bundle` error (verified).
3. Payment QR forgery → document only; native confirmation UI exists.
4. Signed listing + mismatched bytes → `HashMismatch` trust level blocks verified install.
5. `messaging.broadcast` flood → mitigated by SD-032 rate limits at node ingest.

## Pass criteria (roadmap)

- AS-01, AS-02, AS-04: **fail for attacker** ✓  
- AS-05, AS-06, AS-07: **still open** — track under SD-042 / bridge hardening  
- Written results: this document

**Next drill:** Q3 2026 after permission UI (SD-042) lands.
