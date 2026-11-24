# Mycena Mini-App — Zero-Trust Security Architecture

**Codename:** **Cell** (mini-app runtime)  
**Status:** Target architecture + gap analysis vs today  
**Last updated:** May 2026  
**Companion:** [MINIAPP_PERMISSION_MODEL.md](./MINIAPP_PERMISSION_MODEL.md) · [MINIAPP_ATTACK_SCENARIOS.md](./MINIAPP_ATTACK_SCENARIOS.md) · [MINIAPP_RUNTIME_HARDENING.md](./MINIAPP_RUNTIME_HARDENING.md) · [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) (SD-040–042, SD-240)

---

## 1. Design philosophy

Mini-apps are **untrusted code** running in a **hostile environment** (protest networks, scam stores, sideloaded ZIPs). The host (Mycena node + UI) is the **only** trusted computing base (TCB).

**Zero-trust principles:**

| Principle | Meaning for Cell |
|-----------|------------------|
| **Never trust the bundle** | Signature + hash + manifest policy verified **before** any byte executes |
| **Deny by default** | No API, no storage, no network unless granted |
| **Least privilege** | Capabilities are narrow, time-bound, and revocable |
| **User-in-the-loop** | Dangerous actions need **native** UI, not WebView `confirm()` |
| **Assume compromise** | Sandbox escape attempts are expected; limit blast radius |
| **Offline-first** | Security does not depend on cloud attestation at runtime |

**Inspirations (cherry-picked, not copied):**

| Source | Borrow |
|--------|--------|
| **Browser extensions** | Manifest permissions, isolated worlds |
| **Android** | Install-time + runtime permission prompts |
| **Capability security** | Unforgeable capability tokens per session |
| **Chromium site isolation** | Separate process per app (target) |
| **WASM sandbox** | Future: compute-bound guest code |
| **Tails** | Paranoid defaults, no ambient authority |

---

## 2. Threat model

### 2.1 Adversaries

- Malicious **authors** (trojan features, credential harvesters)
- Malicious **bundles** (ZIP bombs, path traversal, polyglot HTML)
- Malicious **updates** (downgrade, signature strip, hash mismatch)
- **Social engineering** (fake payment QR, impersonation alerts)
- **Network attackers** (blocked by host network policy; app cannot fetch)
- **Privileged malware** on device (root; outside Cell TCB)

### 2.2 Assets

| Asset | Impact if stolen/abused |
|-------|-------------------------|
| libp2p PeerId + enc pubkey | Impersonation, E2E MITM setup |
| MeshCoin keys / balance | Financial loss (preview MXC) |
| Chat / mail / bulletin | Spam, harassment, legal content |
| Per-app storage | Cross-session tracking |
| User attention (QR scan) | Phishing bootstrap / mxcpay |

### 2.3 Trust boundaries (target)

```
┌─────────────────────────────────────────────────────────────┐
│ TCB: Mycena Host (Rust node, native UI, policy engine)     │
│  · Install verifier · Capability issuer · Native prompts       │
└───────────────────────────┬─────────────────────────────────┘
                            │ IPC (capability token + method)
┌───────────────────────────▼─────────────────────────────────┐
│ Untrusted: Mini-app Guest                                    │
│  · HTML/JS today · WASM tomorrow · NO direct FFI            │
│  · NO network · NO filesystem · NO raw PeerId without cap     │
└─────────────────────────────────────────────────────────────┘
```

**Today:** Guest runs in **WebView/iframe same process** — TCB boundary is **weaker** (see [MINIAPP_RUNTIME_HARDENING.md](./MINIAPP_RUNTIME_HARDENING.md)).

---

## 3. Layered architecture (target)

### Layer 0 — Provenance & supply chain

- Signed `mycena-app.json` + **bundle content hash** in `AppStoreListing`
- **Reproducible build** attestation (developer publishes `mxa.sha256` + build recipe)
- **Revocation list** (signed by Mycena curator + optional community keys)
- Offline install: verify against **cached** listing or **pinned** developer key

### Layer 1 — Install gate (static analysis)

Before `AppStore::install`:

1. ZIP structure safe (`bundle.rs` paths)
2. Size ≤ quota (10 MB today → per-tier)
3. Manifest schema valid
4. **Signature:** listing.sig OR inline `manifest.signature` over bundle hash
5. **Hash lock:** `blake3(bundle) == listing.bundle_hash`
6. **Version monotonic:** no downgrade without user override
7. **Permission ceiling:** reject unknown permissions; warn on dangerous sets

### Layer 2 — Runtime sandbox (presentation)

- **Network policy:** deny all (implemented baseline on Android/desktop)
- **CSP:** strict script-src (nonces) — **not yet**
- **Process isolation:** `:miniapp` process (Android) / utility process (desktop) — **not yet**
- **No `eval`**, no dynamic script injection from app HTML

### Layer 3 — Capability plane (API)

- Guest calls `window.mycena.*` → bridge → **`CellPolicy::invoke(cap, method, args)`**
- Each method requires:
  - Manifest permission **or** runtime grant
  - Valid **capability token** (session + nonce)
  - Rate limit / quota check

### Layer 4 — Native UX gate (dangerous ops)

| Action | Must use native UI |
|--------|-------------------|
| Payment / QR | System sheet with amount, recipient, cancel |
| QR scan | Camera overlay; show decoded URL before action |
| Identity reveal | Show PeerId; copy button |
| Bulletin write | Scope + title preview |
| Messaging to new peer | Confirm recipient |

**Rule:** `_bridge_action` hints from Rust are **not** security; host **must** implement or deny.

### Layer 5 — Storage & state

- Per-app **encrypted** sled tree: `miniapp_enc:{app_id}` with HKDF from device master + app_id
- Quotas: keys count, total bytes, single value max
- **No** access to host `chat:`, `bulletin:`, identity vault trees

### Layer 6 — Safe mode & reputation

- **Safe mode:** run app with **deny-all** dangerous caps; read-only bulletin
- **Reputation score** (local): installs, user reports, revocation hits, anomaly rate
- High-risk apps: force safe mode + orange border

---

## 4. Capability tokens (design)

### 4.1 Token structure

```text
CapabilityToken {
  app_id: String,
  permission: Permission,
  session_id: u64,           // rotates on app restart
  expires_at_ms: u64,
  mac: [u8; 32],             // HMAC(device_secret, fields)
}
```

- Issued by host when user grants runtime permission
- Bridge validates MAC before `BridgeHost::handle`
- **Ephemeral:** 15 min default for `Payments`, `Messaging`; `Storage` may be install-persistent

### 4.2 Invocation envelope

```json
{
  "id": 1,
  "method": "messaging.send",
  "args": { "to_peer": "…", "payload": "…" },
  "cap": "base64url(token)"
}
```

Missing/invalid `cap` → error even if manifest lists permission (unless install-time grant recorded).

---

## 5. Secure update chain

```text
Developer builds mxa v1.2.0
  → blake3(bundle) = H
  → sign(manifest || H || version || permissions) with dev key
  → publish AppStoreListing on gossip / ship on bulletin

User updates:
  → fetch listing (signature verified on gossip ingest ✓ today)
  → download bundle bytes
  → verify H' == H and sig
  → if version < installed: reject (downgrade) unless force flag
  → install replaces app_files tree atomically (write temp → rename)
  → bump session_id (invalidate old caps)
```

**Revocation:**

```text
RevocationEntry { app_id, max_version, reason, revoked_at, curator_sig }
```

Host refuses launch/install if `app_id` on revocation list (bundled + gossip `mycena/appstore/revocations`).

---

## 6. Network policy

| Class | Policy |
|-------|--------|
| Raw HTTP(S)/WS | **Deny** (enforced in WebView/iframe) |
| Mesh APIs | Bridge only |
| DNS | N/A |
| Deep links | Deny from guest; host may open after confirm |

**Future WASM guest:** no sockets; host syscalls only.

---

## 7. Resource quotas

| Resource | Default | Paranoid |
|----------|---------|----------|
| Bundle size | 10 MB | 5 MB |
| Storage | 2 MB / app | 512 KB |
| Bridge calls | 60/min | 20/min |
| Bulletin posts | 10/hour | 2/hour |
| Messages sent | 30/hour | 5/hour |
| CPU time | wall 30s/session | 10s |

Exceeded → `ResourceExhausted` error; safe mode auto if repeated.

---

## 8. Mini-App Security SDK (developer-facing)

### 8.1 Goals

- Safe defaults in `mycena-app.json`
- Type-safe JS wrapper with **no** raw bridge access
- Build-time linter for permissions vs API usage
- Reproducible bundle builder CLI

### 8.2 Package layout (proposed)

```text
tools/miniapp-sdk/
  cli/          mxa pack, mxa sign, mxa verify
  js/           @mycena/miniapp-sdk (ESM, no node fs)
  schema/       manifest-v1.schema.json
  templates/    minimal app, payment app
```

### 8.3 Developer-safe API surface

```javascript
// @mycena/miniapp-sdk — wraps window.mycena
import { messaging, storage, identity } from '@mycena/miniapp-sdk';

// Linter ensures:
// - messaging.send only if Messaging declared
// - no eval, no dynamic script
```

### 8.4 Dangerous API classification

See [MINIAPP_PERMISSION_MODEL.md](./MINIAPP_PERMISSION_MODEL.md) §4.

---

## 9. App provenance UI (target)

**Install review screen** (native Compose / Tauri):

- App name, version, developer PeerId (fingerprint)
- Signature status: ✓ valid / ⚠ sideload / ✗ revoked
- Permission chips (color-coded risk)
- Bundle hash (truncated) + “matches store listing”
- Toggle: “Allow in safe mode only”

**Running app chrome:**

- Persistent banner: app id + safe mode indicator
- Menu: revoke permissions, report app, uninstall

---

## 10. Execution modes

| Mode | Description |
|------|-------------|
| **Normal** | Declared permissions + runtime grants |
| **Safe** | Read-only bulletin, util.now, app metadata only |
| **Paranoid host** | Global: all apps safe mode |

---

## 11. WASM sandbox path (Phase 3+)

**Why:** HTML+JS cannot be made fully safe with CSP alone (`unsafe-inline` today).

**Model:**

- Guest = WASM module + WASI subset (no network)
- UI = host-rendered declarative schema (optional)
- Bridge = host function imports matching capability tokens

**Incremental:** keep HTML apps in WebView; new apps may ship `guest.wasm` + manifest flag `runtime: "wasm"`.

---

## 12. Offline-first compatibility

| Requirement | Approach |
|-------------|----------|
| Install without network | File picker + verify sig with **pinned** dev key or cached listing |
| Update offline | Pre-fetched bundle on SD card; same verify path |
| Revocation offline | Bundled revocation snapshot in app release; stale OK with warn |
| Capability tokens | Issued locally; no server |

---

## 13. Mapping to current codebase

| Target layer | Current implementation |
|--------------|------------------------|
| ZIP path safety | `bundle.rs` `is_safe_bundle_path` ✓ |
| Size limit | 10 MB `AppStore::install` ✓ |
| Listing signature on gossip | `node.rs` verify on ingest ✓ |
| Install signature | **Missing** `install()` |
| Hash lock install | **Missing** |
| Bridge permissions | `bridge_host.rs` partial ✓ |
| identity.get gate | **Missing** `Permission::Identity` |
| Network deny | WebView/iframe ✓ (baseline) |
| CSP strict | **Partial** `unsafe-inline` |
| Native payment confirm | **Missing** Android handler |
| Capability tokens | Bridge **session** (C2) + **HMAC runtime caps** (P4/H25) |
| Revocation | **Local sled + gossip** `mycena/appstore/revocations/v1` (P2) |
| Encrypted app storage | **HKDF + ChaCha20** when `storage_key` set (C2) |
| Process isolation | **Android `:miniapp` process** (P2); desktop iframe only |
| Safe mode / reputation | **Local score + safe mode** (P2) |
| Security SDK | **`miniapp-sdk` lint/hash** (C4) |

Full gap list: [MINIAPP_RUNTIME_HARDENING.md](./MINIAPP_RUNTIME_HARDENING.md) §2.

---

## 14. Incremental roadmap

| Phase | Deliverable |
|-------|-------------|
| **C0** | Install verify: sig + bundle_hash; provenance UI |
| **C1** | Runtime prompts (pay, QR, identity); fix permission gaps |
| **C2** | Capability tokens + quotas + encrypted storage |
| **C3** | CSP nonces; isolated process; revocation gossip |
| **C4** | miniapp-sdk CLI + linter; reproducible builds |
| **C5** | WASM runtime option |

Aligns with [SECURITY_ROADMAP.md](./SECURITY_ROADMAP.md) mini-app items.

---

## 15. Success criteria

- No install path executes unsigned bundle in **strict** mode
- Every **dangerous** bridge method has native UX gate
- Red-team mini-app cannot exfiltrate PeerId without grant + prompt
- Phishing QR cannot auto-navigate without scan review
- Documented safe mode usable in hostile environments
