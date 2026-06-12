# Mycelium — Abuse Response Playbook

**Audience:** Operators, maintainers, on-call  
**Assumption:** There is **no central server** with user accounts—response is **distributed**, **legal**, and **client-side**.

**Registers:** [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) · [ARCHITECTURE_RISKS.md](./ARCHITECTURE_RISKS.md)

---

## 1. Scope of responsibility

| We control | We do NOT control |
|------------|-------------------|
| Default relay (`mycelium-relay`) | Peer-to-peer message storage on user devices |
| App store binaries | Bulletin content after gossip |
| Bootstrap peer list in releases | Witness gossip for MeshCoin |
| Mini-app store listings we sign | Sideload: user ack in install UI (partially mitigated — no crypto verify for sideload) |

**Moderation is impossible at scale** (SD-230)—response = **tooling + legal + client updates**, not “delete from server.”

---

## 2. Abuse categories

| Category | Examples | Primary harm |
|----------|----------|--------------|
| **Spam** | Flood direct/gossip, sync storms | DoS, UX |
| **Harassment** | Targeted messages, impersonation | User safety |
| **Malware** | Malicious mini-app, toxic ZIP | Device compromise |
| **Scam** | Fake MXC payments, phishing bootstrap | Financial / credential |
| **Illegal content** | CSAM, terrorism propaganda in bulletin | Legal |
| **Sybil** | Fake witnesses, fake store listings | Integrity |
| **Infrastructure** | Relay exhaustion, DDoS Fly.io | Availability |

---

## 3. Severity classification

| Level | Criteria | Response time target |
|-------|----------|----------------------|
| **SEV-1** | Active RCE, mass wallet drain, CSAM distribution link | 4h |
| **SEV-2** | Relay down, bootstrap MITM, signed app compromise | 24h |
| **SEV-3** | Spam wave, scam mini-app in store gossip | 72h |
| **SEV-4** | Individual harassment, misconfiguration | Best effort |

---

## 4. Detection signals

### 4.1 Technical

- Spike in `messages_dropped_*` metrics (if telemetry opt-in exists—**default: none**)
- Relay `/health` connection count anomaly
- Play Store reviews / GitHub issues mentioning “spam” / “virus”
- `fly logs` circuit reservation saturation

### 4.2 Social / legal

- Law enforcement request
- Press inquiry
- Trusted user report with PeerId + screenshot

### 4.3 Store

- Google Play policy warning
- App signature mismatch report

---

## 5. Response procedures

### 5.1 Spam / network flood (SEV-3)

1. **Confirm:** PeerId samples, message type (`Data` vs gossip), approximate rate.
2. **Short-term:**
   - Document workaround: disconnect Internet (mesh-only), block PeerId locally (feature gap—manual ignore).
   - Increase local throttle in node if hotfix ready (reputation strikes).
3. **Medium-term:** Ship SD-030, SD-031, rate limits (SECURITY_ROADMAP Phase 0–1).
4. **Communicate:** Status on project channel; no PII in public post.

### 5.2 Malicious mini-app (SEV-1 / SEV-3)

1. **Identify:** `app_id`, developer_peer_id, bundle_hash from listing.
2. **If in gossip store:** Publish **revocation listing** (signed update) with `revoked: true` (requires store pipeline).
3. **If sideload:** Remind users SD-041; ship install signature requirement.
4. **Android:** Request Play Protect scan if APK distributed OOB.
5. **Post-mortem:** Add debt row; CSP / permission review.

### 5.3 Scam / MeshCoin fraud (SEV-3)

1. **Never imply funds recovery**—MXC is preview.
2. Pin in-app banner + web FAQ.
3. If fake **signed** txs: document witness Sybil (SD-011).
4. Law enforcement: provide PeerId, tx hashes from **reporter device only**—we have no server logs.

### 5.4 Illegal content (SEV-1)

1. **Do not** re-host content on infrastructure.
2. **Relay:** We do not inspect circuit payloads—legal counsel determines NCMEC / authority reporting duty (jurisdiction-specific).
3. **App:** Ship client block list for scope + PeerId if legally required in jurisdiction of distribution.
4. **Document:** Moderation impossibility + user obligation in Terms (`assets/terms.html`).
5. **Cooperate** with lawful orders pertaining to **operator systems only** (relay logs if any—minimize in advance).

### 5.5 Relay / bootstrap compromise (SEV-2)

1. **Rotate** relay libp2p key; update `bootstrap.rs` + remote config if available.
2. **Release** emergency app update with new peer ID.
3. **Notify** users: verify bootstrap fingerprint out-of-band.
4. **Post-mortem:** SD-091, SD-220.

### 5.6 Vulnerability report (security)

1. **Intake:** security@[project-domain] (create before beta).
2. **Triage** within 72h; acknowledge receipt.
3. **Fix** Critical within 14 days target; coordinate disclosure.
4. **Credit** reporter if desired.
5. Update SECURITY_DEBT_REGISTER + release notes.

---

## 6. Communication templates

### User-facing (in-app / blog)

> We are aware of increased spam on the Mycelium network. Mycelium is a decentralized mesh—messages are stored on participants’ devices, not on our servers. Update to version X.Y.Z for improved filtering. Do not install mini-apps from untrusted QR codes. MeshCoin (MXC) is experimental and offers no monetary protection.

### Law enforcement (template — legal must review)

> Mycelium Network does not operate a centralized messaging service. We operate optional relay infrastructure that forwards encrypted libp2p circuits without access to message content. We do not store user accounts or message databases. We can provide information about our relay systems to the extent available and as required by law.

---

## 7. Infrastructure actions

| Action | When | Command / location |
|--------|------|---------------------|
| Scale relay | Circuit exhaustion | Fly.io scale |
| Pause relay | Active exploit in libp2p stack | `fly apps stop` (breaks Internet path) |
| Rotate TLS / certs | Compromise | Fly secrets |
| Invalidate bootstrap | Peer ID compromise | Edit `bootstrap.rs`, ship app |

**No IP logging policy:** Configure Fly.io / reverse proxy to **not store** client IPs in application logs. Audit quarterly.

---

## 8. Play Store / distribution

| Event | Action |
|-------|--------|
| Policy rejection | Legal review + feature change; update Play Store listing copy |
| Malware flag | Supply SBOM + source link; point to reproducible build |
| Government takedown | Geographic build variant counsel |

---

## 9. Post-incident checklist

- [ ] SEV level assigned
- [ ] SECURITY_DEBT_REGISTER row added or updated
- [ ] Fix shipped or waiver documented
- [ ] Users notified if ongoing harm
- [ ] Privacy policy / terms updated if practice changed
- [ ] Drill notes filed (internal)
- [ ] Metrics reviewed (if available)

---

## 10. Contacts (fill before beta)

| Role | Contact |
|------|---------|
| Security lead | _[EMAIL]_ |
| Legal | _[EMAIL]_ |
| Infrastructure | _[EMAIL]_ |
| Public communications | _[EMAIL]_ |

---

## 11. Related debt IDs (quick reference)

| Abuse type | Primary IDs |
|------------|-------------|
| Spam | SD-030, SD-031, SD-032 |
| Sybil | SD-010, SD-011 |
| Mini-app malware | SD-040, SD-041, SD-240 |
| Scam coin | SD-011, SD-160, SD-161 |
| Censorship | SD-180, SD-181 |
| Illegal content | SD-230, SD-231 |
| Relay | SD-020, SD-022, SD-220 |

---

## 12. Exercise schedule

- **Quarterly:** Tabletop spam + relay outage (2h).
- **Annually:** Mini-app malware injection drill.
- **Before beta:** Single run of SEV-2 bootstrap rotation drill.
