# Mycelium — Known Limitations

**Honest user-facing limitations** for public beta.  
Not a marketing document.

---

## NOT SAFE AGAINST

### Network & metadata

| Threat | Why |
|--------|-----|
| **Traffic analysis** | libp2p exposes peers, scopes, timing, sync bloom traffic |
| **ISP / state blocking** | No Tor/Nym transport in release |
| **Relay operator surveillance** | IPs visible at circuit setup; status endpoint public |
| **Global passive adversary** | No padding / cover traffic in production |

### Identity & crypto

| Threat | Why |
|--------|-----|
| **Long-term tracking via PeerId** | Transport PeerId may rotate daily (SD-190 spike); stable account id remains in contact book — not full anonymity |
| **Retroactive decrypt (1:1)** | **Partially mitigated (v1):** Double Ratchet after prekey bundle is cached — via v3 invite ratchet tail or `PeerInfo` ([ADR 004](./adr/004-double-ratchet-e2e.md)). Legacy v2/v3-without-ratchet and v0 fallback remain static X25519 until bundle exchange. |
| **Group QR leak** | Single PSK; no epoch rotation on member leave |
| **Forged unsigned wire messages** | Mitigated (SD-030) — unsigned `Data` dropped; gossip relay bodies exempt |
| **Plaintext chat mistake** | Mitigated (SD-081) — send blocked until enc key exchanged |

### Abuse & availability

| Threat | Why |
|--------|-----|
| **Relay amplification** | Mitigated (SD-031) — relay fanout capped (default 3 peers) |
| **Sybil spam at scale** | No global proof-of-identity |
| **Gossip author confusion** | Mitigated (SD-080) — gossip payloads stored unsigned; no local re-sign |
| **Bulletin illegal content** | Censorship-resistant design; no global moderation |

### Device & platform

| Threat | Why |
|--------|-----|
| **WebView 0-day (mini-apps)** | Chromium in WebView = full app TCB break |
| **Android backup exfiltration** | Mitigated (SD-171) — `allowBackup=false` + backup rules |
| **Desktop weak vault** | Mitigated (SD-131) — new installs require OS keyring; existing file fallback migrated only |
| **Rooted / seized device** | Local sled holds history |

### Economic

| Threat | Why |
|--------|-----|
| **Fake MeshCoin balances** | Sybil witnesses; local ledger only |
| **Scam payment QR** | User can be tricked; caps limit loss not fraud |

---

## THREAT MODEL EXCLUSIONS

We explicitly **exclude** these from beta security claims:

1. Nation-state adversaries with device exploit chains.
2. Legal compulsion to decrypt (we cannot — no central plaintext store).
3. Global Sybil-resistant cryptocurrency.
4. App-store-grade malware review of all sideloaded bundles.
5. Metadata-private messaging comparable to mature MLS + anonymity networks.
6. Guaranteed delivery under partition (partition-tolerant, not CP).
7. Protection when user installs **modified APK** from third party.
8. Safety of **journalists** without operational security training beyond app limits.

---

## EXPERIMENTAL FEATURES

Treat as **preview only**:

| Feature | Risk |
|---------|------|
| **MeshCoin (MXC)** | Sybil witnesses; not legal tender; caps not insurance |
| **Mini-app store / sideload** | HTML/JS; bridge to wallet & messaging |
| **Bulletin boards** | Public scopes; content unmoderated |
| **BLE / WiFi Direct mesh** | Platform-specific leaks; experimental paths |
| **Shade / privacy experiments** | SD-070 padding+batching shipped **experimental, off by default** — see [PRIVACY_SHAPING_BENCHMARK.md](./PRIVACY_SHAPING_BENCHMARK.md) |
| **WASM mini-apps** | Rejected at install; executor not built |
| **Curator revocation publish** | Any node key can publish; no allowlist yet |

---

## HIGH-RISK USER WARNINGS

**Do not rely on Mycelium beta for:**

- Life-threatening situations without backup comms (SMS, satellite, in-person).
- Evading state surveillance **solely** via this app.
- Storing wealth in MXC.
- Whistleblowing without **device hygiene** (no backup, no plaintext chat, verify keys).
- Installing mini-apps from unknown developers.

**If you are an activist or journalist:**

- Assume **account id + transport PeerId + IP + timing** are exposed to relay and local network observers.
- Use **encrypted chat only** after verifying enc key exchange — app refuses send without peer enc key.
- Do not share group invites in public channels.
- Prefer **LAN-only** mode if Internet bootstrap is untrusted.
- Plan **panic wipe** before field use; assume phone seizure reveals local DB.

**If you are a protest organizer:**

- Expect **spam and amplification** mitigations active (SD-030/031); Sybil scale remains open.
- MeshCoin tips are **not** settlement for vendors at scale.

**If you are in disaster zone / internet outage:**

- **Good fit (with caveats):** LAN mesh, bulletin, offline-first *after* peers joined; **Android BLE pocket mesh** (F1–F3) when Wi‑Fi/cell absent.
- **Caveats:** Still need initial peer discovery; relay not required on LAN; battery cost; **Mac/laptop BLE not active yet** ([DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md)); city-wide hundreds of hops **not supported**.

---

## DOCUMENTED PARTIAL MITIGATIONS

| Area | What works | What does not |
|------|------------|---------------|
| Mini-apps | Network block, sessions, caps, safe mode, bundle scan, Android isolated process, strict CSP (no inline scripts), sideload requires `mycelium-bundle.sig.json` | WebView CVE class |
| Chat E2E | Encrypted path when keys present; send blocked without peer enc key | Metadata still visible |
| Install | Listing verify, revocation, downgrade block | Sideload still possible |
| Relay | Circuit limits, blind forward | Single operator, IP visibility |

---

## Operational limitations

- **No 24/7 abuse team** — see [INCIDENT_RESPONSE_RUNBOOK.md](./INCIDENT_RESPONSE_RUNBOOK.md).
- **Cannot delete** messages on other users’ phones.
- **Cannot recover** forgotten keys / wiped data.
- **Relay outage** degrades Internet path; LAN may continue.
- **Mini-app direct messaging** requires the recipient to have **accepted** the sender as a contact; there is no anonymous DM bridge bypass.

---

## Intentionally deferred (not bugs)

These gaps were reviewed and left out of scope because mini-apps or settings already cover the use case, or because shipping them needs product decisions:

| Item | Rationale |
|------|-----------|
| Native Android/desktop proximity UI | The **Proximity** mini-app (`proximity.mxa`) is the supported UX; FFI exists for future native screens. |
| Desktop Settings for scope keys | Encrypted channels are managed in **Android Settings** today; desktop users install mini-apps that declare bulletin scopes. |
| Curated signed store listings in-repo | Bundles (`meshaid.mxa`, `meshmarket.mxa`, `proximity.mxa`) ship for sideload; gossip store curation needs operator keys and policy. |
| `MiniAppRunnerScreen` (legacy WebView) | Superseded by `MiniAppProcessActivity` (isolated process). Legacy path retained for reference only. |
| Full mesh multi-node CI for proximity DM | Unit/integration tests cover bulletin relay, presence ingest, and mutual-match gating; live two-node mesh tests remain in `mycelium-sim` scenarios. |

---

## Comparison claims we refuse to make

- “Signal-level security” — **false** today.
- “Untraceable” — **false**.
- “Bank in your pocket” (MXC) — **false**.
- “App store safe” for all sideloads — **false**.

---

## See also

- [THREAT_MODEL.md](./THREAT_MODEL.md)
- [SECURITY_ASSUMPTIONS.md](./SECURITY_ASSUMPTIONS.md)
- [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md)
- [BETA_SECURITY_ASSESSMENT.md](./BETA_SECURITY_ASSESSMENT.md)
- [security-for-users.md](./security-for-users.md)
- [CONNECTIVITY_MODES.md](./CONNECTIVITY_MODES.md)
- [DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md)
