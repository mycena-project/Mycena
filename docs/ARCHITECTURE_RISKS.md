# Mycelium — Architecture Risk Ledger

**Status:** Pre-public beta · living document  
**Last updated:** May 2026  
**Companion:** [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md)

This ledger describes **systemic** risks arising from architectural choices—not individual bugs. It assumes:

- Networks are hostile (LAN, café Wi‑Fi, ISP, relay operator, future state actors).
- Users are malicious (Sybils, spammers, scammers).
- App stores and regulators are adversarial (policy changes, takedowns, data requests).
- Devices will be lost, rooted, seized, and forensically imaged.

---

## 1. Trust boundary map

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         USER DEVICE (TCB: OS + app)                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐ │
│  │ Android UI   │  │ Mini-app     │  │ Rust node    │  │ sled + vault│ │
│  │ Kotlin/FFI   │──│ WebView/iframe│  │ libp2p stack │──│ at-rest     │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └─────────────┘ │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ Noise / QUIC / TCP (metadata visible)
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│              OTHER PEERS (untrusted) + RELAY (semi-trusted infra)        │
│  gossipsub · direct RR · bloom sync · EncryptedDirect forward hops       │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│         INTERNET BOOTSTRAP (trusted-first-use: baked peer IDs)           │
│  mycelium-relay.fly.dev · user paste multiaddrs · mDNS LAN               │
└─────────────────────────────────────────────────────────────────────────┘
```

**What we do NOT trust:** arbitrary peers, bulletin content, mini-app HTML, gossip author strings without signature, witness peer IDs, custom bootstrap pasted by user.

**What we incorrectly trust today:** first `PeerInfo` enc key per peer; unsigned `Data` frames; local re-signatures on gossip ingest; relay reachability probe as “Internet OK.”

---

## 2. Architectural risk matrix

| Risk ID | Domain | Description | Blast radius | Detectability | Residual risk after beta mitigations |
|---------|--------|-------------|--------------|---------------|--------------------------------------|
| AR-01 | **Concentration** | Federated relay registry + client failover (was: single Fly.io bootstrap) | Global Internet path if all listed relays fail | Outage obvious; per-relay failure triggers rotation | **Medium** — mesh LAN still works; production still relies on one primary relay until community relays ship |
| AR-02 | **Amplification** | Store-and-forward without authenticated routing | Network-wide flood | Metrics queue growth | High until SD-031 fixed |
| AR-03 | **Identity binding** | Transport PeerId rotated (cold start + interval); stable account id for contacts; E2E re-announce | Relay/LAN see ephemeral ids per epoch | Passive correlation harder | **Medium** — account id + enc key timing still observable |
| AR-04 | **E2E model** | v1 Double Ratchet + v0 legacy fallback ([ADR 004](./adr/004-double-ratchet-e2e.md)) | Message content | User mistake / pre-bootstrap v0 | Medium |
| AR-05 | **Group model** | One PSK per group in QR | All group history | Invite leak | Critical |
| AR-06 | **Economic layer** | MeshCoin is local-ledger gossip, not BFT | Fake money / scams | User education | Critical if marketed as currency |
| AR-07 | **Mini-app model** | HTML+JS in WebView with bridge | Wallet + messages | Install count | High |
| AR-08 | **Moderation** | Gossip is censorship-resistant by design | Illegal content spread | External report | Legal/reputational |
| AR-09 | **Metadata** | libp2p + sync leak social graph | Targeting | Traffic analysis | High |
| AR-10 | **Operator liability** | Relay sees IPs at transport layer | Subpoena | Hosting logs | Medium |

---

## 3. Failure mode catalog

### 3.1 Confidentiality failures

| Mode | Trigger | Outcome |
|------|---------|---------|
| Plaintext fallback | No `peer_x25519` for recipient | Chat body readable on wire |
| Unsigned Data accepted | Legacy peer / attacker | Forged messages ingested |
| Gossip re-sign | Scoped bulletin path | Wrong author attribution |
| Static E2E keys | Device seizure | Historical decrypt (**v0 / pre-bootstrap only**; v1 sessions mitigated) |
| Group QR leak | Photo / forward | Full group read |

### 3.2 Integrity failures

| Mode | Trigger | Outcome |
|------|---------|---------|
| EncryptedDirect without sig | Pre-fix peers | **Mitigated** — drop |
| Witness Sybil | 3 colluding peers | Fake MXC confirmation |
| Sideload mini-app | Unsigned ZIP | Bridge calls with permissions |
| Bootstrap MITM | Malicious multiaddr | Eclipse + unsigned injection |

### 3.3 Availability failures

| Mode | Trigger | Outcome |
|------|---------|---------|
| Relay outage | Fly.io / DNS / all federated relays | Internet path degraded; client rotates pool |
| Queue flood | SD-030/031 | Battery + storage exhaustion |
| Bloom sync storm | Large id sets | CPU / bandwidth spike |
| Play Store removal | Policy violation | Distribution collapse |

### 3.4 Accountability failures

| Mode | Trigger | Outcome |
|------|---------|---------|
| Permanent PeerId | Any use | Long-term tracking (**mitigated:** transport rotation SD-190; account id remains stable) |
| Display name spoof | Social eng. | User trusts wrong party |
| No forward secrecy | Key leak | Retroactive exposure |

---

## 4. Adversary tiers

| Tier | Capability | Mycelium exposure today |
|------|------------|-------------------------|
| **A0 Script kiddie** | LAN sniffing, spam bots | Unsigned Data, bulletin flood |
| **A1 Scammer** | Fake mini-apps, MXC fraud | Sideload, experimental coin |
| **A2 Organized abuse** | Sybil farms, relay fanout | Witness gaming, SD-031 |
| **A3 State ISP** | Traffic analysis, DNS block | Metadata, censorship |
| **A4 State law enforcement** | Seizure, relay subpoena | Device data, IP at relay |
| **A5 Nation-state** | WebView 0-day, HSM bypass | Full device TCB break |

**Design center of gravity today:** A0–A1 with documentation. **Not yet ready for A4–A5** without substantial roadmap (see SECURITY_ROADMAP).

---

## 5. Component coupling risks

### 5.1 libp2p identity ≡ app identity

Coupling mesh routing keys with MeshCoin signing and envelope signatures means **one compromise loses everything**. No blast-radius separation between transport, coin, and E2E.

**Mitigation direction:** Sub-keys per purpose (HPKE chat key, coin key, device key) with cross-certification.

### 5.2 FFI single process (Android)

Kotlin UI, WebView, and Rust node share one process. WebView escape → full node control.

**Mitigation direction:** Isolated process for mini-apps; IPC bridge.

### 5.3 sled as universal datastore

Messages, coin ledger, mini-app storage, identity paths coexist. Panic wipe must delete correct tree; bug leaves cross-artifact recovery.

**Mitigation direction:** Separate DB namespaces with independent wipe.

### 5.4 Experimental coin in production UI

Wallet screen in release build creates **user expectation of value** while ledger is local-only gossip.

**Mitigation direction:** Feature flag; compile-time testnet; strong UX (done partially).

---

## 6. Scaling risks (security-relevant)

| Scale | Phenomenon | Security impact |
|-------|------------|-----------------|
| 10² users | Random spam | Annoyance |
| 10³ users | Sybil witnesses / bulletin abuse | Integrity breaks |
| 10⁴ users | Relay circuit exhaustion | DoS + censorship |
| 10⁵ users | Law enforcement attention | Subpoenas, store policy |
| 10⁶ users | Nation-state interest | Protocol breaks, metadata |

Architecture currently **O(peers)** fanout on unknown destinations—does not scale securely.

---

## 7. Regulatory & app-store risks

| Risk | Mechanism | Mitigation |
|------|-----------|------------|
| **Illegal content** | Bulletin gossip | TTL; no central hosting; legal review |
| **Unregistered money transmission** | MeshCoin | Preview disclaimer; no fiat rails |
| **Sanctions / export** | Crypto in app | Legal counsel; geo policy |
| **GDPR data controller** | Relay IP processing | Privacy policy; data minimization |
| **Children’s data** | P2P network | Age gate; no targeted collection |

**Hostile app store scenario:** Google rejects update → users stranded on old bootstrap peer IDs (SD-221).

---

## 8. Recovery & resilience architecture gaps

| Capability | Present | Needed |
|------------|---------|--------|
| Remote kill switch | No | Signed config halt |
| Key rotation | Panic wipe only | Live rotation without wipe |
| Multi-relay | Single default | Pool + health |
| Post-incident forensics | Logs local | Privacy-preserving telemetry opt-in |
| User-to-user block | No | Local deny list |

---

## 9. Invariants we want (not yet enforced)

1. **No unsigned app-layer frame** enters ingest after beta.
2. **No plaintext chat** leaves device without explicit two-tap consent.
3. **No mini-app install** without developer signature match.
4. **No forward** beyond authenticated next hop toward `to_peer`.
5. **MeshCoin never implies value** in release builds without BFT roadmap shipped.
6. **Relay operator cannot read message content** (already true for E2E payloads; maintain).

---

## 10. Decision log (architecture)

| Date | Decision | Risk accepted |
|------|----------|---------------|
| 2026-05 | libp2p relay on Fly.io as default bootstrap | AR-01, AR-10 |
| 2026-07 | Federated signed relay registry + pool failover ([RELAY_OPERATOR_GUIDE](./RELAY_OPERATOR_GUIDE.md)) | Reduces AR-01; Sybil caps via curator endorsements |
| 2026-05 | MeshCoin local ledger + witnesses (preview) | AR-06 |
| 2026-05 | Mini-apps in WebView not native SDK | AR-07 |
| 2026-05 | EncryptedDirect requires mesh_signature | Reduces AR-02 partial |
| 2026-05 | No forward secrecy in v1 | AR-04 |

---

## 11. Review cadence

- **Weekly** during beta: beta-blocker items in SECURITY_DEBT_REGISTER.
- **Per release:** update this ledger if trust boundaries move.
- **Post-incident:** add failure mode row within 48h.
