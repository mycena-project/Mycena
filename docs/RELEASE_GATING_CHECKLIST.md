# Mycelium — Release Gating Checklist (Public Beta)

Use this checklist for **every** public beta candidate (Play Store, GitHub release, relay deploy).  
**No single item is optional** unless a linked waiver exists in [BETA_BLOCKERS.md](./BETA_BLOCKERS.md).

---

## 1. Security code gates

### 1.1 Network & protocol (P0)

- [ ] **SD-030:** Incoming `WireMessage::Data` without valid envelope signature is **rejected** (metric `messages_dropped_no_sig` increments)
- [ ] **SD-031:** Outbound send to unknown `to_peer` does **not** fan out to all peers (bounded routing or drop)
- [ ] **SD-080:** Gossip `ScopedReceived` does **not** re-sign with local libp2p key (forward opaque blob)
- [ ] **SD-081:** `send_chat` **fails closed** when recipient enc key missing (or explicit dangerous override)
- [ ] Replay: duplicate message IDs not re-delivered to app layer (seen cache behavior documented)

### 1.2 Android

- [ ] `android:allowBackup="false"` (SD-171)
- [ ] `android:fullBackupContent` excludes `databases/`, identity, mini-app trees
- [ ] UniFFI bindings match `mycelium.udl` (issue_capability, publish_revocation, install params)
- [ ] Mini-app runs in `:miniapp` process; bridge IPC smoke-tested
- [ ] ProGuard/R8 rules reviewed for FFI surface

### 1.3 Desktop

- [ ] Document weak at-rest fallback OR require OS keyring + refuse start without key (SD-131)
- [ ] Tauri CSP / IPC surface reviewed
- [ ] Mini-app iframe: `credentialless`, `referrerpolicy`, origin-checked postMessage

### 1.4 Mini-apps (Cell)

- [ ] `cargo test -p mycelium-app miniapp::` green
- [ ] `miniapp-sdk lint` on reference bundle
- [ ] Install preview shows trust level + revocation + bundle scan failures
- [ ] Sideload path shows provenance warning (if sideload still enabled)
- [ ] Red-team script: AS-01,02,04,05,06,07 attempted — results recorded

### 1.5 MeshCoin

- [ ] In-app copy: **preview / emergency credits only** — no investment language
- [ ] Settlement caps enforced in tests (`settlement_policy.rs`)
- [ ] Faucet disabled or capped in release flavor
- [ ] Legal sign-off if any “payment” wording in store listing

---

## 2. Build & supply chain

- [ ] `cargo clippy` / `cargo test` workspace green
- [ ] Lockfile committed; dependency audit noted (cargo-deny / vet aspirational)
- [ ] Release APK/AAB signed with **production** key (not debug)
- [ ] SBOM or dependency list archived for release tag
- [ ] Relay Docker image tagged; Fly secrets not in repo

---

## 3. Infrastructure & operations

- [ ] Relay Peer ID matches embedded bootstrap in release branch
- [ ] DNS `mycelium-relay.fly.dev` monitored; break-glass bootstrap multiaddrs documented
- [ ] Relay operator runbook: key rotation, compromise response ([INCIDENT_RESPONSE_RUNBOOK.md](./INCIDENT_RESPONSE_RUNBOOK.md))
- [ ] Status endpoint (`:8080`) reviewed for information disclosure (SD-003)
- [ ] No PII in relay logs (IP retention policy documented)

---

## 4. Documentation & legal

- [ ] [SECURITY_ASSUMPTIONS.md](./SECURITY_ASSUMPTIONS.md) linked from store listing / README
- [ ] [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md) in-app or website
- [ ] [HIGH-RISK USER WARNINGS](./KNOWN_LIMITATIONS.md#high-risk-user-warnings) visible to activists/journalists path
- [ ] Privacy policy URL live (not template placeholder)
- [ ] `SECURITY_DEBT_REGISTER.md` reviewed; beta-blocker table matches [BETA_BLOCKERS.md](./BETA_BLOCKERS.md)

---

## 5. Manual scenarios (smoke)

| # | Scenario | Pass criteria |
|---|----------|---------------|
| 1 | Fresh install → chat with one peer | E2E when keys exchanged; **no silent plaintext** |
| 2 | Install mini-app from file (sideload) | Provenance modal; permissions listed |
| 3 | Revoked app id in bundle | Install blocked at preview |
| 4 | Relay offline | Mesh LAN or degraded mode documented |
| 5 | Panic wipe (if exposed) | Data dir removed |

---

## 6. Sign-off

| Role | Name | Date | Beta build ID |
|------|------|------|---------------|
| Engineering | | | |
| Security review | | | |
| Product | | | |
| Legal (if applicable) | | | |

**Release decision:** ☐ Ship ☐ Ship with waivers ☐ Hold

Attached waivers: _______________
