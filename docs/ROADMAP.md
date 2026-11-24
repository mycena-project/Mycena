# Mycena Roadmap

**Level 0 mesh (discovery, privacy, scale, emergency):** see **[LEVEL0_ROADMAP.md](./LEVEL0_ROADMAP.md)** — phased plan from the Mac/Android chat milestone toward offline mesh, daily ID rotation, and emergency SOS.

**Work tracker (audit):** see **[WORK_REMAINING.md](./WORK_REMAINING.md)** — categorized human / hardware / software backlog and iteration plan.

**Connectivity modes & disaster mesh:** see **[CONNECTIVITY_MODES.md](./CONNECTIVITY_MODES.md)** and **[DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md)** — Settings semantics, status labels, desktop BLE Phase 1, DTN Phases 2–4, **Crisis Mode + resource map** ([ADR 008](./adr/008-resource-map-crisis-mode.md)).

## Phase 1 — Kurzfristig (P0)

| # | Thema | Status |
|---|--------|--------|
| 1 | Android Bottom Navigation + Settings-Parität | Done |
| 2 | CLI `/broadcast <scope> <text>` + README | Done |
| 3 | Android-Build in CI (`android-ci.yml`) | Done |
| 4 | Desktop Settings immediate apply + connectivity status | Done |
| 5 | Disaster mesh docs + desktop BLE Phase 1a (stub) | Done — [DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md) |
| 6 | **Crisis Mode + resource map (core + UI)** | Done — [ADR 008](./adr/008-resource-map-crisis-mode.md) |

## Phase 2 — Mittelfristig

| # | Thema | Status |
|---|--------|--------|
| 4 | Keys at rest (EncryptedSharedPreferences / Keychain) | **Partial (beta)** — see [SECURE_HARDWARE_STATUS.md](./SECURE_HARDWARE_STATUS.md); not store-launch grade |
| 5 | Integrationstests (at-rest, panic wipe, E2E envelope) | Done |
| 6 | E2E-Envelope nach Decrypt fixen | Done |
| 7 | Endnutzer-Security-Doc | Done (`docs/security-for-users.md`) |

## Später — TODOs

| ID | Thema | Beschreibung |
|----|--------|----------------|
| **TODO-8** | Event-getriebene UI | Done — Tauri `chat-updated`/`mail-updated`/…; Android `MeshEventBus` |
| **TODO-9** | Android E2E-UX | Done — `EncryptionBadge` + `sendChatEncrypted` in `ChatConversationScreen` |
| **TODO-10** | Mini-App-Store strikt | Done — `verify_signature` lehnt fehlende/ungültige Signatur ab |

## Optional (Backlog)

See **[WORK_REMAINING.md](./WORK_REMAINING.md)** for categorized audit (human / hardware / software) and P0–P3 iteration plan.

- **Crisis field metrics** — essential vs background drop counters ([DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md) 2.5.8)
- ~~**Volunteer coordination (phase 2)**~~ — Done — `mycena/volunteer/v1`, Android/Desktop SOS UI ([ADR 008](./adr/008-resource-map-crisis-mode.md))
- **UserTrust follow-ups** — contribution history export, relay badge in SOS screen
- **iOS host (long-term)** — UniFFI Swift shell; see [MOBILE_PLATFORMS.md](./MOBILE_PLATFORMS.md) Phase 2–3
- ~~Forward Secrecy / Double Ratchet~~ — **Done (v1)** — [ADR 004](./adr/004-double-ratchet-e2e.md); v0 fallback retained
- ~~Relay wire parity with node protocol IDs~~ — Done — `mycena_core::protocol` shared by node + relay
- ~~Desktop release runbook (signing, updates)~~ — Done — [RELEASING.md](../RELEASING.md)
- ~~CLI: Groups, Wallet, Panic for headless nodes~~ — Done — `mycena group|wallet|panic`, `--headless`, HTTP API
