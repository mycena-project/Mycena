# Mycena Mini-App — Attack Scenarios

**Purpose:** Red-team catalog for Cell runtime — each scenario maps to mitigations and current gaps.  
**Companion:** [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) · [MINIAPP_RUNTIME_HARDENING.md](./MINIAPP_RUNTIME_HARDENING.md)

**Severity:** Critical / High / Medium / Low

---

## AS-01 — Trojan sideload bundle

| Field | Detail |
|-------|--------|
| **Severity** | Critical |
| **Attacker** | Malicious QR / file share |
| **Path** | `MiniAppViewModel.installFromUri` → `install_app` → `AppStore::install` |
| **Steps** | 1. Victim installs `evil.mxa` from protest bulletin link. 2. Manifest requests `Payments` + `Messaging`. 3. App runs immediately. |
| **Impact** | Full bridge abuse; balance read; spam |
| **Target mitigation** | Install requires signature + hash match; provenance UI |
| **Today** | Path traversal blocked; size cap; sideload requires bundle signature + provenance UI |
| **Status** | **MITIGATED** (SD-041) |

---

## AS-02 — Store listing swap (hash mismatch)

| Field | Detail |
|-------|--------|
| **Severity** | High |
| **Attacker** | Malicious gossip publisher |
| **Path** | Valid signed listing for app A + different bundle bytes on peer URL |
| **Steps** | 1. Listing sig verifies (manifest + hash H). 2. User downloads bundle with hash H'≠H. 3. Install doesn't check. |
| **Impact** | Trusted name, malicious code |
| **Target mitigation** | `install(bundle)` requires `blake3(bundle)==listing.bundle_hash` |
| **Today** | Gossip listing sig verified; install checks hash via `install_verified` |
| **Status** | **MITIGATED** |

---

## AS-03 — Downgrade attack

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Attacker** | Bundles older vulnerable version |
| **Steps** | Install `v0.9` over `v1.2` with known XSS |
| **Target mitigation** | Monotonic version compare |
| **Today** | Downgrade blocked in preview unless user opts in |
| **Status** | **MITIGATED** |

---

## AS-04 — XSS → bridge privilege escalation

| Field | Detail |
|-------|--------|
| **Severity** | High |
| **Attacker** | Malicious HTML/inline script |
| **Path** | CSP allows `unsafe-inline` |
| **Steps** | 1. Inject script via bulletin-rendered HTML or app UI. 2. Call `window.mycena` freely. |
| **Impact** | All granted permissions used without user intent |
| **Target mitigation** | CSP nonces; strict script-src |
| **Today** | Network blocked; strict CSP nonces; inline scripts rejected at install |
| **Status** | **MITIGATED** (SD-040) |

---

## AS-05 — Identity harvest without permission

| Field | Detail |
|-------|--------|
| **Severity** | High |
| **Attacker** | Any installed app |
| **Path** | `identity.get` — no `Permission::Identity` check |
| **Steps** | App calls `getIdentity()` on load |
| **Impact** | PeerId + enc pubkey + display name exfil via steganography in outbound messages |
| **Target mitigation** | Require Identity + runtime prompt |
| **Today** | **Open API** |
| **Status** | **VULNERABLE** |

---

## AS-06 — Fake payment confirmation (WebView trust)

| Field | Detail |
|-------|--------|
| **Severity** | Critical |
| **Attacker** | Payment scam app |
| **Path** | `payment.request` returns `_bridge_action: payment_confirmation_required` |
| **Steps** | 1. App draws fake “Confirm pay” UI in HTML. 2. User taps. 3. App calls `createPaymentQr` separately. |
| **Impact** | User believes they confirmed; attacker shows malicious QR |
| **Target mitigation** | Native payment sheet only; ignore WebView UI |
| **Today** | Android bridge handles `show_alert` only; payment action **not** implemented |
| **Status** | **VULNERABLE** |

---

## AS-07 — mxcpay QR phishing

| Field | Detail |
|-------|--------|
| **Severity** | Critical |
| **Attacker** | `payment.create_qr` + social engineering |
| **Path** | App with `Payments` permission |
| **Steps** | 1. `createPaymentQr` to attacker address. 2. Display QR in WebView (looks official). 3. Victim sends MXC. |
| **Impact** | Funds to attacker (preview coin still trains bad habits) |
| **Target mitigation** | QR only in native overlay with address fingerprint |
| **Today** | Returns URI to WebView; `_bridge_action: render_qr` **not** handled natively |
| **Status** | **VULNERABLE** |

---

## AS-08 — QR scanner hijack

| Field | Detail |
|-------|--------|
| **Severity** | High |
| **Attacker** | App calls `util.scan_qr` |
| **Path** | No `Camera` permission check |
| **Steps** | Scan attacker bootstrap / mxcpay / malicious multiaddr |
| **Impact** | Eclipse network or payment redirect |
| **Target mitigation** | Camera permission + decode preview + confirm |
| **Today** | Returns `open_qr_scanner` — **not** handled in WebView |
| **Status** | **PARTIAL** (scanner may not open; permission not gated) |

---

## AS-09 — Spam via messaging.broadcast

| Field | Detail |
|-------|--------|
| **Severity** | High |
| **Attacker** | Messaging permission only |
| **Path** | `messaging.broadcast` → `send_chat(None, …)` |
| **Steps** | Flood global chat scope with `[app/evil]` payloads |
| **Impact** | Network DoS; harassment |
| **Target mitigation** | Separate permission; rate limit; scope allowlist in manifest |
| **Today** | Requires Messaging; no rate limit |
| **Status** | **VULNERABLE** |

---

## AS-10 — Bulletin scope poisoning

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Attacker** | BulletinWrite permission |
| **Path** | `bulletin.post` arbitrary scope string |
| **Steps** | Post illegal content to `city/public` impersonating news |
| **Impact** | Legal/reputational; harassment |
| **Target mitigation** | Scope allowlist in manifest; moderator keys (future) |
| **Today** | Any scope string allowed |
| **Status** | **VULNERABLE** |

---

## AS-11 — Storage cross-app leak (key guessing)

| Field | Detail |
|-------|--------|
| **Severity** | Low |
| **Attacker** | App A tries keys for app B |
| **Path** | `storage.*` scoped `app:{id}:{key}` |
| **Steps** | Malicious keys only touch own prefix |
| **Impact** | None if isolation holds |
| **Target mitigation** | Prefix enforced in bridge |
| **Today** | `AppStorage` tree shared but keys prefixed; test exists ✓ |
| **Status** | **MITIGATED** |

---

## AS-12 — Dual storage tree confusion

| Field | Detail |
|-------|--------|
| **Severity** | Low |
| **Attacker** | Developer mistake / future bug |
| **Path** | `AppStore::miniapp_kv` vs `AppStorage::miniapp_storage` |
| **Steps** | FFI `miniapp_storage_*` uses AppStorage; store has unused `miniapp_kv` tree |
| **Impact** | Inconsistent wipe; potential future wrong tree binding |
| **Target mitigation** | Single storage backend |
| **Today** | Bridge uses AppStorage only |
| **Status** | **DEBT** (hygiene) |

---

## AS-13 — WebView sandbox escape (CVE)

| Field | Detail |
|-------|--------|
| **Severity** | Critical |
| **Attacker** | Nation-state / commercial exploit |
| **Path** | Chromium WebView RCE |
| **Steps** | Compromise renderer → access Kotlin process → FFI → node keys |
| **Impact** | Full device |
| **Target mitigation** | Isolated process; reduce TCB in WebView process |
| **Today** | Same process as wallet (SD-050) |
| **Status** | **INDUSTRY RISK** |

---

## AS-14 — postMessage bridge injection (desktop)

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Attacker** | Malicious desktop UI XSS |
| **Path** | `postMessage(..., "*")` |
| **Steps** | Parent page script forges `__mycena_call` |
| **Impact** | Bridge calls as app |
| **Target mitigation** | Origin check + fixed targetOrigin |
| **Today** | iframe source check on receive ✓; target `*` |
| **Status** | **PARTIAL** |

---

## AS-15 — JavaScriptInterface brute force (Android)

| Field | Detail |
|-------|--------|
| **Severity** | Low |
| **Attacker** | Other WebView in app (if any) |
| **Path** | `@JavascriptInterface MycenaMiniapp` |
| **Steps** | Secondary WebView tries `MycenaMiniapp.onNativeCall` |
| **Impact** | Bridge calls if reachable |
| **Target mitigation** | `@JavascriptInterface` only on dedicated WebView; ProGuard |
| **Today** | Single mini-app WebView |
| **Status** | **LOW RISK** |

---

## AS-16 — Social engineering via util.alert

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Attacker** | “Your wallet is compromised. Tap OK to secure.” |
| **Path** | `util.alert` → Toast on Android |
| **Steps** | User trusts toast as system |
| **Impact** | Drives malicious next step |
| **Target mitigation** | Branded native dialog: “Message from app %id%” |
| **Today** | Plain Toast |
| **Status** | **WEAK** |

---

## AS-17 — Revocation bypass (offline)

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Attacker** | Known-malware app id |
| **Path** | No revocation list |
| **Steps** | Install before list update; never go online |
| **Impact** | Persistent malware |
| **Target mitigation** | Bundled revocation snapshot |
| **Today** | Not implemented |
| **Status** | **GAP** |

---

## AS-18 — Permission ceiling escalation via update

| Field | Detail |
|-------|--------|
| **Severity** | High |
| **Attacker** | Benign v1 → malicious v2 update |
| **Path** | Sideload update bundle |
| **Steps** | v2 adds `Payments` without new user review |
| **Impact** | Escalation |
| **Target mitigation** | Re-show install review on permission delta |
| **Today** | Overwrite install |
| **Status** | **VULNERABLE** |

---

## AS-19 — Denial of wallet (resource exhaustion)

| Field | Detail |
|-------|--------|
| **Severity** | Medium |
| **Attacker** | Tight loop `miniapp_bridge_call` |
| **Path** | FFI synchronous block_on |
| **Steps** | Freeze UI thread / exhaust coin node lock |
| **Impact** | DoS |
| **Target mitigation** | Rate limits; async bridge |
| **Today** | No limit |
| **Status** | **VULNERABLE** |

---

## AS-20 — Malicious offline payload in bulletin

| Field | Detail |
|-------|--------|
| **Severity** | High |
| **Attacker** | Bulletin post: “Install aid app” + link |
| **Path** | AS-01 + mesh gossip |
| **Steps** | Disinformation during crisis |
| **Impact** | Mass compromise |
| **Target mitigation** | AS-01 fix + user education |
| **Today** | Sideload requires developer bundle signature (SD-041/240) |
| **Status** | **MITIGATED** |

---

## Scenario → beta-blocker map

| Scenario | Beta-blocker? |
|----------|---------------|
| AS-01, AS-02 | **yes** |
| AS-05, AS-06, AS-07 | **yes** |
| AS-04 | **yes** (CSP) |
| AS-09 | **recommended** |
| AS-13 | document only |

---

## Red-team exercise script (90 min)

1. Build `evil.mxa` with all permissions; sideload — record exfiltrated identity.
2. XSS inline script call bridge — document success/fail.
3. `payment.create_qr` to attacker address — observe native vs WebView UI.
4. Publish signed listing + install mismatched bundle — document hash check.
5. `messaging.broadcast` flood — measure peer impact.
6. Desktop: attempt parent postMessage forgery.

**Pass:** AS-01,02,05,06,07 fail (attack blocked); others documented with severity.
