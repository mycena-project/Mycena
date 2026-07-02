# Mycelium Roadmap

**Level 0 mesh (discovery, privacy, scale, emergency):** see **[LEVEL0_ROADMAP.md](./LEVEL0_ROADMAP.md)** — phased plan from the Mac/Android chat milestone toward offline mesh, daily ID rotation, and emergency SOS.

## Phase 1 — Kurzfristig (P0)

| # | Thema | Status |
|---|--------|--------|
| 1 | Android Bottom Navigation + Settings-Parität | Done |
| 2 | CLI `/broadcast <scope> <text>` + README | Done |
| 3 | Android-Build in CI (`android-ci.yml`) | Done |

## Phase 2 — Mittelfristig

| # | Thema | Status |
|---|--------|--------|
| 4 | Keys at rest (Keystore / Keychain) | Done |
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

- **iOS host (long-term)** — UniFFI Swift shell; see [MOBILE_PLATFORMS.md](./MOBILE_PLATFORMS.md) Phase 2–3
- Forward Secrecy / Double Ratchet
- Relay-Parität mit Node-Wire-Semantik
- Desktop-Release-Runbook (Signierung, Updates)
- CLI: Groups, Wallet, Panic für Headless-Nodes
