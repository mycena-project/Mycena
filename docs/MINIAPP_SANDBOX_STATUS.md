# Mini-App Sandbox — Ist-Verhalten & Ressourcen-Limits

**Stand:** Juli 2026  
**Scope:** Runtime-Architektur, aktuelle Grenzen, neue Plattform-Limits, WASM-Bewertung  
**Verwandt:** [MINIAPP_SECURITY_ARCHITECTURE.md](./MINIAPP_SECURITY_ARCHITECTURE.md), [MINIAPP_RUNTIME_HARDENING.md](./MINIAPP_RUNTIME_HARDENING.md), [MINIAPP_WASM_RUNTIME.md](./MINIAPP_WASM_RUNTIME.md), SD-050

---

## 1. Executive Summary

Mycelium-Miniapps laufen **ausschließlich als HTML/JavaScript in einer Plattform-WebView** (Android: System-WebView/Chromium; Desktop: Tauri-iframe im Host-WebView). Es gibt **keinen** eingebetteten WASM-Executor (kein wasmtime/wasmer im Repo). Manifest-Wert `runtime: "wasm"` wird beim Install **abgewiesen**.

Die Sicherheitsgrenze ist damit:

```text
Gast-JS (WebView)  →  Host-Bridge  →  Rust (BridgeHost / Node)
```

**Trust Boundary:** Chromium/WebView ist Teil des TCB (SD-050). Android reduziert Schaden durch **Prozess-Isolation** (`:miniapp`), aber ein WebView-0-day kann weiterhin den isolierten Renderer-Prozess kompromittieren.

---

## 2. Runtime nach Plattform

| Plattform | Gast-Runtime | Host-Datei | Bridge-Pfad |
|-----------|--------------|------------|-------------|
| **Android** | `android.webkit.WebView` in Prozess `:miniapp` | `MiniAppProcessActivity.kt` | JS → `@JavascriptInterface` → AIDL → `MiniAppBridgeService` → UniFFI → Rust |
| **Desktop** | Sandboxed `<iframe>` + `postMessage` | `MiniApp.svelte` | JS → Tauri `invoke("miniapp_bridge_call")` → Rust |
| **iOS** | **Nicht implementiert** (nur BLE/Mesh) | — | Vorbereitung: `ios/Mycelium/MiniApp/MiniAppResourceGuard.swift` |

### 2.1 Was die WebView-Sandbox heute erzwingt

| Kontrolle | Android | Desktop |
|-----------|---------|---------|
| Netzwerk | `fetch`/XHR/WebSocket/`sendBeacon` gestubbt + URL-Intercept | CSP `connect-src 'none'` + iframe-Sandbox |
| Dateizugriff | Nur Bundle-Dateien via virtuelle Origin `https://app.mycelium.local/` | Entry-HTML aus Rust-Store |
| DOM Storage | `domStorageEnabled = false` | iframe ohne persistente Gast-DOM-Storage |
| CSP | Nonce-basiert (`MiniAppCsp.kt`) | Nonce + Meta-CSP |
| Bridge | Session-Token + HMAC-Capability-Tokens + Manifest-Permissions | Gleiche Rust-Logik |

### 2.2 Was die WebView **nicht** begrenzen kann

| Risiko | Grund |
|--------|--------|
| Endlosschleife / enge JS-Schleife im Gast | Kein JS-CPU-Metering in WebView-API |
| Speicherhunger rein in Gast-JS/DOM | Kein hartes RAM-Quota pro WebView (OS/WebView-Prozess) |
| Akku durch `requestAnimationFrame`/Timer | `pauseTimers()` nur wenn Activity pausiert |
| Chromium-RCE | Plattform-TCB, nicht durch App-Policy lösbar |

---

## 3. Bridge-Architektur (Rust)

**Crate:** `mycelium-miniapp` (Manifest, Bundle, Limits) + `mycelium-app/bridge_host.rs` (Dispatcher)

```text
bridge_api.js          window.mycelium.* → __mycelium_native_call(JSON)
        ↓
Plattform-Host         Session + Capability anhängen, Grants, native UI
        ↓
BridgeHost::handle()   Rate → Chain-Budget → Timeout → Permission → Methode
        ↓
AppNode / AppStorage / CoinNode
```

**Wichtige Dateien:**

| Datei | Rolle |
|-------|--------|
| `crates/mycelium-miniapp/src/bridge_api.js` | Gast-API, Promise-Timeout |
| `crates/mycelium-miniapp/src/bridge_limits.rs` | Rate + Execution-Budget |
| `crates/mycelium-app/src/bridge_host.rs` | Native Handler + `tokio::timeout` |
| `crates/mycelium-ffi/src/lib.rs` | `miniapp_bridge_call` (Android UniFFI) |

---

## 4. Ressourcen-Limits (Implementiert)

### 4.1 Rust / Bridge (`bridge_limits.rs`)

| Limit | Wert | Verhalten |
|-------|------|-----------|
| Bridge-Call-Rate | **120/min** pro `app_id` | Sliding Window; Anomalie-Log bei Überschreitung |
| Einzel-Call-Timeout | **5 s** | `tokio::time::timeout` um `BridgeHost::handle_inner` |
| Chain-Budget | **30 s kumulativ / 30 s Fenster** | Summe gemessener nativer Handler-Latenzen pro App |
| Broadcast-Rate | 10/h | `bridge_host.rs` |
| Session-TTL | 1 h | `bridge_session.rs` |
| Capability-TTL | 15 min | `capability_token.rs` |
| Storage | 256 KB gesamt, 256 Keys, 32 KB/Value | `storage_quota.rs` |
| Bundle | 10 MB, 128 Dateien, Zip-Bomb-Scan | `store.rs`, `bundle_scan.rs` |

**Chain-Budget:** Modelliert „Bridge-Call-Ketten“ (schnelle Folge von `window.mycelium.*`-Aufrufen). Jeder native Handler misst Wall-Time; Überschreitung → `"bridge execution budget exceeded"`.

### 4.2 Gast-JS (`bridge_api.js`)

| Limit | Wert |
|-------|------|
| Promise-Timeout | **8 s** (über nativem 5-s-Timeout, damit Rust-Fehler zuerst ankommt) |

Hängende Bridge-Antworten werden im Gast rejected; der Gast-Thread bleibt blockierbar durch **reines JS ohne Bridge** (siehe §2.2).

### 4.3 Android (`MiniAppResourceGuard.kt`, `MiniAppProcessActivity.kt`)

| Mechanismus | Verhalten |
|-------------|-----------|
| **Bridge-IPC-Timeout** | `Future.get(5s)` um AIDL `bridgeCall` |
| **`ComponentCallbacks2.onTrimMemory`** | `RUNNING_LOW` → Cache leeren; `RUNNING_CRITICAL` → WebView pausieren + `pauseTimers()`; `COMPLETE` → WebView destroy + Activity beenden |
| **`onLowMemory`** | WebView destroy + Activity beenden |
| **`onRenderProcessGone`** | Renderer-Crash/OOM → WebView destroy |
| **`onPause`/`onResume`** | `pauseTimers()` / `resumeTimers()` |
| **Prozess-Isolation** | `:miniapp` — Haupt-Node-Prozess nicht direkt im WebView-Prozess |

### 4.4 Desktop (`MiniApp.svelte`)

| Mechanismus | Verhalten |
|-------------|-----------|
| Bridge-Timeout | `Promise.race` 8 s um Tauri-`invoke` |
| iframe-Sandbox | Kein direkter Netz-/Top-Navigation-Zugriff |

### 4.5 iOS (vorbereitet)

`MiniAppResourceGuard.swift` registriert `UIApplication.didReceiveMemoryWarningNotification` → WebView-Teardown-Callback. **Noch nicht an WKWebView angebunden** (kein Miniapp-Host auf iOS).

---

## 5. Limit-Matrix: Was schützt vor was?

| Angriff / Fehler | Rate-Limit | Call-Timeout | Chain-Budget | Memory-Pressure | Prozess-Isolation |
|------------------|:----------:|:------------:|:------------:|:---------------:|:-----------------:|
| Bridge-Spam (API-Flood) | ✓ | — | ✓ | — | ✓ (Android) |
| Langsamer/hängender Rust-Handler | — | ✓ | ✓ | — | — |
| Schnelle Bridge-Schleife (`while(true) call()`) | ✓ | ✓ | ✓ | — | ✓ |
| Reine JS-Endlosschleife (kein Bridge) | — | — | — | teilweise¹ | ✓ |
| DOM/JS-Speicherhunger | — | — | — | ✓² | ✓ |
| WebView-Chromium-RCE | — | — | — | — | teilweise³ |

¹ `pauseTimers()` wenn Activity im Hintergrund; Vordergrund-CPU bleibt OS/WebView-Sache.  
² Android beendet Miniapp bei starkem Memory-Druck; kein präzises Quota.  
³ Isolierter Prozess schützt Node/Wallet, nicht Gerät global.

---

## 6. WASM-Sandbox — Bewertung für zukünftige Miniapp-Logik

### 6.1 Ist-Zustand

- `runtime: "wasm"` ist **Install-Gate only** ([MINIAPP_WASM_RUNTIME.md](./MINIAPP_WASM_RUNTIME.md)).
- Kein `wasmtime`/`wasmer` in `Cargo.toml` / CI.

### 6.2 WebView-JS vs. WASM (wasmtime + Fuel)

| Kriterium | WebView-JS (heute) | WASM + wasmtime |
|-----------|-------------------|-----------------|
| **Deterministisches CPU-Limit** | ✗ | ✓ (Fuel/Epoch interruption) |
| **Speicher-Obergrenze** | ✗ (OS-level) | ✓ (Store-Limits konfigurierbar) |
| **Netzwerk-Isolation** | Host-Policy nötig | Kein Netz by default |
| **UI/HTML** | Natürlich (DOM) | Braucht separaten UI-Layer oder Canvas |
| **Ökosystem / DX** | Standard Web | Neues SDK, kleinere Toolchain |
| **TCB-Größe** | Chromium (groß) | wasmtime + Gast-Module (kleiner, aber Host-Bridge bleibt) |
| **Akku** | Schwer messbar | Fuel pro Instruction → begrenzbar |

### 6.3 Empfehlung (Jul 2026)

**Kurzfristig (Beta):** WebView beibehalten + **Host-Limits** (dieses Dokument) + Bridge-Härtung. Begründung: bestehende HTML-Miniapps, geringer Migrationsaufwand, Android-Prozess-Isolation bereits live.

**Mittelfristig (P3):** **Hybrid-Modell** wie in [MINIAPP_SECURITY_ARCHITECTURE.md](./MINIAPP_SECURITY_ARCHITECTURE.md) skizziert:

```text
runtime: "webview"  →  UI/DOM in WebView, Logik minimal
runtime: "wasm"     →  wasmtime-Gast mit Fuel (z.B. 10⁹ Instructionen/Call),
                       linear memory cap (z.B. 16 MB), kein WASI-Netz;
                       UI weiter über deklarative Bridge-Events
```

**WASM lohnt sich besonders für:**

- Rechenintensive oder unbound-Loop-fähige Gast-Logik (Spiele, Parsing, Krypto in Gast)
- Deterministische Audit-Anforderungen („max X CPU pro Aktion“)
- Reduktion der XSS-Fläche (kein Gast-DOM-Zugriff auf Host)

**WASM ersetzt nicht:**

- Native Payment-UI (weiter Host-only)
- WebView-TCB für HTML-Apps bis Migration abgeschlossen
- Bridge-Permission-Modell (Capabilities bleiben in Rust)

### 6.4 Geplanter WASM-Pfad (noch nicht implementiert)

1. `wasmtime` als optionale Feature-Flag-Dependency (`mycelium-miniapp/wasm-runtime`)
2. Fuel-Limit pro `bridge.invoke_guest()` (analog Chain-Budget)
3. Manifest: `runtime: "wasm"`, Entry `guest.wasm` + optional `ui/index.html`
4. Red-Team: Fuel-Exhaustion, Memory-OOM, Host-Import-Whitelist

---

## 7. Akzeptierte Restrisiken

| ID | Risiko | Mitigation heute | Vollständige Lösung |
|----|--------|------------------|---------------------|
| SD-050 | WebView/Chromium CVE | Isolierter Prozess (Android) | WASM für Logik; WebView nur UI |
| — | Gast-JS-CPU ohne Bridge | `pauseTimers`, Activity-Lifecycle | WASM Fuel |
| — | Kein iOS-Miniapp-Host | — | WKWebView + gleiche Limits |
| — | Desktop iframe = kein OS-Prozess | Tauri-Sandbox, Timeouts | Optional separater Helper-Prozess |

Siehe [ACCEPTED_SECURITY_RESIDUALS.md](./ACCEPTED_SECURITY_RESIDUALS.md) R4 (WebView TCB).

---

## 8. Tests & Verifikation

```bash
# Rust: Rate + Chain-Budget
cargo test -p mycelium-miniapp bridge_chain

# Gesamte Miniapp-Security-Suite
cargo test -p mycelium-miniapp

# Bridge-Handler (Timeout indirekt über handle)
cargo test -p mycelium-app
```

Manuell Android:

1. Miniapp mit enger Bridge-Schleife → nach ~120 Calls/min oder Chain-Budget Fehler
2. Entwickleroption „Don't keep activities“ + Speicherdruck → Miniapp-Prozess beendet sich

---

## 9. Änderungshistorie

| Datum | Änderung |
|-------|----------|
| Jul 2026 | Erstfassung: Ist-Analyse WebView-only; Chain-Budget + Call-Timeout; Android `ComponentCallbacks2`; iOS-Vorbereitung; WASM-Bewertung |
