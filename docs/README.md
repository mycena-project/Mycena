# Mycena documentation

Start with the [project README](../README.md). This page groups the documents by
what you want to know. Documents marked *working notes* record the engineering
process of the beta (audits, freeze plans, queues); they are kept for
transparency, not as user documentation.

## Understand the project

| Document | What it covers |
|---|---|
| [architecture.md](architecture.md) | Layers, crates and how a message travels |
| [ECOSYSTEM.md](ECOSYSTEM.md) | The three repositories (app, relay, website) |
| [CONNECTIVITY_MODES.md](CONNECTIVITY_MODES.md) | LAN, Bluetooth, relay and offline modes |
| [DISASTER_MESH_ROADMAP.md](DISASTER_MESH_ROADMAP.md) | Roadmap for disaster use: store-and-forward, crisis mode, field drills |
| [ROADMAP.md](ROADMAP.md) | Overall roadmap |
| [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) | What does not work yet, honestly stated |
| [research-notes.md](research-notes.md) | Lessons from Briar, Serval and other mesh / DTN projects |
| [adr/](adr/) | Architecture decision records (BLE mesh, E2E ratchet, Wi-Fi Aware, …) |

## Security and privacy

| Document | What it covers |
|---|---|
| [THREAT_MODEL.md](THREAT_MODEL.md) | Adversaries, assets, trust boundaries |
| [BETA_SECURITY_ASSESSMENT.md](BETA_SECURITY_ASSESSMENT.md) | Honest fit of the beta per scenario |
| [security-for-users.md](security-for-users.md) | Plain-language guidance for users |
| [SECURITY_ASSUMPTIONS.md](SECURITY_ASSUMPTIONS.md) · [SECURITY_DEBT_REGISTER.md](SECURITY_DEBT_REGISTER.md) · [ACCEPTED_SECURITY_RESIDUALS.md](ACCEPTED_SECURITY_RESIDUALS.md) | Open items and accepted risks |
| [PRIVACY_ROADMAP.md](PRIVACY_ROADMAP.md) | Metadata protection plans |
| [DEPENDENCY_RISK.md](DEPENDENCY_RISK.md) | Third-party advisories and how they are handled |
| [legal/privacy-policy.html](legal/privacy-policy.html) | App privacy policy |

## Mini-apps and offline credits

| Document | What it covers |
|---|---|
| [miniapp-guide.md](miniapp-guide.md) | Writing a mini-app |
| [MINIAPP_SECURITY_ARCHITECTURE.md](MINIAPP_SECURITY_ARCHITECTURE.md) · [MINIAPP_PERMISSION_MODEL.md](MINIAPP_PERMISSION_MODEL.md) | Sandbox and permissions |
| [MESHCOIN_POSITIONING.md](MESHCOIN_POSITIONING.md) | Offline credits research preview — not money, not a cryptocurrency |
| [REGULATORY_DESIGN_NOTES.md](REGULATORY_DESIGN_NOTES.md) | Design constraints for offline credits |

## Build, test, operate

| Document | What it covers |
|---|---|
| [ANDROID_BUILD.md](ANDROID_BUILD.md) | Building the Android app within a 10 GB disk budget |
| [MOBILE_PLATFORMS.md](MOBILE_PLATFORMS.md) | Android, desktop and iOS hosts |
| [HARDWARE_TEST_MATRIX.md](HARDWARE_TEST_MATRIX.md) · [HARDWARE_TEST_RUNBOOK.md](HARDWARE_TEST_RUNBOOK.md) | Device testing |
| [MYCENALAB.md](MYCENALAB.md) | Simulator for city-scale and disaster scenarios |
| [RELAY_OPERATOR_GUIDE.md](RELAY_OPERATOR_GUIDE.md) | Running a bootstrap relay |
| [RELEASE_INTEGRITY.md](RELEASE_INTEGRITY.md) | Reproducible builds and release signing |
| [FUZZING.md](FUZZING.md) | Fuzz targets |

## Working notes (engineering process)

Freeze and release process: [DEVELOPMENT_FREEZE.md](DEVELOPMENT_FREEZE.md),
[FINAL_DEVELOPMENT_FREEZE.md](FINAL_DEVELOPMENT_FREEZE.md),
[RELEASE_FREEZE_PLAN.md](RELEASE_FREEZE_PLAN.md),
[RELEASE_FREEZE_AUDIT.md](RELEASE_FREEZE_AUDIT.md),
[RELEASE_FREEZE_BACKLOG.md](RELEASE_FREEZE_BACKLOG.md),
[POST_FREEZE_BACKLOG.md](POST_FREEZE_BACKLOG.md),
[P0_IMPLEMENTATION_QUEUE.md](P0_IMPLEMENTATION_QUEUE.md),
[WORK_REMAINING.md](WORK_REMAINING.md),
[PRE_RELEASE_GAPS.md](PRE_RELEASE_GAPS.md),
[IMPLEMENTATION_STATUS_2026-07-05.md](IMPLEMENTATION_STATUS_2026-07-05.md),
[PUBLIC_REPOSITORY_AUDIT.md](PUBLIC_REPOSITORY_AUDIT.md).
