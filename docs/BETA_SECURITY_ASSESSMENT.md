# Mycelium — Brutal Honest Beta Security Assessment

**Review date:** May 2026  
**Question:** Would we **personally** trust this system in the scenarios below?

**Answer format:** Yes / Limited / No — with reasons. No marketing language.

---

## Summary verdict

| Scenario | Verdict |
|----------|---------|
| Protest | **Limited** (LAN-only, trained users) / **No** (Internet path with default relay) |
| Disaster zone | **Limited** |
| Hostile country | **No** |
| Censorship event | **Limited** (LAN) / **No** (Internet via default relay) |
| Internet outage | **Limited** (good LAN fit) |
| Targeted surveillance | **No** |

**Overall:** Suitable for **technical early adopters** who read [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md). P0 network signature and sideload gaps are **closed**; remaining risk is metadata, Sybil, and WebView TCB. **Not** suitable as primary life-safety or high-risk activist comms without LAN-only OPSEC.

---

## Protest

**Would we trust it?** **Limited** for a small street crew on LAN with trained OPSEC; **No** for a large protest relying on default Internet bootstrap.

**Why no (Internet path):**

- **SD-031:** Flooding unknown PeerIds still amplifies to connected peers (bounded fanout mitigated).
- **Metadata:** PeerId, scopes, and timing visible to relay and nearby observers — correlation with physical presence.

**Why limited yes:**

- LAN mesh without untrusted Internet bootstrap can work for **coordination bulletin** among pre-joined peers.
- Mini-apps are hardened enough that **we would not install random protest “apps”** — only audited bundles.

**What we would do instead:** Signal with verified safety numbers for core cell; Mycelium as **secondary** bulletin if P0 fixed and LAN-only.

---

## Disaster zone

**Would we trust it?** **Limited**.

**Why limited yes:**

- Offline-first design matches connectivity loss.
- Bulletin + mesh chat useful for **local** resource coordination when peers already on mesh.
- MeshCoin **emergency credits** (capped) align with barter-style neighbor help — **not** rebuilding the economy.

**Why not fully yes:**

- Initial peer discovery still hard for non-technical users.
- No guarantee strangers on mesh are honest (Sybil, scam listings).
- Seized phone exposes local history; Android backup disabled (SD-171 mitigated).

**Personal use:** Yes for **small neighborhood mesh** after training; no for **city-wide** untrusted mesh.

---

## Hostile country

**Would we trust it?** **No**.

**Why:**

- Default **Internet bootstrap** points to Fly.io relay — state can block DNS, subpoena operator, or MITM first connect if bootstrap poisoned.
- **No Tor/pluggable transport** — traffic fingerprintable as libp2p.
- **Static PeerId** — long-term tracking across sessions.
- **No forward secrecy** — historical decrypt if device later seized and keys extracted.
- Mini-app WebView is unacceptable TCB against state-grade exploit budget.

**Personal use:** Would not use as primary comms. Would not recommend to at-risk contacts without major roadmap Phase 2–3 delivery.

---

## Censorship event

**Would we trust it?** **Limited** on LAN; **No** on default Internet path.

**Why limited:**

- LAN mesh can survive **local** censorship of ISP if peers physically co-located.
- Gossip bulletin may spread within mesh — but **cannot** punch through national firewall without working bootstrap path.

**Why no for Internet:**

- Single relay hostname = single block point.
- Amplification bugs help censors **indirectly** (resource exhaustion).

---

## Internet outage

**Would we trust it?** **Limited** — this is the **best-fit scenario**.

**Why yes-ish:**

- Core value prop: devices talk without ISP.
- mDNS / LAN discovery relevant.

**Caveats:**

- Must have **pre-provisioned** peers and keys; not “download app during outage and find everyone.”
- Battery drain under flood (SD-030/031) still hurts.

**Personal use:** Yes for **prepared** mesh group; no for spontaneous city-scale adoption day one.

---

## Targeted surveillance environment

**Would we trust it?** **No**.

**Why:**

- Adversary with **passive** access (ISP, relay, LAN) learns graph metadata even if E2E content protected.
- Adversary with **active** access exploits SD-030, SD-080, SD-081 trivially.
- Adversary with **device** access wins completely (sled, vault fallback on desktop, Android backup).
- Mini-app bridge = high-value target for implant via malicious bundle.

**Personal use:** Would assume **full compromise** of comms metadata and **possible** content mistakes. Would not use for sensitive sourcing.

---

## What improved (fair credit)

- Mini-app Cell P0–P4: real install policy, sessions, capability tokens, bundle scan, Android process isolation.
- EncryptedDirect signature enforcement.
- MeshCoin repositioned with hard caps.
- Revocation gossip + safe mode + reputation signals.

These do **not** close network P0 holes.

---

## What we would require before personal high-risk use

1. SD-030, SD-031, SD-080, SD-081 fixed and tested.
2. SD-171 fixed on Android.
3. Red-team pass on mini-apps with written failures only on accepted risks.
4. Multi-relay + signed bootstrap.
5. Double ratchet (v1, partial) — **shipped** for 1:1 after PeerInfo prekey exchange ([ADR 004](./adr/004-double-ratchet-e2e.md)); v0 fallback retained.

---

## Release recommendation

| Label | Recommendation |
|-------|----------------|
| **Public Play “beta”** | **Hold** until P0 network fixes or signed waivers + loud [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md) |
| **Closed technical preview** | **OK** with participant cap and LAN-only guidance |
| **Activist/journalist outreach** | **Do not** until assessment changes |

---

## Sign-off

This assessment is the maintainers’ engineering judgment, not legal advice or a certification.
