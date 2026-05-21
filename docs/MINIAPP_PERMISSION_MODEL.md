# Mycelium Mini-App — Permission & Capability Model

**Version:** manifest v1 → **capability v2** (target)  
**Companion:** [MINIAPP_SECURITY_ARCHITECTURE.md](./MINIAPP_SECURITY_ARCHITECTURE.md)

---

## 1. Two-phase authorization

| Phase | When | What is decided |
|-------|------|-----------------|
| **Install-time** | User taps Install | Maximum permission **ceiling** baked into manifest |
| **Runtime** | First sensitive API call | Native prompt; issues **capability token** |

**Rule:** Manifest permission = *may request*. Runtime grant = *may execute now*.

Install UI shows **all** manifest permissions. Runtime UI shows **why now** (method + args summary).

---

## 2. Permission catalog (v1 — implemented)

Defined in `crates/mycelium-app/src/miniapp/manifest.rs`:

| Permission | Risk tier | Bridge methods (today) | Install default |
|------------|-----------|------------------------|-----------------|
| `Messaging` | **High** | `messaging.send`, `messaging.subscribe`, `messaging.broadcast` | Prompt |
| `Payments` | **Critical** | `payment.request`, `payment.create_qr`, `payment.get_balance` | Prompt + native sheet |
| `Storage` | **Medium** | `storage.get/set/delete/list` | Prompt (first use) |
| `BulletinRead` | **Low** | `bulletin.get` | Auto-grant optional |
| `BulletinWrite` | **High** | `bulletin.post` | Prompt |
| `PeerDiscovery` | **Medium** | `peers.nearby` | Prompt |
| `Identity` | **High** | `identity.get` (should be) | Prompt |
| `Camera` | **High** | `util.scan_qr` (should require) | Prompt |

### 2.1 Gaps in enforcement (today)

| Permission | Declared | Enforced in `bridge_host.rs` |
|------------|----------|------------------------------|
| `Identity` | ✓ | **NO** — `identity.get` is **public** |
| `Camera` | ✓ | **NO** — `util.scan_qr` unchecked |
| `Messaging` | ✓ | Partial — `broadcast` ignores scope abuse |
| `Payments` | ✓ | Partial — no native confirm on Android |

---

## 3. Permission catalog (v2 — proposed additions)

| Permission | Risk | Methods |
|------------|------|---------|
| `IdentityMinimal` | Low | Pseudonym id only (rotating `app_session_peer`) |
| `IdentityFull` | High | Real PeerId + enc pubkey |
| `MessagingDirect` | High | `messaging.send` only |
| `MessagingBroadcast` | Critical | `messaging.broadcast` (separate prompt) |
| `PaymentsRead` | Medium | `payment.get_balance` |
| `PaymentsWrite` | Critical | QR + request (native) |
| `Clipboard` | Medium | Copy mxcpay URI (future) |
| `Notifications` | Low | Local notifications (future) |

**Principle of split:** Read vs write; direct vs broadcast; minimal vs full identity.

---

## 4. Dangerous API classification

### Tier 0 — Safe (no permission)

| Method | Notes |
|--------|-------|
| `app.get_id` | Opaque app id only |
| `app.get_version` | Public manifest field |
| `util.now` | Coarse clock; rate limited |

### Tier 1 — Sensitive (manifest + soft prompt once)

| Method | Notes |
|--------|-------|
| `storage.*` | Quota enforced |
| `bulletin.get` | Scoped read |
| `messaging.subscribe` | No push pipe yet — low risk |

### Tier 2 — Dangerous (manifest + runtime prompt every session)

| Method | Native gate required |
|--------|---------------------|
| `identity.get` | Show PeerId; user confirms |
| `peers.nearby` | List peers; warn on enumeration |
| `bulletin.post` | Preview title/body/scope |
| `messaging.send` | Show recipient + payload excerpt |
| `util.alert` / `util.confirm` | **Host renders** text; ignore spoof styling |

### Tier 3 — Critical (manifest + per-invocation native sheet)

| Method | Native gate required |
|--------|---------------------|
| `payment.request` | Amount, memo, recipient — **no auto-pay** |
| `payment.create_qr` | Display QR in host overlay; not WebView canvas |
| `messaging.broadcast` | Scope + reach warning |
| `util.scan_qr` | Camera + decode preview + user confirm action |

### Tier 4 — Forbidden (never exposed to guest)

| Capability | Reason |
|------------|--------|
| Raw filesystem | Host paths |
| Raw `miniapp_bridge_call` from JS | Bypass policy |
| `NodeCommand` / FFI | Full node |
| Install other apps | Chain malware |
| Change manifest permissions | Escalation |
| `panic_wipe` | Obvious |
| Direct sled access | Data exfil |

---

## 5. Runtime prompt specification

### 5.1 Prompt record

```text
RuntimeGrant {
  app_id,
  permission,
  granted_at_ms,
  expires_at_ms,
  grant_level: Session | UntilRevoked,
}
```

Stored in host DB: `miniapp_grants:{app_id}` (encrypted).

### 5.2 UI copy templates

| Permission | Title | Body |
|------------|-------|------|
| `Messaging` | Allow messages? | “%app% can send mesh chat on your behalf.” |
| `Payments` | Allow payments? | “%app% can show payment requests and your balance.” |
| `Identity` | Share your identity? | “%app% will see your Peer ID and encryption key.” |
| `Camera` | Use camera? | “%app% wants to scan QR codes.” |

**Anti-phishing:** Display name in prompt must come from **verified manifest**, not bridge `util.alert`.

---

## 6. Capability tokens (binding permissions to calls)

See [MINIAPP_SECURITY_ARCHITECTURE.md](./MINIAPP_SECURITY_ARCHITECTURE.md) §4.

**Policy check order:**

1. Method exists and not Tier 4
2. Permission in manifest ceiling
3. Runtime grant active (or Tier 0/1 auto)
4. Capability token valid MAC + not expired
5. Rate limit / quota
6. Safe mode blocks Tier 2–3

---

## 7. Safe mode matrix

| Method | Normal | Safe mode |
|--------|--------|-----------|
| `storage.get` | ✓ | ✓ (read only) |
| `storage.set` | ✓ | ✗ |
| `bulletin.get` | ✓ | ✓ |
| `bulletin.post` | ✓ | ✗ |
| `messaging.*` | ✓ | ✗ |
| `payment.*` | ✓ | ✗ |
| `identity.get` | ✓ | ✗ (or minimal pseudonym only) |
| `util.scan_qr` | ✓ | ✗ |

User toggle per app; host can force safe mode if reputation < threshold.

---

## 8. Permission downgrade & revocation

| Event | Effect |
|-------|--------|
| User revokes grant in Settings | Delete grants; invalidate caps |
| App update adds permission | **New install review** required |
| App update removes permission | Caps trimmed automatically |
| Revocation list hit | App won't launch; uninstall offered |
| Uninstall | Drop `app_files`, `miniapp_kv`, grants, caps |

---

## 9. Manifest schema extensions (proposed)

```json
{
  "id": "com.example.app",
  "permissions": ["Storage", "MessagingDirect"],
  "permission_options": {
    "MessagingDirect": { "max_recipients_per_hour": 10 }
  },
  "dangerous_permissions_ack": true,
  "reproducible_build_url": "https://…",
  "developer_peer_id": "12D3KooW…"
}
```

`dangerous_permissions_ack` required if manifest contains any Tier 3 permission (store policy).

---

## 10. App reputation → permission policy

Local score `0..100` (not global truth):

| Signal | Δ score |
|--------|---------|
| Valid signed listing | +20 |
| Sideload install | −15 |
| User report | −30 |
| Revocation list | −100 |
| Anomaly: >100 msg/hour | −25 |

| Score | Policy |
|-------|--------|
| ≥ 70 | Normal |
| 40–69 | Safe mode suggested |
| < 40 | Safe mode forced; Tier 3 denied |

---

## 11. Developer SDK linter rules

| Rule ID | Check |
|---------|-------|
| L001 | No `eval` / `Function()` in JS |
| L002 | Declared permission ⊇ used APIs |
| L003 | No fetch/XHR (SDK doesn't expose) |
| L004 | `accepts_payments` implies `Payments` permission |
| L005 | Description ≤ 120 chars, no URL phishing |

---

## 12. Migration from v1 → v2

1. Ship host that accepts both manifests (`min_mycelium_version` gate).
2. `identity.get` starts requiring `Identity` — breaking; major version bump.
3. Issue capability tokens optionally (`cap` field ignored if absent during transition).
4. Enforce tokens in `min_mycelium_version: 0.2.0`.

---

## 13. Test cases (permission)

| Test | Expected |
|------|----------|
| Call `payment.get_balance` without `Payments` | Error |
| Call `identity.get` without `Identity` (v2) | Error |
| Expired capability token | Error |
| Safe mode + `messaging.send` | Error |
| User grants once; second session | Re-prompt if Session grant |

See [MINIAPP_RUNTIME_HARDENING.md](./MINIAPP_RUNTIME_HARDENING.md) §4.
