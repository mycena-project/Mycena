# Mycelium — Incident Response Runbook

**Audience:** Maintainers, relay operators, release managers  
**Assumption:** No central user database; incidents are **distributed**, **client-side**, and **infrastructure** events.

**Related:** [ABUSE_RESPONSE_PLAYBOOK.md](./ABUSE_RESPONSE_PLAYBOOK.md) · [BETA_BLOCKERS.md](./BETA_BLOCKERS.md)

---

## 1. Severity levels

| Level | Examples | Target response |
|-------|----------|-----------------|
| **SEV-1** | Active exploit in default release; signing key leak; malicious APK signed with our key | Hours |
| **SEV-2** | Relay compromise suspected; spam storm; store listing malware | 24–72 h |
| **SEV-3** | Single-user harassment; bad bulletin content; doc error | Best effort |
| **SEV-4** | Hardening gap, no active exploit | Backlog |

---

## 2. Roles

| Role | Responsibility |
|------|----------------|
| **Incident lead** | Triage, comms, timeline |
| **Engineering** | Patch, release, metrics |
| **Relay operator** | Fly.io account, DNS, relay key |
| **Legal** | CSAM, subpoena, store policy |

There is no 24/7 SOC unless the project establishes one.

---

## 3. Detection sources

| Source | What it indicates |
|--------|-------------------|
| User reports | Harassment, scam mini-app, lost funds (MXC) |
| Metrics (`messages_dropped_*`, queue depth) | SD-030/031 abuse |
| Relay status JSON | Connection spikes, exhaustion |
| Play Console / GitHub | Malicious fork, impersonation |
| Journalist / researcher | Responsible disclosure |

---

## 4. Playbooks

### 4.1 Fly.io relay compromise (SEV-1/2)

**Indicators:** Unexpected Peer ID, config drift, Fly account alert, traffic to wrong host.

**Immediate:**

1. Stop promoting compromised multiaddr in docs/bootstrap.
2. Rotate relay libp2p keypair; deploy new Fly app with **new** Peer ID.
3. Ship emergency release with updated baked bootstrap (or signed bootstrap manifest when available).
4. Post public notice: “Rotate to build X; old relay Peer ID untrusted.”

**We cannot:** Revoke circuits already established; wipe user message history on devices.

**Evidence:** Preserve Fly logs, deploy history, image digest.

---

### 4.2 DNS / domain takeover (SEV-1/2)

**Indicators:** `mycelium-relay.fly.dev` resolves elsewhere; TLS mismatch.

**Immediate:**

1. Halt auto-connect to hostname; document manual multiaddrs.
2. Recover Fly/DNS account; invalidate old TLS certs.
3. Connectivity probe hardening (multi-host) — see SD-022.

---

### 4.3 Signing key compromise (SEV-1)

**Android release key:**

1. Stop all releases from compromised key.
2. Play Console key rotation procedure; users must uninstall malicious updates.
3. Post advisory; consider package name change only as last resort.

**App store listing / curator key (libp2p):**

1. Attacker can publish signed listings + revocations.
2. Ship updated **curator allowlist** in app (when implemented) or disable store ingest until patch.
3. Publish correct revocations from clean key.

**Relay key:** See §4.1.

---

### 4.4 Malicious APK redistribution (SEV-2)

**Indicators:** Fake “Mycelium” APK with malware; wrong signature.

**Actions:**

1. DMCA / abuse report to host.
2. Official download only from Play / signed GitHub releases page.
3. Document APK signature fingerprint for verification.

**We cannot:** Remove sideloaded copies from the internet.

---

### 4.5 Play Store takedown (SEV-2)

**Indicators:** App removed, policy strike.

**Actions:**

1. Identify policy violation (content, MXC wording, missing privacy policy).
2. Appeal with updated copy referencing [MESHCOIN_POSITIONING.md](./MESHCOIN_POSITIONING.md).
3. Communicate alternate install only if legally safe.

---

### 4.6 Spam / amplification storm (SEV-2)

**Indicators:** Battery drain, sled growth, `messages_dropped_*` flatlines while queue grows.

**Tied to:** SD-030, SD-031.

**Immediate:**

1. Recommend users switch to Passive energy mode; disconnect Internet bootstrap.
2. Ship patch rejecting unsigned Data + cap fanout.
3. Publish `bundled_revocations.json` update if spam from known app ids.

**Metrics to capture:** `seen_cache_size`, forward fanout, peer strike counts.

---

### 4.7 Malicious mini-app (SEV-2)

**Indicators:** User report, scam QR, identity exfiltration.

**Actions:**

1. Publish signed **revocation** gossip (`miniapp_publish_revocation` / curator tooling).
2. Update bundled revocations in next release.
3. Document in [ABUSE_RESPONSE_PLAYBOOK.md](./ABUSE_RESPONSE_PLAYBOOK.md).

**We cannot:** Remotely uninstall app from user devices.

---

### 4.8 Forced / malicious update scenario (SEV-2)

**Indicators:** Attacker pushes fake update server; supply-chain compromise.

**Actions:**

1. Verify GitHub release signatures / Play signing cert.
2. Pin users to known-good version hash.
3. Post-incident: reproducible builds + attestations (H23) for store listings.

---

### 4.9 Crash-report / logging privacy incident (SEV-3)

**Indicators:** Sensitive data in logcat, tracing subscriber misconfig.

**Actions:**

1. Audit `tracing` fields for message bodies, peer IDs in release builds.
2. Disable verbose logs in release; redact PeerId in user-exported logs.
3. Note: Google Play pre-launch reports may contain stack traces — no custom crash SDK today.

---

## 5. Communication templates

### User-facing (short)

> We identified a security issue in Mycelium [version/build]. Risk: [one sentence]. Action: update to [version] or avoid [feature]. We do not have access to your messages on our servers.

### Researcher acknowledgment

> Thank you for the report. We confirm / cannot reproduce [X]. Fixed in [commit]. CVE: [pending/TBD]. Embargo until [date].

---

## 6. Post-incident

- [ ] Update [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md)
- [ ] Add regression test
- [ ] Entry in incident log (private)
- [ ] Review [RELEASE_GATING_CHECKLIST.md](./RELEASE_GATING_CHECKLIST.md)

---

## 7. What we cannot do

| Request | Reality |
|---------|---------|
| Delete a message network-wide | No server copy |
| Ban a user globally | No accounts |
| Recover MXC “stolen” via Sybil witnesses | Local ledger view only |
| Guarantee activist safety | See [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md) |
