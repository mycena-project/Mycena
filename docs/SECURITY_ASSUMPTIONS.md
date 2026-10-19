# Mycelium — Security Assumptions

**Audience:** Users, operators, security reviewers
**Status:** Pre–public beta — assumptions may change per release.

This document states what the system **assumes to be true** for its security claims to hold. Violating an assumption may void protections without a visible error.

---

## 1. Threat model scope

We design against:

| Adversary | Assumed capability |
|-----------|-------------------|
| Malicious peer on mesh | Send arbitrary libp2p traffic |
| Malicious mini-app author | Tricked or malicious install |
| Relay operator | See IP addresses, circuit metadata; drop or delay traffic |
| Local attacker | Unlocked device, adb backup (Android today) |
| Scammer | Social engineering, fake APKs |

We **do not** claim protection against:

| Adversary | See [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md) |
|-----------|-----------------------------------------------------|
| Nation-state with WebView 0-day | Full device compromise |
| ISP / state traffic analysis | Metadata correlation |
| Seized unlocked phone | Forensics on sled + vault |
| Global Sybil farm | Witness / spam at scale |

---

## 2. Trust assumptions

### 2.1 You trust your device OS

- Keystore / StrongBox (Android) when `storage_key` is configured.
- Desktop refuses **new** weak path-derived vault keys without OS keyring; existing fallback keys migrate (SD-131).

### 2.2 You trust the official binary

- APK/IPA from Play / signed GitHub release matches source you audited.
- Sideloaded clones are **out of scope**.

### 2.3 You trust bootstrap on first use

- Default bootstrap includes **baked relay Peer IDs** and `relay.example.invalid`.
- First connection to a malicious bootstrap can eclipse or inject traffic until detected.

### 2.4 You trust relay operator for Internet path

- Relay sees **transport-layer** metadata (IPs, timing, circuit peers).
- Relay does **not** decrypt E2E chat payloads when E2E is used correctly.
- Relay **can** censor or throttle your circuits.

### 2.5 You trust peers for MeshCoin witnesses (if used)

- Witness quorum is **local and Sybil-vulnerable**.
- MXC is **not** global consensus money.

### 2.6 You trust mini-app install decisions

- Store listings with valid signatures are only as trustworthy as **publisher keys**.
- Sideload installs require **valid developer bundle signature** (`mycelium-bundle.sig.json`) unless from a signed store listing.
- WebView remains a large attack surface (Chromium).

---

## 3. Cryptographic assumptions

| Property | Assumption |
|----------|------------|
| libp2p transport PeerId | Rotates on cold start (default) and/or every 24h; stable account id for contacts |
| X25519 enc keys | Static until rotated manually; **no forward secrecy** on 1:1 |
| Ed25519 envelope sig | **Required on direct `Data` frames** (SD-030 mitigated); gossip relay bodies remain unsigned with inner auth |
| Group keys | PSK in QR; leak of QR = full group history compromise |
| At-rest encryption | ChaCha20-Poly1305 with master key from vault or fallback |

---

## 4. Network assumptions

- **LAN mesh** is lower latency but exposes PeerIds to local observers.
- **Internet** path often transits **one public relay** (concentration risk).
- **mDNS** on LAN is spoofable for peer discovery.
- Gossip scopes leak **interest** (which topics you subscribe to).

---

## 5. Operational assumptions

- Maintainers can ship updated **revocation lists** and **bootstrap** via releases.
- VPS host and DNS for `relay.example.invalid` remain under project control.
- No government mandate to backdoor a build we do not control (sideload ecosystem).

---

## 6. User behavior assumptions

Security features fail if users:

- Install unsigned mini-apps without reading permissions.
- Chat in **plaintext** when enc key not yet exchanged (app may still send).
- Share group QR invites publicly.
- Paste untrusted bootstrap multiaddrs.
- Treat MXC as bankable savings.

---

## 7. Explicit non-assumptions

We **do not** assume:

- Honest majority of peers.
- Confidentiality of message **metadata** (who talks to whom, when, scope).
- Availability of relay under censorship.
- App store permanence.
- Legal permissibility in every jurisdiction for mesh bulletin content.

---

## 8. Verification

Before trusting this release, verify:

1. [THREAT_MODEL.md](./THREAT_MODEL.md) — adversaries, assets, accepted residuals.
2. [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) — open items and waivers.
3. [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md) — NOT SAFE AGAINST.
4. Build hash / Play signing certificate fingerprint.

---

## 9. Change log

| Date | Change |
|------|--------|
| Jun 2026 | SD-030/041 mitigated; SD-032/060 Phase-1 ingest guards; desktop vault hardening |
| May 2026 | Initial pre–public beta assumptions |
