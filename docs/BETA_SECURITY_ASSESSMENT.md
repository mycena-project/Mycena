# Mycena — Brutal Honest Beta Security Assessment

**Review date:** July 2026
**Threat model:** [THREAT_MODEL.md](./THREAT_MODEL.md) (canonical adversary + asset model)
**Question:** Would we **personally** trust this system in the scenarios below?

**Answer format:** Yes / Limited / No — with reasons. No marketing language.

---

## Summary verdict

| Scenario | Verdict | Threat-model adversary (§4) |
|----------|---------|-------------------------------|
| Protest | **Limited** (LAN-only, trained users) / **No** (Internet path with default relay) | Sybil swarm · Malicious relay · Nation-state (metadata) |
| Disaster zone | **Limited** | Sybil swarm · Compromised custody carrier |
| Hostile country | **No** | Nation-state · Malicious relay |
| Censorship event | **Limited** (LAN) / **No** (Internet via default relay) | Nation-state · Malicious relay |
| Internet outage | **Limited** (good LAN fit) | Sybil swarm (local) |
| Targeted surveillance | **No** | Nation-state · Device theft |

**Overall:** Suitable for **technical early adopters** who read [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md) and [THREAT_MODEL.md](./THREAT_MODEL.md) §5 (accepted residuals). Network P0 items (SD-030, SD-031, SD-080, SD-081) and Android backup (SD-171) are **mitigated**; remaining risk is **metadata**, **Sybil**, **WebView TCB**, and **offline settlement**. **Not** suitable as primary life-safety or high-risk activist comms without LAN-only OPSEC.

---

## Protest

**Would we trust it?** **Limited** for a small street crew on LAN with trained OPSEC; **No** for a large protest relying on default Internet bootstrap.

**Why no (Internet path):**

- **Metadata (SD-001, SD-070):** PeerId, scopes, timing visible to relay and nearby observers — correlation with physical presence. See [THREAT_MODEL.md](./THREAT_MODEL.md) §5.3.
- **Relay concentration (SD-020, SD-220):** Default bootstrap pins to one operator hostname.

**Why limited yes:**

- LAN mesh without untrusted Internet bootstrap can work for **coordination bulletin** among pre-joined peers.
- Mini-app P0 hardening closed (SD-040, SD-041 mitigated) — **we would still not install random protest “apps”** without auditing the developer signature.

**What we would do instead:** Signal with verified safety numbers for core cell; Mycena as **secondary** bulletin if LAN-only and users trained on metadata limits.

**Debt / privacy phase:** SD-001, SD-070 · Privacy Phase C–D

---

## Disaster zone

**Would we trust it?** **Limited**.

**Why limited yes:**

- Offline-first design and **custody DTN** match connectivity loss ([CUSTODY_DTN.md](./CUSTODY_DTN.md), [THREAT_MODEL.md](./THREAT_MODEL.md) §3.4).
- Bulletin + mesh chat useful for **local** resource coordination when peers already on mesh.
- MeshCoin **emergency credits** (capped) align with barter-style neighbor help — **not** rebuilding the economy.

**Why not fully yes:**

- Initial peer discovery still hard for non-technical users.
- No guarantee strangers on mesh are honest (**Sybil**, SD-010).
- **Custody carriers** see routing metadata (size, timing, destination peer id) even when content is E2E — [THREAT_MODEL.md](./THREAT_MODEL.md) §5.2.
- Seized phone exposes local history; Android backup disabled (SD-171 mitigated).

**Personal use:** Yes for **small neighborhood mesh** after training; no for **city-wide** untrusted mesh.

**Debt / privacy phase:** SD-010, SD-241 · Phase B (quotas)

---

## Hostile country

**Would we trust it?** **No**.

**Why:**

- Default **Internet bootstrap** points to the Fly.io relay — state can block DNS, subpoena the operator (**SD-180, SD-210**), or MITM first connect if bootstrap poisoned (**SD-090**).
- **No Tor/pluggable transport** — traffic fingerprintable as libp2p (**SD-180**).
- **Pseudonymous, not anonymous** — account id + transport PeerId trackable (**SD-190**).
- **Partial forward secrecy** — v1 ratchet after prekey exchange; v0 / groups remain weaker (**SD-100, SD-101**).
- Mini-app WebView is unacceptable TCB against state-grade exploit budget (**SD-050**).

**Personal use:** Would not use as primary comms. Would not recommend to at-risk contacts without Privacy Roadmap Phase D delivery.

**Threat-model ref:** §4.1 Nation-state · §5.3 metadata · §5.5 WebView

---

## Censorship event

**Would we trust it?** **Limited** on LAN; **No** on default Internet path.

**Why limited:**

- LAN mesh can survive **local** censorship of ISP if peers physically co-located.
- Gossip bulletin may spread within mesh — but **cannot** punch through national firewall without working bootstrap path.

**Why no for Internet:**

- Single relay hostname = single block point (**SD-180, SD-220**).
- Malicious relay can drop circuits selectively (**SD-181**).

**Threat-model ref:** §4.5 Malicious relay operator

---

## Internet outage

**Would we trust it?** **Limited** — this is the **best-fit scenario**.

**Why yes-ish:**

- Core value prop: devices talk without ISP.
- mDNS / LAN discovery relevant.

**Caveats:**

- Must have **pre-provisioned** peers and keys; not “download app during outage and find everyone.”
- Battery drain under Sybil flood still possible at scale (**SD-010**); ingest rate limits help (**SD-032** mitigated).

**Personal use:** Yes for **prepared** mesh group; no for spontaneous city-scale adoption day one.

---

## Targeted surveillance environment

**Would we trust it?** **No**.

**Why:**

- Adversary with **passive** access (ISP, relay, LAN) learns graph metadata even if E2E content protected (**SD-001, SD-070, SD-071**). This is an **accepted residual** per [THREAT_MODEL.md](./THREAT_MODEL.md) §5.3 — not a bug we claim to fix in beta.
- Adversary with **active** mesh access: unsigned wire and plaintext chat downgrade paths are **closed** (SD-030, SD-080, SD-081 mitigated); Sybil witness and spam scale remain (**SD-010, SD-011**).
- Adversary with **device** access wins completely (sled, vault, hot wallet — **SD-110, SD-130, SD-160**).
- Malicious mini-app or sideload with valid dev signature still a bridge abuse vector (**SD-040, SD-041** mitigated; **SD-050** WebView class remains).

**Personal use:** Would assume **full compromise** of comms metadata and **possible** content mistakes on groups / mis-verified keys. Would not use for sensitive sourcing.

**Threat-model ref:** §4.1, §4.3, §4.2

---

## What improved since May 2026 (fair credit)

| Area | Status | Debt IDs |
|------|--------|----------|
| Unsigned wire / downgrade | **Mitigated** | SD-030, SD-140 |
| Relay fanout amplification | **Mitigated** | SD-031 |
| Gossip re-sign author confusion | **Mitigated** | SD-080 |
| Plaintext chat fallback | **Mitigated** — fail-closed send | SD-081, SD-141 |
| Android backup exfiltration | **Mitigated** | SD-171 |
| Mini-app CSP / sideload signature | **Mitigated** | SD-040, SD-041, SD-240 |
| Desktop weak vault (new installs) | **Mitigated** | SD-131 |
| Ingest rate limits + replay window | **Mitigated** | SD-032, SD-060 |
| Mutex poison recovery | **Mitigated** — node continues after lock panic | (availability hardening) |
| Mini-app Cell P0–P4 | Shipped | sessions, capability tokens, bundle scan, Android process isolation |
| Double ratchet v1 (1:1) | **Partial** | SD-100 |
| Threat model document | **Shipped** | [THREAT_MODEL.md](./THREAT_MODEL.md) |

These close **network P0 beta-blockers** from the May assessment. They do **not** close metadata privacy, Sybil economics, or nation-state device TCB.

---

## What we would require before personal high-risk use

| # | Requirement | Status (Jul 2026) |
|---|-------------|-------------------|
| 1 | SD-030, SD-031, SD-080, SD-081 fixed and tested | **Done** |
| 2 | SD-171 fixed on Android | **Done** |
| 3 | Red-team pass on mini-apps with written failures only on accepted risks | **Partial** — architecture hardened; formal red-team **not** completed |
| 4 | Multi-relay + signed bootstrap | **Open** — SD-022, SD-220 · Privacy Phase C |
| 5 | Double ratchet (1:1) | **Partial** — v1 shipped ([ADR 004](./adr/004-double-ratchet-e2e.md)); groups still PSK |
| 6 | Metadata resistance (padding / multi-relay) | **Open** — SD-070 experimental off · Privacy Phase C |
| 7 | External security review against [THREAT_MODEL.md](./THREAT_MODEL.md) §8 checklist | **Pending** |

---

## Mapping to threat model assets

| Asset ([THREAT_MODEL.md](./THREAT_MODEL.md) §3) | Blocks high-risk use today? |
|--------------------------------------------------|----------------------------|
| Identity keys | Partial — rotation + vault OK; tracking + seizure not |
| Message content | Partial — E2E 1:1 OK; metadata + groups weak |
| Payment vouchers | Yes for commerce — unsettled double-spend accepted (§5.1) |
| Custody queue | Partial — availability good; carrier metadata exposed |
| Relay reputation | No — Sybil-gameable (SD-010) |

---

## Release recommendation

| Label | Recommendation (Jul 2026) |
|-------|---------------------------|
| **Public Play “beta”** | **Conditional OK** — P0 network + sideload mitigations shipped; require loud [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md), updated [privacy policy](../android/app/src/main/assets/privacy_policy.html), and **pending** external review per [THREAT_MODEL.md](./THREAT_MODEL.md) §8 |
| **Closed technical preview** | **OK** with participant cap and LAN-only guidance |
| **Activist/journalist outreach** | **Do not** until assessment + Phase C/D privacy items move; metadata residuals explicit in §5 |

---

## Sign-off

This assessment is the maintainers’ engineering judgment, not legal advice or a certification. For external review, use [THREAT_MODEL.md](./THREAT_MODEL.md) §8 together with this document and a dated snapshot of [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md).
