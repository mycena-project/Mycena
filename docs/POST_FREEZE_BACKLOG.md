# Post-freeze backlog

**Created:** 2026-08-29
**Context:** Policy freeze **APPROVED WITH EXCEPTIONS** ([DEVELOPMENT_FREEZE.md](./DEVELOPMENT_FREEZE.md)). Technical P0 device/CI evidence is still incomplete.

This file holds work that **must not be treated as freeze P0** (plan §2.2 / §4.3): P2 and Play-track items.

**Do not** park freeze P0s here. Those remain in [RELEASE_FREEZE_BACKLOG.md](./RELEASE_FREEZE_BACKLOG.md) as **allowed freeze work** (device proof, CI, honesty), not as feature work.

**Does not replace:** [WORK_REMAINING.md](./WORK_REMAINING.md), [SECURITY_ROADMAP.md](./SECURITY_ROADMAP.md), [DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md), [PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md).

---

## Freeze-blocking evidence (allowed during freeze; not features)

Still current queue in RELEASE_FREEZE_BACKLOG — **not** deferred as P2:

- Hardware matrix HV-* / G2–G6 device records (RFB-HW-01, NET-02, PERS-*)
- Two-phone share (RFB-P2P-*, APK-*, G3)
- Secret-scan URL (RFB-SEC-04)
- CI evidence (RFB-TEST-01)
- G9 tag/publish/install (RFB-REL-02) after P0s
- §11 / BLE honesty (RFB-DOC-01, DOC-03, DOC-05)
- Panic device proof (RFB-PERS-01)

---

## P2 / deferred product (plan §4.3)

| Theme | Notes | Tracker |
|-------|--------|---------|
| iOS shipping host / App Store | Groundwork only | MOBILE_PLATFORMS, ios/README |
| Tor / Nym / pluggable transports | SD-180 | SECURITY_ROADMAP |
| MLS / group epoch rotation | SD-101, SD-242 | SECURITY_DEBT_REGISTER |
| Hardware-bound identity keys, StrongBox-only, biometric, root detect | SD-120 | SECURE_HARDWARE_STATUS |
| Metadata padding / Shade on by default | SD-070 experimental | SECURITY_ROADMAP |
| Multi-operator signed bootstrap as must-ship | SD-022 / SD-220 | residuals R6/R8 |
| Extra mini-app red-team | Beyond existing results | WORK_REMAINING Cat 1 |
| 520-node soak; 10–50 node neighborhood drill | | DISASTER_MESH_ROADMAP |
| Wi-Fi Aware / WebRTC / NFC **field** validation | | WORK_REMAINING Cat 2 |
| Auto GPS geohash | | AR-06 / WORK_REMAINING |
| MeshCoin BFT, faucet-as-money, hardware wallets | Preview MXC stays | MESHCOIN_POSITIONING |
| Byte-identical APK zip across Gradle hosts | | RELEASE_INTEGRITY §7 |
| Desktop Tauri reproducible builds | | RELEASE_INTEGRITY §7 |
| Play Store listing, Data Safety, Pages privacy URL | Separate track | PRE_RELEASE_GAPS |
| OSS-Fuzz submission | Needs public repo | CI_PUBLIC_TRANSITION |
| Non-critical i18n | | — |
| Native proximity UI | Proximity mini-app is UX | KNOWN_LIMITATIONS |
| cargo vet | Optional; SD-150 honesty is freeze P1 | DEPENDENCY_RISK |
| Share-as-FGS | Not a freeze feature | RFB-P2P-09 docs choice |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Do not “fix” Samsung with this | SAMSUNG_STABILITY_AUDIT |

---

## P1 accepted-as-deferred (GitHub freeze vs Play)

These do **not** justify pausing development today. After a real G10 they stay here unless product pulls Play into scope:

| Item | Disposition | Date |
|------|-------------|------|
| External security review | Deferred for GitHub-only beta; not scheduled | 2026-08-29 |
| Play Console Data Safety | Deferred unless Play added to G10 | Plan §2.3 |
| Privacy policy GitHub Pages | Play **DO NOW**; GitHub freeze may use in-app + `docs/legal/` | PRE_RELEASE_GAPS |
| Residuals R1–R10 unsigned | GitHub-only freeze-accepted; Play still needs names | ACCEPTED_SECURITY_RESIDUALS |
| Samsung hardware | Public “untested”; not a G4 pass | KNOWN_LIMITATIONS |

---

## After this policy freeze

1. Keep WORK_REMAINING Category 1–2 and SECURITY_ROADMAP Phases 1–5 as living lists.
2. Apply [DEVELOPMENT_FREEZE.md](./DEVELOPMENT_FREEZE.md) / plan §7: no new features.
3. Un-freeze only as a conscious roadmap increment.

**Do not** start the P2 table above to “finish the freeze.” Device/CI P0s in RELEASE_FREEZE_BACKLOG remain in scope as allowed freeze work.
