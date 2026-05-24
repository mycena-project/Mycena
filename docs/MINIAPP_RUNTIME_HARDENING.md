# Mycelium Mini-App — Runtime Hardening & Implementation Review

> **Status May 2026**: Items §1 (CSP), §3 (bridge sessions), §4 (process isolation),
> §8 (rate limits), §9 (capability tokens) are implemented.
> Open items: §2 (sideload without sig, partially), §5 (WASM runtime, future).
> See [BETA_BLOCKERS.md](./BETA_BLOCKERS.md) for current P0 list.

**Last updated:** May 2026  
**Scope:** Code review of current mini-app stack vs zero-trust target ([MINIAPP_SECURITY_ARCHITECTURE.md](./MINIAPP_SECURITY_ARCHITECTURE.md))

---

## 1. Current architecture (as-built)

```text
┌──────────────────────────────────────────────────────────────────┐
│ Android: MiniAppRunnerScreen / MiniAppStoreScreen (Kotlin)        │
│   WebView + @JavascriptInterface → miniappBridgeCall (UniFFI)     │
├──────────────────────────────────────────────────────────────────┤
│ Desktop: MiniApp.svelte iframe + postMessage → miniapp_bridge_call │
├──────────────────────────────────────────────────────────────────┤
│ mycelium-ffi: install_app, get_app_file, miniapp_bridge_call      │
├──────────────────────────────────────────────────────────────────┤
│ mycelium-app:                                                     │
│   bundle.rs      ZIP load, path traversal check                   │
│   store.rs       install, listings, sled trees                    │
│   bridge_host.rs permission checks (partial)                      │
│   bridge_api.js  window.mycelium facade                           │
└──────────────────────────────────────────────────────────────────┘
```

**Data paths:**

| Tree | Path | Used by |
|------|------|---------|
| App files | `app_files:{app_id}` | `get_app_file`, WebView intercept |
| Installed manifest | `installed_apps` | permissions list |
| Listings cache | `app_listings` | store browse |
| Bridge storage | `miniapp_storage` keys `app:{id}:{key}` | `bridge_host` via `AppStorage` |
| Legacy | `miniapp_kv:{app_id}` | **Removed** — bridge + FFI use `miniapp_storage` only |

---

## 2. Unsafe trust assumptions (found in code/docs)

| # | Assumption | Reality | Risk |
|---|------------|---------|------|
| T1 | “Listing signature verified” = safe app | Install does not verify listing or bundle | AS-01, AS-02 — **partially resolved** (store path + sideload ack) |
| T2 | “Permissions in manifest” = enforced | ~~`identity.get`, `util.scan_qr` skip checks~~ **resolved** in `bridge_session.rs` | AS-05, AS-08 |
| T3 | `_bridge_action` = security UX | Android only implements `show_alert` | AS-06, AS-07 |
| T4 | “WebView CSP” = XSS safe | `unsafe-inline` allowed | AS-04 |
| T5 | “Sideload from file” = same as store | No provenance UI distinction | AS-01 |
| T6 | `miniapp-guide.md` says sig verify is “future” | Partially stale — gossip verify exists, install doesn’t | Doc drift |
| T7 | User reads Toast as system message | `util.alert` = Toast | AS-16 |
| T8 | Bridge in same process as node | WebView escape = game over | AS-13 |

---

## 3. Missing isolation boundaries

| Boundary | Target | Current |
|----------|--------|---------|
| Process | Mini-app renderer separate | **Shared** with Mycelium app |
| Network | Deny all guest egress | **Enforced** (JS stubs + intercept) ✓ |
| Filesystem | Virtual app URL only | **Enforced** WebView intercept ✓ |
| Storage | Encrypted per-app | **Plaintext** sled; prefix isolation ✓ |
| Identity | Cap-gated | **Resolved** — session + capability tokens in bridge |
| Coin | Native confirm | **Ungated** QR generation |
| CPU/time | Quotas | **None** |
| IPC | Capability tokens | **Direct** bridge string call |

---

## 4. Dangerous APIs (inventory)

| API | Tier | Permission today | Native gate | Notes |
|-----|------|------------------|-------------|-------|
| `identity.get` | 2 | **None** | None | Returns peer_id, enc_pubkey, display_name |
| `payment.get_balance` | 3 | Payments | None | Exposes balances |
| `payment.create_qr` | 3 | Payments | None | Returns mxcpay URI + render hint |
| `payment.request` | 3 | Payments | **Not implemented** | Fake UI risk |
| `messaging.send` | 2 | Messaging | None | Formats body with app id |
| `messaging.broadcast` | 3 | Messaging | None | Default scope `mycelium/chat` |
| `bulletin.post` | 2 | BulletinWrite | None | Any scope |
| `peers.nearby` | 2 | PeerDiscovery | None | Full peer list |
| `util.scan_qr` | 3 | **None** | **Not implemented** | Camera |
| `util.confirm` | 2 | None | **Not implemented** | |
| `storage.set` | 1 | Storage | None | String only; no size cap per key |

**Privilege escalation paths:**

1. **Install trojan** → immediate Tier 3 via manifest (AS-01).
2. **XSS** → call bridge without extra permission (AS-04).
3. **identity.get** → no permission (AS-05).
4. **Payments** → generate QR without native review (AS-07).
5. **FFI `miniapp_storage_*`** — callable from Android? If exposed to other Kotlin without app_id validation — grep shows only bindings; **low risk** if not exported.

---

## 5. Bundle validation gaps

| Check | `bundle.rs` | `store.install` | Gossip listing |
|-------|-------------|-----------------|----------------|
| Path traversal | ✓ | via load | N/A |
| `mycelium-app.json` present | ✓ | ✓ | N/A |
| Manifest validate | ✓ | ✓ | N/A |
| 10 MB cap | test | ✓ | N/A |
| MIME / polyglot scan | ✓ | ✓ (install) | N/A |
| HTML entry sanity (no `file:`) | ✓ | ✓ (install) | N/A |
| Entry file count limit | ✓ (128) | ✓ (install) | N/A |
| Signature over bundle | ✗ sideload | ✗ sideload / **✓ store listing sig** | listing only |
| `bundle_hash` match | ✗ | ✗ | on publish only |
| Downgrade guard | ✗ | ✗ | ✗ |
| Revocation check | ✓ | ✓ (preview/install) | ingest ✓ |

**Required install pipeline (target):**

```rust
pub fn install_verified(
    &self,
    bundle_bytes: &[u8],
    trust: InstallTrust, // StoreListing | PinnedDeveloper | SideloadWarn
) -> Result<MiniAppManifest>
```

---

## 6. Storage isolation weaknesses

| Issue | Severity | Detail |
|-------|----------|--------|
| Plaintext sled | Medium | `miniapp_storage` not encrypted at rest |
| No per-key size limit | Medium | Large value DoS |
| No total quota | Medium | Fill disk |
| Dual KV systems | Low | `miniapp_kv` vs `miniapp_storage` confusion |
| Uninstall | OK | Clears `app_files` + `miniapp_kv` + `miniapp_clear_all_for_app` |

**Test coverage:** `storage.rs` `miniapp_storage_isolation` ✓

---

## 7. Network policy (current)

| Control | Android | Desktop |
|---------|---------|---------|
| fetch/XHR/WS block | JS inject on `onPageFinished` | Injected in srcdoc |
| CSP | Injected meta; `unsafe-inline` | Same |
| External URL intercept | `shouldInterceptRequest` | N/A (srcdoc only) |
| Navigation lock | `shouldOverrideUrlLoading` | srcdoc |

**Gaps:** `unsafe-inline` retained for bundles with inline `<script>`; strict CSP (nonce-only) when `strict_csp_eligible` (P3/H05). `wasm-unsafe-eval 'none'` (C3). Desktop postMessage replies use iframe `e.origin` (C3).

---

## 8. Hardening checklist (prioritized)

### P0 — Beta blockers (Cell C0 — implemented 2026-05)

- [x] **H01** `AppStore::install_verified` — `install_policy.rs`, FFI `preview_miniapp_install` / `install_app`, store UI provenance
- [x] **H02** `identity.get` → `require_permission(Identity)` + runtime grant (Android `MiniAppGrants`, desktop confirm)
- [x] **H03** Bridge actions `payment_confirmation_required`, `render_qr`, `show_confirm`, `show_alert` in native UI (`MiniAppWebView.kt`, `MiniApp.svelte`)
- [x] **H04** `util.scan_qr` → `Camera` permission + ZXing on Android; desktop prompt stub
- [x] **H05** CSP nonce on host scripts; `unsafe-inline` only when bundle entry uses inline `<script>`
- [x] **H06** Install + first-launch provenance dialogs (`MiniAppProvenance.kt`, `MiniAppStore.svelte` modal)
- [x] **H07** `MessagingBroadcast` permission + 10/hour rate limit in `bridge_host.rs`

**Files:** `store.rs`, `bridge_host.rs`, `MiniAppWebView.kt`, `MiniApp.svelte`, new `MiniAppProvenanceScreen.kt`

### P1 — Strongly recommended

- [x] **H08** Bridge call rate limiter (`bridge_limits.rs`, 120/min) + session token (`bridge_session.rs`) — Cell C2
- [x] **H09** Storage quotas + max string length (`storage_quota.rs`) — Cell C2
- [x] **H10** Encrypt `miniapp_storage` with HKDF(storage_key, scoped_key) when `storage_key` set — Cell C2
- [x] **H11** Version monotonic check on install (`install_policy.rs`); `allow_downgrade` override in install UI
- [x] **H12** Single storage tree: removed dead `AppStore::miniapp_kv_*`; bridge + FFI use `miniapp_storage`
- [x] **H13** Desktop postMessage reply targets iframe `origin` — Cell C3
- [x] **H14** Native `AlertDialog` with app name + “not from Mycelium” notice (Android); desktop prefix on alerts
- [x] **H15** Bulletin scope allowlist (`manifest.bulletin_scopes`) — Cell C1/C2

### P2 — Defense in depth

- [x] **H16** Android `:miniapp` process + `MiniAppBridgeService` IPC (H16)
- [x] **H17** Bridge session tokens per app launch — Cell C2; HMAC runtime caps in P4/H25
- [x] **H18** Signed revocation gossip `mycelium/appstore/revocations/v1` + sled merge
- [x] **H19** `miniapp-sdk` CLI `lint` + `hash` — Cell C4
- [x] **H20** Safe mode per app (user toggle + forced by reputation); bridge blocks Tier 2–3
- [x] **H21** Local reputation score in sled; install/report/revocation/anomaly signals

### P3 — Strategic

- [x] **H05** Strict CSP when entry has no inline `<script>` (`csp.rs`, hosts use nonce-only)
- [x] **H22** WASM gate + [`MINIAPP_WASM_RUNTIME.md`](./MINIAPP_WASM_RUNTIME.md) (executor deferred)
- [x] **H23** `reproducible_build` in manifest + `miniapp-sdk attest` + install preview flag  
  Attestation uses **content hash** (`content_attestation_hash`: manifest without `reproducible_build` + sorted files), so merging attest JSON does not invalidate the hash. Archive `blake3(.mxa)` still used for store listings.

### P4 — Supply chain + capability v2 + CI

- [x] **H24** Bundle static scan (`bundle_scan.rs`): file count, zip-bomb ratio, extension allowlist, polyglot sniff, HTML scheme checks; wired into `preview_install` + `miniapp-sdk lint`
- [x] **H25** HMAC capability tokens (`capability_token.rs`): `_cap` on tier 2–3 bridge methods, bound to `_session`; `miniapp_issue_capability` FFI/desktop/Android IPC
- [x] **H26** Curator revocation publish: `AppStore::build_revocation_gossip`, `AppNode::publish_app_revocation`, desktop store UI + `miniapp_publish_revocation`
- [x] **H27** `miniapp_security_tests.rs` (revoked install, bundle scan, capability MAC, gossip revocation)
- [x] **H28** Desktop runner: iframe `credentialless` + `referrerpolicy="no-referrer"` (full utility process deferred)

**CI gate:** `cargo test -p mycelium-app miniapp::`

---

## 9. Suggested code changes (sketches)

### 9.1 Install verification

```rust
// store.rs
pub fn install_with_trust(
    &self,
    bundle_bytes: &[u8],
    listing: Option<&AppStoreListing>,
    allow_sideload: bool,
) -> anyhow::Result<MiniAppManifest> {
    let hash = blake3::hash(bundle_bytes).to_hex().to_string();
    if let Some(l) = listing {
        anyhow::ensure!(l.verify_signature()?, "invalid listing signature");
        anyhow::ensure!(l.bundle_hash == hash, "bundle hash mismatch");
    } else if !allow_sideload {
        anyhow::bail!("sideload install disabled");
    }
    // version monotonic...
    self.install(bundle_bytes)
}
```

### 9.2 Identity gate

```rust
// bridge_host.rs
"identity.get" => {
    self.require_permission(Permission::Identity)?;
    // return minimal fields if safe mode
    ...
}
```

### 9.3 Native bridge actions (Android)

```kotlin
when (result.optString("_bridge_action")) {
    "payment_confirmation_required" -> showPaymentSheet(...)
    "render_qr" -> showQrOverlay(result.getString("_qr_content"))
    "open_qr_scanner" -> launchQrScanner(callId)
    "show_confirm" -> showConfirmDialog(...)
    ...
}
```

---

## 10. Test methodology

| Layer | Test |
|-------|------|
| Unit | `install` rejects hash mismatch; identity permission |
| Unit | `bridge_host` denies Tier 3 without permission |
| Android | Espresso: install UI shows permissions |
| Instrumented | Sideload evil bundle blocked in strict mode |
| Static | grep for `unsafe-inline` in CSP |
| Fuzz | `miniapp_bridge_call` random method strings |
| Red team | [MINIAPP_ATTACK_SCENARIOS.md](./MINIAPP_ATTACK_SCENARIOS.md) script |

**CI gate:** `cargo test -p mycelium-app` + future `miniapp_security_tests.rs`

---

## 11. Documentation hygiene

| Doc | Action |
|-----|--------|
| `miniapp-guide.md` | Update: install verify, bridge actions, CSP |
| `SECURITY_DEBT_REGISTER` | SD-040–042, SD-240 linked ✓ |
| `security-for-users.md` | Add mini-app trust section for users |

---

## 12. Summary scorecard

| Area | Score (0–5) | Notes |
|------|---------------|-------|
| Supply chain | 2 | Gossip sig ✓; install ✗ |
| Sandbox | 3 | Network ✓; CSP partial |
| Permissions | 2 | Many gaps |
| Native UX gates | 1 | Alerts only |
| Storage | 3 | Isolation ✓; encryption ✗ |
| Isolation | 1 | Same process |
| Offline | 4 | Works; trust weak |

**Overall:** Suitable for **trusted developer preview**, not yet **zero-trust hostile environment**.

**Minimum to claim “hardened beta”:** H01–H07 complete + red-team script pass.
