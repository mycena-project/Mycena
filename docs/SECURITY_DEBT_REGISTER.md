# Mycelium — Security Debt Register

**Status:** Pre-public beta · living document
**Last updated:** 2026-08-01
**Audience:** Engineering, security review, release management
**Tone:** Assume hostile networks, malicious users, compromised devices, and eventual nation-state interest.

**Operator note (2026-08):** Abuse / security intake contacts are filled in [ABUSE_RESPONSE_PLAYBOOK.md](./ABUSE_RESPONSE_PLAYBOOK.md) (`security@example.invalid`). Body rows for items marked **Mitigated** in the summary table were aligned on this date (beta-blocker **no** where mitigation shipped). Remaining open beta-blockers still gate public Play Store / wide beta — cross-check `KNOWN_LIMITATIONS.md`.

This register records **known** security, privacy, abuse, and legal debt. It is not a certification, audit, or guarantee. Items marked **beta-blocker: yes** must be resolved or explicitly accepted with written risk sign-off before a public Play Store / wide beta.

**Related:** [THREAT_MODEL.md](./THREAT_MODEL.md) · [ARCHITECTURE_RISKS.md](./ARCHITECTURE_RISKS.md) · [SECURITY_ROADMAP.md](./SECURITY_ROADMAP.md) · [PRIVACY_ROADMAP.md](./PRIVACY_ROADMAP.md) · [ABUSE_RESPONSE_PLAYBOOK.md](./ABUSE_RESPONSE_PLAYBOOK.md) · [BETA_SECURITY_ASSESSMENT.md](./BETA_SECURITY_ASSESSMENT.md)

---

## Severity scale

| Level | Meaning |
|-------|---------|
| **Critical** | Remote compromise, mass deanonymization, or silent fund loss plausible without victim action |
| **High** | Targeted compromise, large-scale abuse, or regulatory incident likely at beta scale |
| **Medium** | Meaningful harm under realistic adversary; mitigations partial |
| **Low** | Limited blast radius or requires chained preconditions |
| **Informational** | Hardening / hygiene; not exploitable alone at beta |

---

## Metadata leakage

### SD-001 — Gossip and sync frames expose social graph metadata

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | `mycelium-node` gossipsub, bloom sync (`SyncBloom`, `SyncIds`), scoped topics |
| **Exploitation scenario** | Passive observer on LAN, ISP path, or relay circuit infers who talks to whom, which scopes are active, and message IDs / timing even when payloads are encrypted |
| **Likelihood** | High (default mesh + Internet bootstrap) |
| **Impact** | Relationship mapping, event correlation, targeting for follow-on attacks |
| **Current mitigations** | E2E for direct chat when keys exchanged; scoped subscription filter on receive |
| **Missing mitigations** | Padding, constant-rate traffic, encrypted topic names, private information retrieval for sync |
| **Proposed architecture changes** | Metadata-minimized sync (PIR or epoch buckets); optional “paranoid mode” with fixed cell sizes |
| **Implementation complexity** | L (6–12 months research + engineering) |
| **Beta-blocker** | **no** (document + user warning) |

### SD-002 — MeshCoin balance queries leak economic metadata

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | `mycelium-coin`, gossip scope `mycelium/coin/v1` |
| **Exploitation scenario** | Observer correlates balance probes / witness gossip with peer IDs and timing |
| **Likelihood** | Medium |
| **Impact** | Wealth inference, extortion targeting |
| **Current mitigations** | MXC marked experimental; no production monetary claims |
| **Missing mitigations** | Private balance proofs, rate limits, encrypted coin gossip |
| **Proposed architecture changes** | Delayed / aggregated witness gossip; opt-out of balance gossip |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

### SD-003 — Relay HTTP status exposes operational telemetry

| Field | Value |
|-------|-------|
| **Severity** | Low |
| **Affected systems** | `mycelium-relay` (`:8080` `/health`) |
| **Exploitation scenario** | Attacker monitors peer counts, relay load, deploy times for infrastructure mapping |
| **Likelihood** | High |
| **Impact** | Aids DDoS timing and infrastructure mapping |
| **Current mitigations** | No message content on HTTP surface |
| **Missing mitigations** | Auth-gated metrics, minimal public surface |
| **Proposed architecture changes** | Separate ops plane; public health = boolean only |
| **Implementation complexity** | S |
| **Beta-blocker** | **no** |

### SD-004 — PeerInfo and ScopeAnnounce reveal capabilities

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | `WireMessage::PeerInfo`, `ScopeAnnounce` |
| **Exploitation scenario** | Malicious peer harvests enc pubkeys, display names, subscribed scopes for profiling |
| **Likelihood** | High |
| **Impact** | Capability fingerprinting, spear-phishing with display names |
| **Current mitigations** | Enc pubkey required for E2E; display name user-chosen |
| **Missing mitigations** | Minimal handshake mode; rotateable pseudonyms |
| **Proposed architecture changes** | Lazy PeerInfo; scope bucketing instead of exact lists |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

---

## Sybil attacks

### SD-010 — No global identity cost for mesh participation

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | libp2p peer layer, reputation (`PeerReputationSnapshot`), MeshCoin witnesses |
| **Exploitation scenario** | Adversary spins thousands of libp2p identities to flood gossip, skew witnesses, eclipse victims |
| **Likelihood** | High at public beta |
| **Impact** | Spam amplification, false witness quorums, routing manipulation |
| **Current mitigations** | Strike-based throttle (3 strikes → 5 min); hop limits; bloom sync caps |
| **Missing mitigations** | Proof-of-work / proof-of-personhood / stake for peer admission |
| **Proposed architecture changes** | MeshCoin-backed identity bonds (when consensus exists); CAPTCHA only for Internet path |
| **Implementation complexity** | L |
| **Beta-blocker** | **no** (disclaim; rate limits for beta) |

### SD-011 — MeshCoin witness quorum is locally gamed

| Field | Value |
|-------|-------|
| **Severity** | Critical (if MXC treated as value) |
| **Affected systems** | `mycelium-coin` ledger, witness counting |
| **Exploitation scenario** | Attacker confirms own txs with Sybil witnesses; victim accepts 3/3 from colluding peers |
| **Likelihood** | High |
| **Impact** | Double-spend from victim’s view; fake balances |
| **Current mitigations** | Experimental disclaimer; local-only ledger view |
| **Missing mitigations** | BFT consensus, witness allowlists, cross-peer reconciliation |
| **Proposed architecture changes** | Witness reputation graph; checkpoint signed by diverse peer set |
| **Implementation complexity** | L |
| **Beta-blocker** | **yes** (for any “real money” positioning — **no** if MXC stays preview-only) |

---

## Relay abuse

### SD-020 — Public relay is a choke point and observation point

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | `mycelium-relay`, `bootstrap.rs`, connectivity probe |
| **Exploitation scenario** | Operator or compromised Fly.io account logs circuit endpoints, drops traffic, targets users by IP at TCP layer |
| **Likelihood** | Medium |
| **Impact** | Censorship, correlation, selective delivery failure |
| **Current mitigations** | libp2p relay v2 limits (circuits, bandwidth); E2E inside payloads |
| **Missing mitigations** | Multi-operator relay pool, client-side relay rotation, Tor/Nym bridge |
| **Proposed architecture changes** | Community relay registry; automatic failover; onion transport |
| **Implementation complexity** | M–L |
| **Beta-blocker** | **no** (document trust model) |

### SD-021 — Relay does not understand app semantics (blind forward)

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | `mycelium-relay` vs `mycelium-node` |
| **Exploitation scenario** | Abuse relay purely as free bandwidth for arbitrary libp2p protocols if misconfigured |
| **Likelihood** | Low–Medium |
| **Impact** | Cost exhaustion, reputation damage to project |
| **Current mitigations** | Circuit limits in relay `main.rs` |
| **Missing mitigations** | Allowlist protocols; Mycelium-only relay behaviour |
| **Proposed architecture changes** | “Relay repo parity” — app-aware policy in companion relay repo |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

### SD-022 — Connectivity probe pins to single host

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | `connectivity.rs`, hardcoded `mycelium-relay.fly.dev:4001` |
| **Exploitation scenario** | DNS / hosting takedown forces global “mesh-only” or pushes users to attacker bootstrap |
| **Likelihood** | Medium |
| **Impact** | Network partition, downgrade to attacker-controlled bootstrap |
| **Current mitigations** | User-editable bootstrap list; mDNS LAN |
| **Missing mitigations** | Multiple independent probes; signed bootstrap bundle |
| **Proposed architecture changes** | Bootstrap transparency log (CONIKS-style lite) |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

---

## Spam amplification

### SD-030 — Unsigned `WireMessage::Data` still accepted

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | `node.rs` `handle_incoming_direct` |
| **Exploitation scenario** | Botnet injects unlimited unsigned direct messages; nodes ingest and flood relays |
| **Likelihood** | High (pre-mitigation) |
| **Impact** | Battery drain, storage fill, user harassment |
| **Current mitigations** | **Mitigated** — `UNSIGNED_GRACE_PERIOD_UNTIL_MS = 0`; unsigned `Data` hard-rejected at ingest |
| **Missing mitigations** | None for beta gate (do not re-enable grace period) |
| **Proposed architecture changes** | Keep fail-closed; optional protocol version advertise in PeerInfo |
| **Implementation complexity** | S–M |
| **Beta-blocker** | **no** (mitigated) |

### SD-031 — Flood to unknown destination fans out to all peers

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | `send_direct_internal`, forwarding when target not in `known_peers` |
| **Exploitation scenario** | Attacker sends to non-existent peer ID → message queued to every connected peer (relay amplification) |
| **Likelihood** | Medium (pre-mitigation) |
| **Impact** | O(n) network amplification per message |
| **Current mitigations** | **Mitigated** — trust-scored relay selection with **max fanout 3** (`security.rs` / SD-031) |
| **Missing mitigations** | Authenticated routing hints / DHT (hardening, not beta gate) |
| **Proposed architecture changes** | DHT or trusted introducer routing |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** (mitigated) |

### SD-032 — Gossip bulletin/mail scopes lack per-author rate limits

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | `mycelium-app` bulletin, scoped gossip |
| **Exploitation scenario** | Sybil floods city-scope bulletin with megabyte posts |
| **Likelihood** | Medium |
| **Impact** | Storage exhaustion, UI DoS |
| **Current mitigations** | Scope subscription filter; ingest dedup |
| **Missing mitigations** | Per-scope rate limit; size cap on gossip payload |
| **Proposed architecture changes** | Signed scope moderator keys; PoW per post (optional) |
| **Implementation complexity** | S–M |
| **Beta-blocker** | **no** |

---

## Malicious mini-apps

### SD-040 — CSP allows `unsafe-inline` script

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | `MiniAppWebView.kt`, `MiniApp.svelte` |
| **Exploitation scenario** | Malicious installed mini-app XSS → calls bridge APIs with user-granted permissions |
| **Likelihood** | Medium (requires user install) |
| **Impact** | Send messages, request payments, read storage, post bulletins |
| **Current mitigations** | **Mitigated** — nonce-only `script-src`; inline scripts rejected at install; bridge still permission-gated |
| **Missing mitigations** | Optional per-bundle script hashes (hardening) |
| **Proposed architecture changes** | Keep nonce-only default; reject regress to `unsafe-inline` |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** (mitigated) |

### SD-041 — Sideload install skips listing signature verification

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | `miniapp/store.rs` `install()` |
| **Exploitation scenario** | User installs ZIP from QR / file share → trojan mini-app with full declared permissions |
| **Likelihood** | Medium |
| **Impact** | Bridge abuse as above |
| **Current mitigations** | **Mitigated** — sideload requires valid `mycelium-bundle.sig.json`; path traversal check; size cap |
| **Missing mitigations** | Developer key pinning UX (hardening) |
| **Proposed architecture changes** | Keep signature mandatory; improve trust UX |
| **Implementation complexity** | S |
| **Beta-blocker** | **no** (mitigated) |

### SD-042 — Permission manifest not enforced at runtime granularity

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | `bridge_host.rs`, `manifest.rs` |
| **Exploitation scenario** | App declares `Payments` + `Messaging`; user never sees granular consent |
| **Likelihood** | High |
| **Impact** | Over-privileged third-party apps |
| **Current mitigations** | Static permission enum checks per method |
| **Missing mitigations** | Install-time permission review UI; runtime permission upgrades |
| **Proposed architecture changes** | Android-style permission prompts per sensitive call |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

---

## Sandbox escape

### SD-050 — Android WebView is not a security boundary

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | Android `WebView`, system WebView CVEs |
| **Exploitation scenario** | 0-day in Chromium WebView → escape to app process → read `EncryptedSharedPreferences`, FFI keys |
| **Likelihood** | Low per version; **non-zero over app lifetime** |
| **Impact** | Full app compromise |
| **Current mitigations** | Network isolation; no file URLs; minimal WebSettings |
| **Missing mitigations** | Custom WebView update cadence; mini-apps in separate process (isolatedProcess) |
| **Proposed architecture changes** | Render mini-apps in `:sandbox` process with Binder IPC to bridge |
| **Implementation complexity** | L |
| **Beta-blocker** | **no** (industry-standard residual risk — document) |

### SD-051 — Desktop `postMessage` uses target `*`

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | `MiniApp.svelte`, `miniapp_get_entry_html` shim |
| **Exploitation scenario** | Malicious script in parent page (if XSS elsewhere) intercepts bridge messages |
| **Likelihood** | Low |
| **Impact** | Bridge call forgery / response injection |
| **Current mitigations** | `e.source === iframeEl.contentWindow` check on receive |
| **Missing mitigations** | Fixed `targetOrigin`; per-session channel nonce |
| **Proposed architecture changes** | `postMessage(..., 'tauri://miniapp')` + origin validation |
| **Implementation complexity** | S |
| **Beta-blocker** | **no** |

---

## Replay attacks

### SD-060 — No global message nonce / replay window at ingest

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | `IngestPipeline`, `SeenCache` |
| **Exploitation scenario** | Captured signed `Data` or `EncryptedDirect` re-injected hours later; dedup only by message id |
| **Likelihood** | Medium |
| **Impact** | Duplicate deliveries, bulletin reposts, witness replay |
| **Current mitigations** | `ContentId` dedup; TTL expiry |
| **Missing mitigations** | Short replay window; sliding window of seen ids per author |
| **Proposed architecture changes** | Envelope `nonce` + monotonic author counter |
| **Implementation complexity** | S |
| **Beta-blocker** | **no** |

### SD-061 — MeshCoin tx replay across peers

| Field | Value |
|-------|-------|
| **Severity** | High (if MXC has value) |
| **Affected systems** | `mycelium-coin` ledger |
| **Exploitation scenario** | Same signed tx gossiped twice; victim ledger applies twice if nonce not checked globally |
| **Likelihood** | Medium |
| **Impact** | Inflation / double credit |
| **Current mitigations** | Local nonce in tx struct (verify in `ledger.rs`) |
| **Missing mitigations** | Network-wide nonce uniqueness; checkpoint rejection |
| **Proposed architecture changes** | Distributed nonce registry per address |
| **Implementation complexity** | M |
| **Beta-blocker** | **yes** (if MXC beyond preview) |

---

## Traffic analysis

### SD-070 — No padding or traffic shaping

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | All transports |
| **Exploitation scenario** | ISP / relay correlates packet sizes and timing to message types (chat vs coin vs sync) |
| **Likelihood** | High |
| **Impact** | Deanonymization of users and actions |
| **Current mitigations** | None substantive |
| **Missing mitigations** | Constant-rate cells, cover traffic, obfuscated lengths |
| **Proposed architecture changes** | Optional cover traffic mode; batch-and-flush scheduler |
| **Implementation complexity** | L |
| **Beta-blocker** | **no** |

### SD-071 — Long-lived libp2p connections fingerprint users

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | libp2p TCP/QUIC sessions |
| **Exploitation scenario** | Persistent connection mapping to residential IP over days |
| **Likelihood** | High |
| **Impact** | Accountability for mesh activity |
| **Current mitigations** | Intermittent mesh; multiple peers |
| **Missing mitigations** | Periodic circuit rotation; ephemeral peer sub-keys |
| **Proposed architecture changes** | Rotate transport peer sub-identity daily |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

---

## Identity spoofing

### SD-080 — Gossip path re-signs with local libp2p key

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | `node.rs` scoped receive handler |
| **Exploitation scenario** | Node receives gossip, re-wraps payload with `Envelope::sign(local_key)` while `from_peer` is upstream relay — downstream peers may trust wrong author binding |
| **Likelihood** | Medium (pre-mitigation) |
| **Impact** | Author confusion, false attribution, bypass of author-based policy |
| **Current mitigations** | **Mitigated** — gossip relay bodies stored/forwarded unsigned; inner payload carries its own auth |
| **Missing mitigations** | None for beta gate |
| **Proposed architecture changes** | Keep opaque forward; document author trust model |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** (mitigated) |

### SD-081 — Plaintext chat fallback when enc key missing

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | `mycelium-app/node.rs` `send_chat_encrypted` |
| **Exploitation scenario** | MITM or race before PeerInfo → app sends **unencrypted** chat with user believing E2E |
| **Likelihood** | Medium (pre-mitigation) |
| **Impact** | Content disclosure |
| **Current mitigations** | **Mitigated** — fail-closed send until enc key present |
| **Missing mitigations** | None for beta gate |
| **Proposed architecture changes** | Keep fail-closed; optional explicit unsafe override only behind UX gate |
| **Implementation complexity** | S |
| **Beta-blocker** | **no** (mitigated) |

### SD-082 — Display name not bound to cryptographic identity

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | Chat UI, `PeerInfo` |
| **Exploitation scenario** | Attacker sets display name “Alice (official)” with different peer ID |
| **Likelihood** | High |
| **Impact** | Social engineering |
| **Current mitigations** | Peer ID shown in technical screens |
| **Missing mitigations** | Verified handles; cross-sign display name in signed envelope |
| **Proposed architecture changes** | Optional signed profile attestation |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

---

## Malicious bootstrap peers

### SD-090 — User-supplied bootstrap strings are trusted

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | Android settings, `NodeConfig.bootstrap_peers` |
| **Exploitation scenario** | Phishing QR gives attacker multiaddr → victim dials attacker first → eclipse / MITM |
| **Likelihood** | Medium |
| **Impact** | Full traffic capture for unsigned frames |
| **Current mitigations** | Default baked-in relay peer ID |
| **Missing mitigations** | Bootstrap pin set in app release; warn on custom bootstrap |
| **Proposed architecture changes** | Signed bootstrap manifest shipped with app updates |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

### SD-091 — Embedded relay peer ID stale after redeploy

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | `bootstrap.rs` |
| **Exploitation scenario** | Attacker registers old peer ID on new infra; users with old app versions connect to wrong host |
| **Likelihood** | Low–Medium |
| **Impact** | Partition or MITM if combined with DNS control |
| **Current mitigations** | Documented `curl` update procedure |
| **Missing mitigations** | DNSSEC + HTTPS bootstrap manifest auto-update |
| **Proposed architecture changes** | Remote config for bootstrap with signature |
| **Implementation complexity** | S–M |
| **Beta-blocker** | **no** |

---

## Trust model weaknesses

### SD-100 — No forward secrecy for E2E keys

| Field | Value |
|-------|-------|
| **Severity** | High → **Medium (partial)** |
| **Affected systems** | v0: `crypto/mod.rs` static ECDH; v1: [`crypto/ratchet.rs`](../crates/mycelium-core/src/crypto/ratchet.rs) |
| **Exploitation scenario** | Long-term key compromise decrypts v0 and pre-bootstrap v1 traffic; v1 session messages after ratchet steps protected |
| **Likelihood** | Medium |
| **Impact** | Retroactive disclosure (reduced for v1 peers) |
| **Current mitigations** | v1 Double Ratchet ([ADR 004](./adr/004-double-ratchet-e2e.md)); v0 fallback for interop; documented in `KNOWN_LIMITATIONS.md` |
| **Missing mitigations** | Group ratchet; SPK rotation policy; ContactHello prekey for instant v1 |
| **Proposed architecture changes** | MLS for groups; optional libsignal alignment |
| **Implementation complexity** | L (v1 done; groups remain) |
| **Beta-blocker** | **no** (document) |

### SD-101 — Group security = single PSK in invite QR

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | Groups, `EncryptedGroup` |
| **Exploitation scenario** | Leaked invite QR → permanent group read/write for all historical messages |
| **Likelihood** | Medium |
| **Impact** | Full group compromise |
| **Current mitigations** | UI warning on invite share |
| **Missing mitigations** | Epoch keys; member-specific keys; expiring invites |
| **Proposed architecture changes** | MLS or Sender Keys with rotation |
| **Implementation complexity** | L |
| **Beta-blocker** | **no** |

### SD-102 — Trust-on-first-use for peer enc keys

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | `peer_x25519` map |
| **Exploitation scenario** | First PeerInfo from malicious relay poisons key → MITM all subsequent E2E |
| **Likelihood** | Medium |
| **Impact** | E2E break |
| **Current mitigations** | Inner wrap binds logical author inside ciphertext |
| **Missing mitigations** | Safety numbers; key change alerts |
| **Proposed architecture changes** | Out-of-band fingerprint verification UI |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

---

## Device compromise

### SD-110 — Panic wipe is delete-tree only

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | `SettingsScreen.kt`, desktop `panic_wipe` |
| **Exploitation scenario** | Forensic recovery of flash after panic wipe |
| **Likelihood** | Medium (targeted) |
| **Impact** | Message / key recovery |
| **Current mitigations** | Deletes `filesDir/mycelium`, prefs, storage key |
| **Missing mitigations** | Secure delete API; SQLcipher page wipe; RAM zeroization |
| **Proposed architecture changes** | Platform secure erase hooks where available |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

### SD-111 — No remote revocation / session invalidation

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | Protocol design |
| **Exploitation scenario** | Stolen phone continues to receive/gossip until manual panic wipe |
| **Likelihood** | Medium |
| **Impact** | Ongoing impersonation |
| **Current mitigations** | None |
| **Missing mitigations** | Optional “revocation gossip” signed by user key |
| **Proposed architecture changes** | User-published revocation epoch in signed profile |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

---

## Rooted Android risks

### SD-120 — Root bypasses Android Keystore / EncryptedSharedPreferences

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | `StorageKeyHelper.kt`, JNI FFI |
| **Exploitation scenario** | Rooted device hooks `miniappBridgeCall` / reads master key from memory |
| **Likelihood** | Medium (beta demographic) |
| **Impact** | Full node impersonation |
| **Current mitigations** | At-rest vault encryption; E2E for messages |
| **Missing mitigations** | Root detection + degraded mode; hardware-backed keys only |
| **Proposed architecture changes** | StrongBox requirement for `storage_key`; refuse mesh on detected root |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** (warn in docs) |

### SD-121 — Magisk hide / kernel hooks invisible to app

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | Android client |
| **Exploitation scenario** | Sophisticated malware exfiltrates `EncryptionKeypair` after recent zeroize (memory scrape) |
| **Likelihood** | Low–Medium |
| **Impact** | Key theft |
| **Current mitigations** | `secret_raw.zeroize()` on drop |
| **Missing mitigations** | mlock sensitive buffers; shorten key lifetime in process |
| **Proposed architecture changes** | Keys only in native secure enclave bridge |
| **Implementation complexity** | L |
| **Beta-blocker** | **no** |

---

## Memory extraction

### SD-130 — Secrets live in process RAM during use

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | `EncryptionKeypair`, libp2p `Keypair`, sled pages |
| **Exploitation scenario** | Cold boot attack, crash dump, `/proc/mem`, debugger attach |
| **Likelihood** | Medium (targeted) |
| **Impact** | Identity + message decryption |
| **Current mitigations** | Zeroize on `EncryptionKeypair` drop |
| **Missing mitigations** | mlock; guard pages; hardware TEE |
| **Proposed architecture changes** | Rust `zeroize` on all key material; minimal copy count |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

### SD-131 — Desktop fallback master key derived from db_path

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | `secrets.rs` keyring fallback |
| **Exploitation scenario** | Predictable vault key on shared machine → decrypt `.secrets/*.enc` |
| **Likelihood** | Medium (desktop beta, pre-mitigation) |
| **Impact** | Identity theft |
| **Current mitigations** | **Mitigated** — refuse new weak db_path-derived fallback; OS keyring / stronger path required |
| **Missing mitigations** | Optional passphrase UX polish |
| **Proposed architecture changes** | Keep refusing weak fallback |
| **Implementation complexity** | S |
| **Beta-blocker** | **no** (mitigated) |

---

## Protocol downgrade risks

### SD-140 — Unsigned wire `Data` is legacy-compatible

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | `node.rs` |
| **Exploitation scenario** | Attacker forces peer to speak “legacy” by sending unsigned frames; software accepts |
| **Likelihood** | High (pre-mitigation) |
| **Impact** | MITM injection without signatures |
| **Current mitigations** | **Mitigated** — same as SD-030 (unsigned `Data` rejected; EncryptedDirect requires sig) |
| **Missing mitigations** | Optional `min_protocol_version` advertise |
| **Proposed architecture changes** | Feature flag in PeerInfo handshake |
| **Implementation complexity** | S |
| **Beta-blocker** | **no** (mitigated) |

### SD-141 — Unencrypted chat fallback is a downgrade

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | `send_chat_encrypted` |
| **Exploitation scenario** | Block PeerInfo → force plaintext |
| **Likelihood** | Medium (pre-mitigation) |
| **Impact** | Content exposure |
| **Current mitigations** | **Mitigated** — same as SD-081 (fail-closed send) |
| **Missing mitigations** | None for beta gate |
| **Proposed architecture changes** | Same as SD-081 |
| **Implementation complexity** | S |
| **Beta-blocker** | **no** (mitigated) |

---

## Dependency supply-chain risks

### SD-150 — Large dependency trees (libp2p, WebView, Tauri)

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | `Cargo.lock`, Gradle, npm/Tauri |
| **Exploitation scenario** | Compromised crate or npm package in build pipeline |
| **Likelihood** | Low |
| **Impact** | RCE at build or runtime |
| **Current mitigations** | Dependabot config present |
| **Missing mitigations** | `cargo vet`, SBOM, reproducible builds, signed releases |
| **Proposed architecture changes** | Supply-chain policy in CI |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

### SD-151 — UniFFI / JNI boundary is attack surface

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | `mycelium-ffi`, Android bindings |
| **Exploitation scenario** | Memory corruption in FFI → RCE in Kotlin process |
| **Likelihood** | Low |
| **Impact** | App compromise |
| **Current mitigations** | Rust memory safety for core logic |
| **Missing mitigations** | Fuzz FFI entrypoints |
| **Proposed architecture changes** | Structured input validation on all FFI strings |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

---

## Wallet compromise

### SD-160 — MeshCoin hot wallet stores spending keys locally

| Field | Value |
|-------|-------|
| **Severity** | Critical (if MXC = value) |
| **Affected systems** | `mycelium-coin`, hot wallet |
| **Exploitation scenario** | Malware on device drains hot cache via signed txs |
| **Likelihood** | High |
| **Impact** | Fund loss |
| **Current mitigations** | Cache cap; cold wallet separation; experimental banner |
| **Missing mitigations** | Hardware wallet integration; tx confirmation UI |
| **Proposed architecture changes** | Watch-only hot mode default |
| **Implementation complexity** | M |
| **Beta-blocker** | **yes** (if real funds) / **no** (preview MXC) |

### SD-161 — Faucet / genesis mint is local trust

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | `ledger.rs` `genesis_credit`, faucet API |
| **Exploitation scenario** | Modified client mints arbitrary balances offline |
| **Likelihood** | High (dev builds) |
| **Impact** | False sense of economic security |
| **Current mitigations** | Local ledger only; not global |
| **Missing mitigations** | Remove faucet in release; signed faucet server |
| **Proposed architecture changes** | Testnet flag at compile time |
| **Implementation complexity** | S |
| **Beta-blocker** | **no** |

---

## Key exfiltration

### SD-170 — Relay server identity on disk without vault

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | `mycelium-relay` |
| **Exploitation scenario** | Fly.io disk snapshot leaks relay key → impersonate relay |
| **Likelihood** | Low |
| **Impact** | Network-wide MITM for users trusting that peer ID |
| **Current mitigations** | Limited app semantics on relay |
| **Missing mitigations** | HSM / sealed secrets for relay |
| **Proposed architecture changes** | Vault parity with node |
| **Implementation complexity** | S |
| **Beta-blocker** | **no** |

### SD-171 — Backup tools copy `filesDir/mycelium`

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | Android backup, adb backup |
| **Exploitation scenario** | Google account backup or adb extracts encrypted vault + DB |
| **Likelihood** | Medium (pre-mitigation) |
| **Impact** | Offline brute-force of master key |
| **Current mitigations** | **Mitigated** — `android:allowBackup="false"` + backup exclusion rules; encrypted vault |
| **Missing mitigations** | None for beta gate |
| **Proposed architecture changes** | Keep manifest hardening |
| **Implementation complexity** | S |
| **Beta-blocker** | **no** (mitigated) |

---

## Censorship attacks

### SD-180 — Single-domain bootstrap enables DNS censorship

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | `mycelium-relay.fly.dev` |
| **Exploitation scenario** | State blocks fly.dev or DNS poison → users stuck offline |
| **Likelihood** | Medium (jurisdiction-dependent) |
| **Impact** | Denial of mesh Internet path |
| **Current mitigations** | Pure mesh mDNS still works locally |
| **Missing mitigations** | Pluggable transports; peer-to-peer bootstrap |
| **Proposed architecture changes** | BLE bootstrap (roadmap); multi-domain mirrors |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

### SD-181 — Relay operator can drop circuits selectively

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | libp2p relay |
| **Exploitation scenario** | Block circuits by peer ID hash (targeted censorship) |
| **Likelihood** | Medium |
| **Impact** | Partition |
| **Current mitigations** | Alternate paths if multi-homed |
| **Missing mitigations** | Multi-relay mandatory |
| **Proposed architecture changes** | Client tries N relays in parallel |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

---

## Deanonymization

### SD-190 — Stable libp2p PeerId is permanent identifier

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | Identity layer |
| **Exploitation scenario** | Long-term tracking across networks, QR re-scans, bulletin posts |
| **Likelihood** | High |
| **Impact** | Pseudonymity broken |
| **Current mitigations** | User can panic wipe (new identity); transport PeerId rotation with E2E re-announce (SD-190 spike) |
| **Missing mitigations** | Pairwise IDs per chat context; full fleet MYBR wire migration |
| **Proposed architecture changes** | Pairwise IDs (Signal-style) for direct chat |
| **Implementation complexity** | L |
| **Beta-blocker** | **no** |

### SD-191 — QR codes leak peer IDs in physical world

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | Share screens, group invites |
| **Exploitation scenario** | CCTV captures QR → link physical person to PeerId |
| **Likelihood** | Medium |
| **Impact** | Real-world deanonymization |
| **Current mitigations** | None |
| **Missing mitigations** | Expiring QR tokens; proximity-only BLE exchange |
| **Proposed architecture changes** | Short-lived introduction tokens |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

---

## Emergency shutdown risks

### SD-200 — No kill switch for compromised network

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | Operations |
| **Exploitation scenario** | Active exploit in wild → team cannot force clients to stop networking |
| **Likelihood** | Low |
| **Impact** | Prolonged harm |
| **Current mitigations** | Store takedown only |
| **Missing mitigations** | Signed remote config “pause mesh” |
| **Proposed architecture changes** | Check-in endpoint with signed halt flag |
| **Implementation complexity** | S |
| **Beta-blocker** | **no** |

### SD-201 — Panic wipe requires user physical access

| Field | Value |
|-------|-------|
| **Severity** | Low |
| **Affected systems** | Client |
| **Exploitation scenario** | Device seized while unlocked → no remote wipe |
| **Likelihood** | Medium |
| **Impact** | Data exposure |
| **Current mitigations** | Panic wipe in settings |
| **Missing mitigations** | Duress PIN; auto-wipe after N failed unlocks |
| **Proposed architecture changes** | Optional duress mode |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

---

## Legal seizure risks

### SD-210 — Operator can be compelled to log relay IPs

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | `mycelium-relay`, hosting provider |
| **Exploitation scenario** | Court order to Fly.io for connection logs |
| **Likelihood** | Jurisdiction-dependent |
| **Impact** | User identification |
| **Current mitigations** | Policy: no IP logging (must be operationalized) |
| **Missing mitigations** | Minimal logging architecture; jurisdiction choice |
| **Proposed architecture changes** | Privacy policy + technical no-log config audit |
| **Implementation complexity** | S (legal) + M (technical) |
| **Beta-blocker** | **no** |

### SD-211 — User devices subject to border search

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | Clients |
| **Exploitation scenario** | Device unlocked at border → full history read |
| **Likelihood** | Medium |
| **Impact** | Content + contact exposure |
| **Current mitigations** | Panic wipe; E2E |
| **Missing mitigations** | Travel mode; hidden vault |
| **Proposed architecture changes** | Decoy profile |
| **Implementation complexity** | L |
| **Beta-blocker** | **no** |

---

## Infrastructure concentration risks

### SD-220 — Single relay on single PaaS

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | Deployment |
| **Exploitation scenario** | Fly.io outage or account ban → global Internet bootstrap degradation |
| **Likelihood** | Medium |
| **Impact** | Service unavailability |
| **Current mitigations** | LAN mesh continues |
| **Missing mitigations** | Multi-cloud relays; community hosting kit |
| **Proposed architecture changes** | Relay federation guide |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** |

### SD-221 — Embedded peer ID couples app release to infra deploy

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | `bootstrap.rs` |
| **Exploitation scenario** | Emergency relay migration requires app store update |
| **Likelihood** | Medium |
| **Impact** | Slow incident response |
| **Current mitigations** | User custom bootstrap |
| **Missing mitigations** | Remote signed bootstrap config |
| **Proposed architecture changes** | Same as SD-091 |
| **Implementation complexity** | S–M |
| **Beta-blocker** | **no** |

---

## Moderation impossibility

### SD-230 — No central moderation for bulletin scopes

| Field | Value |
|-------|-------|
| **Severity** | Medium (legal/reputational) |
| **Affected systems** | Bulletin, gossip |
| **Exploitation scenario** | Illegal content propagated mesh-wide; operator has no takedown lever |
| **Likelihood** | High at scale |
| **Impact** | App store removal, criminal liability risk |
| **Current mitigations** | TTL expiry; local delete only |
| **Missing mitigations** | Client-side block lists; scope owner keys |
| **Proposed architecture changes** | Signed moderation events from scope admins |
| **Implementation complexity** | M |
| **Beta-blocker** | **no** (legal review **yes**) |

### SD-231 — No reporting flow to operator

| Field | Value |
|-------|-------|
| **Severity** | Low |
| **Affected systems** | Product |
| **Exploitation scenario** | Users encounter abuse with no in-app reporting |
| **Likelihood** | High |
| **Impact** | Reputation harm |
| **Current mitigations** | None |
| **Missing mitigations** | Export report bundle for user to send to authorities voluntarily |
| **Proposed architecture changes** | Local block + optional share hash to app store curator |
| **Implementation complexity** | S |
| **Beta-blocker** | **no** |

---

## Malicious offline payload propagation

### SD-240 — QR / file share installs unsigned mini-app bundles

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | Mini-app install path |
| **Exploitation scenario** | Disinformation campaign distributes ZIP via mesh bulletin → sideload malware |
| **Likelihood** | Medium (pre-mitigation) |
| **Impact** | Mass bridge abuse among users who install |
| **Current mitigations** | **Mitigated** — same as SD-041 (`mycelium-bundle.sig.json` required for sideload) |
| **Missing mitigations** | None for beta gate |
| **Proposed architecture changes** | Same as SD-041 |
| **Implementation complexity** | S |
| **Beta-blocker** | **no** (mitigated) |

### SD-241 — Mesh sync replicates harmful blobs to all peers

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Affected systems** | `SyncData`, store |
| **Exploitation scenario** | Large blob stored in sled replicated via bloom sync |
| **Likelihood** | Medium |
| **Impact** | Storage DoS; legal liability for possessed data |
| **Current mitigations** | TTL; GC |
| **Missing mitigations** | Content-type allowlist; max blob per author |
| **Proposed architecture changes** | Size quotas per peer |
| **Implementation complexity** | S |
| **Beta-blocker** | **no** |

### SD-242 — Group invite encodes long-lived group key

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Affected systems** | Groups offline QR |
| **Exploitation scenario** | Invite forwarded beyond intended audience → permanent access |
| **Likelihood** | High |
| **Impact** | Confidentiality loss |
| **Current mitigations** | Warning string |
| **Missing mitigations** | Expiring invites; admin revoke |
| **Proposed architecture changes** | Epoch rotation on member change |
| **Implementation complexity** | M–L |
| **Beta-blocker** | **no** |

---

## Beta-blocker summary (must resolve or sign off)

| ID | Title | Beta-blocker | Status (Aug 2026) |
|----|-------|--------------|-------------------|
| SD-030 | Unsigned `WireMessage::Data` accepted | **no** | **Mitigated** — grace period removed |
| SD-031 | Flood amplification to all peers | **no** | **Mitigated** — max fanout 3 |
| SD-040 | CSP `unsafe-inline` | **no** | **Mitigated** — nonce-only; inline scripts rejected at install |
| SD-041 | Sideload install without signature | **no** | **Mitigated** — sideload requires `mycelium-bundle.sig.json` |
| SD-080 | Gossip re-sign with local key | **no** | **Mitigated** — unsigned gossip storage |
| SD-081 | Plaintext chat fallback | **no** | **Mitigated** — fail-closed send |
| SD-131 | Desktop weak vault fallback | **no** | **Mitigated** — refuse new weak fallback |
| SD-140 | Protocol downgrade unsigned Data | **no** | **Mitigated** (same as SD-030) |
| SD-141 | Unencrypted chat downgrade | **no** | **Mitigated** (same as SD-081) |
| SD-171 | Android backup exfiltration | **no** | **Mitigated** |
| SD-240 | Unsigned offline mini-app payloads | **no** | **Mitigated** (same as SD-041) |
| SD-032 | Per-author gossip/direct rate limits | no | **Mitigated** — `IngestGuard` in `mycelium-node` |
| SD-060 | Replay window at ingest | no | **Mitigated** — 30 min freshness + author id window |
| SD-011 | MeshCoin Sybil witnesses | **yes** only if MXC ≠ preview | Open (preview-only OK) |

**Recently mitigated (do not regress):** EncryptedDirect mesh signature hard-reject; EncryptionKeypair zeroize; mini-app network block + strict CSP; Android lockscreen-safe chat/mail notifications; POST_NOTIFICATIONS runtime prompt + denied banner; official mini-app pack/sign tooling; Phase-1 ingest rate limits and replay window (SD-032, SD-060).

---

## Maintenance

- Review this register **every release** and after any incident.
- New features must add rows before merge (security PR template).
- IDs are stable; append new IDs, never reuse.
