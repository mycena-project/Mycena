# Mycelium — Privacy Roadmap

**Goal:** Minimize data collection, metadata leakage, and identifiability—while staying honest about what mesh + relay architectures cannot hide.

**Companion:** [THREAT_MODEL.md](./THREAT_MODEL.md) · [BETA_SECURITY_ASSESSMENT.md](./BETA_SECURITY_ASSESSMENT.md) · [PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md) · [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md)

---

## Privacy principles

1. **Collect nothing centrally** that can be avoided.
2. **Assume the relay sees IPs** until proven otherwise with architecture change.
3. **Assume peers learn metadata** from gossip and sync.
4. **E2E protects content**, not presence, not volume, not timing.
5. **User control:** panic wipe, export nothing by default.

---

## Current privacy posture (honest)

| Data type | Collected by app? | Leaves device? | Who can see it |
|-----------|-------------------|----------------|----------------|
| Message plaintext | Local sled | Only if E2E not used or broken | Peers with keys |
| libp2p PeerId | Generated locally | Yes | All peers, relay (circuit) |
| Display name | Local prefs | Yes (PeerInfo, messages) | Peers |
| X25519 enc pubkey | Local vault | Yes (PeerInfo) | Peers |
| IP address | N/A on device | Yes (TCP/QUIC to relay/peers) | ISP, relay host, peers on LAN |
| MXC balances/txs | Local ledger | Gossip to subscribed peers | Peers in coin scope |
| Mini-app storage | Per-app keys | No (local) | App code via bridge |
| Analytics | **None implemented** | No | — |
| Crash logs | Platform default | Maybe Google/Apple | Platform |

**Privacy policy template:** `android/app/src/main/assets/privacy_policy.html` — must be completed before Play Store (contact, address).

---

## Phase A — Documentation & compliance (pre-beta)

| Action | Owner | Status |
|--------|-------|--------|
| Publish privacy policy URL in Play Console | Legal/product | **Ready** — URL after Pages deploy: [docs/legal/README.md](./legal/README.md) |
| Document relay IP processing (no retention) | Ops | **Policy needed** — stated in privacy policy; ops audit **TODO** |
| In-app link: Settings → Privacy Policy | Android | **Done** — Settings → Legal |
| Clarify “we cannot delete mesh copies” in user doc | Docs | **Done** — privacy policy + [security-for-users.md](./security-for-users.md) |
| MeshCoin = no monetary value statement | Product | Banner added |

---

## Phase B — Data minimization (beta)

| Action | Debt IDs | Effort | Status |
|--------|----------|--------|--------|
| Relay: disable access logs / minimize Fly metrics | SD-210 | S | **Documented** — [RELAY_OPERATOR_GUIDE.md §9](./RELAY_OPERATOR_GUIDE.md#9-logging-privacy-and-audit-sd-003-sd-210) |
| Health endpoint: remove peer_id from public JSON | SD-003 | S | **Done** — `/health` JSON `{ok, version}` |
| Optional “minimal PeerInfo” mode (enc key only) | SD-004 | M | Planned |
| Default display name empty or random | SD-082 | S | Planned |
| No backup to cloud (`allowBackup=false`) | SD-171 | S | **Done** |
| Coin: stop gossiping balance by default | SD-002 | M | **Done** — balance query responses off by default |

---

## Phase C — Metadata resistance (6–12 months)

*Detailed mechanisms and prototype phases: [PRIVACY_EXPERIMENTS.md](./PRIVACY_EXPERIMENTS.md) (codename **Shade**).*

| Action | Debt IDs | Effort | Status |
|--------|----------|--------|--------|
| Padding + batching scheduler | SD-070 | L | **Experimental** — [`privacy_shaping.rs`](../crates/mycelium-core/src/privacy_shaping.rs), off by default; [benchmark](./PRIVACY_SHAPING_BENCHMARK.md) |
| Reduce sync fingerprint (bloom size normalization) | SD-001 | M | Planned |
| Scope bucketing (e.g. `city/*` not exact list announce) | SD-004 | M | **Partial** — `ScopeAnnounce` bucketing when shaping enabled |
| Short-lived QR introduction tokens | SD-191 | M | Planned |
| Multi-relay rotation (IP not pinned to one host) | SD-071, SD-220 | M | Planned |

---

## Phase D — Strong pseudonymity (12+ months)

| Action | Debt IDs | Effort |
|--------|----------|--------|
| Pairwise peer IDs for 1:1 chat | SD-190 | L |
| Rotatable public mesh identity | SD-190 | L |
| Tor/Nym transport plugin | SD-180 | L |
| Private information retrieval for sync | SD-001 | L |

---

## User-facing privacy controls (planned)

| Control | Phase | Description |
|---------|-------|-------------|
| Panic wipe | **Shipped** | Deletes local data + keys |
| Internet / mesh-only toggle | Shipped (connectivity) | Limits relay use |
| Custom bootstrap | Shipped | **Risk:** user can be phished |
| Minimal handshake mode | B | Less metadata to peers |
| Cover traffic | C | Battery cost disclosed |
| Travel mode | D | One-tap lock + delayed wipe |

---

## GDPR / regulatory mapping (high level — not legal advice)

| Topic | Mycelium stance | Action needed |
|-------|-----------------|---------------|
| Data controller | App publisher for store listing; relay may be joint | Legal review |
| Lawful basis | Legitimate interest / contract for app | Privacy policy |
| Right to erasure | Local panic wipe only; mesh copies not erasable | Disclose clearly |
| DPIA | Required before EU scale if relay logs IPs | Conduct DPIA |
| Data residency | Fly.io region choice | Document in policy |

---

## Metrics

| Metric | Target |
|--------|--------|
| Fields in public relay `/health` | ≤ 2 (ok + version) |
| Third-party SDKs in Android APK | 0 analytics SDKs |
| Privacy policy age | < 90 days at release |
| User data export requests handled | N/A (no server account) |

---

## What we will not promise users

- “Anonymous” — we are pseudonymous at best.
- “We delete your messages everywhere” — mesh replication forbids this.
- “Relay cannot see your IP” — false for default Internet path.
- “MXC is private money” — gossip exposes tx metadata.

---

## Review

- Align with [SECURITY_ROADMAP.md](./SECURITY_ROADMAP.md) Phase 2 each quarter.
- Update `privacy_policy.html` when data practices change.
