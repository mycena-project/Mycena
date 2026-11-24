# Mini-App Sandbox — Status & Sicherheitsgewinn

**Stand:** Juli 2026
**Scope:** WebView-Kompatibilität, wasmtime-WASM-Sandbox, Ressourcen-Limits, Migration
**Verwandt:** [MINIAPP_SECURITY_ARCHITECTURE.md](./MINIAPP_SECURITY_ARCHITECTURE.md), [MINIAPP_WASM_RUNTIME.md](./MINIAPP_WASM_RUNTIME.md), SD-050

---

## 1. Executive Summary

Mycena unterstützt jetzt ein **Hybrid-Runtime-Modell**:

| `runtime` | Gast-Logik | UI | Status |
|-----------|------------|-----|--------|
| **`webview`** | HTML/JS in Plattform-WebView | DOM in WebView | Legacy / Kompatibilitäts-Fallback |
| **`wasm`** | **wasmtime**-Sandbox (`guest.wasm`) | optional `ui/index.html` in WebView | **Neuer Standard für Gast-Logik** |

**Neu implementiert (Jul 2026):**

- `wasmtime` v36 as Feature `wasm-runtime` (Default) in `mycena-miniapp`
- **Fuel-Metering** — deterministisches CPU-Budget pro Invocation
- **Memory-Limit** — `ResourceLimiter` auf `Store` (Default 16 MiB linear memory)
- **Execution-Timeout** — Epoch-Interruption + Wall-Clock-Watcher (Default 5 s)
- **Bridge-Import** — `mycena.bridge_call` delegiert an `BridgeHost` (Session-Injection)
- **Migration** — Manifest/Bundle/Scan/SDK akzeptieren `runtime: "wasm"`; WebView-Apps unverändert

---

## 2. Sicherheitsgewinn: Vorher vs. Nachher

### 2.1 Was vorher möglich war (nur WebView)

| Angriff / Fehlverhalten | WebView-only (vorher) |
|-------------------------|------------------------|
| Endlosschleife im Gast-JS (ohne Bridge) | **Möglich** — kein JS-CPU-Metering |
| Speicherhunger via DOM / JS-Heap | **Möglich** — nur OS-/Prozess-Druck (Android `:miniapp`) |
| Unbegrenzte lokale Berechnung | **Möglich** |
| XSS → Bridge-Missbrauch | Durch CSP/Session/Capability **eingeschränkt**, nicht eliminiert |
| Chromium/WebView RCE | **Möglich** (Plattform-TCB, SD-050) |
| Native Binary im Bundle | Install-Scan **verhindert** (MZ/ELF/PDF) |
| Bridge-API-Flood | Rate + Chain-Budget **verhindert** |

### 2.2 Was jetzt technisch verhindert wird (`runtime: "wasm"`)

| Angriff / Fehlverhalten | WASM-Sandbox (jetzt) | Mechanismus |
|-------------------------|----------------------|-------------|
| Endlosschleife in Gast-Logik | **Verhindert** | Fuel erschöpft → `FuelExhausted` |
| Unbegrenztes linear memory growth | **Verhindert** | `ResourceLimiter::memory_growing` |
| Lange laufende Gast-Berechnung | **Verhindert** | Epoch-Timeout 5 s → `ExecutionTimeout` |
| Direkter Netzwerkzugriff aus Gast | **Verhindert** | Kein WASI-Socket; kein JS-`fetch` in WASM-Pfad |
| Dateisystem-Zugriff aus Gast | **Verhindert** | Kein WASI-FS im Linker |
| Unbegrenzte Bridge-Ketten | **Weiter eingeschränkt** | `bridge_limits.rs` + `BridgeHost` (120/min, 30 s Budget) |
| Host-API ohne Session | **Verhindert** | Session wird beim Bridge-Import injiziert |
| Polyglot/EXE im Bundle | **Verhindert** | `bundle_scan.rs` + WASM-Magic-Check |

### 2.3 Was weiterhin nur eingeschränkt oder offen ist

| Risiko | WASM-Runtime | WebView-Fallback |
|--------|--------------|------------------|
| Chromium/WebView CVE (Hybrid-UI) | N/A für reine WASM-Logik | **Weiter TCB** wenn `ui/index.html` |
| Bridge-Permission-Eskalation | Gast muss `_cap` für geschützte Methoden liefern | Gleich |
| Android UniFFI-Bindings nach UDL-Änderung | **Manuell regen** (`miniapp_get_runtime`, `miniapp_invoke_wasm_run`) | — |
| iOS Miniapp-Host | — | Nicht implementiert |
| Desktop ohne OS-Prozess-Isolation | WASM-Logik isoliert; iframe-UI wie bisher | iframe-Sandbox |

---

## 3. Architektur

```text
runtime: "webview"  →  WebView (HTML/JS)  →  Bridge  →  BridgeHost
runtime: "wasm"     →  wasmtime (guest.wasm)  →  mycena.bridge_call  →  BridgeHost
                       optional: ui/index.html  →  WebView (nur UI)
```

### 3.1 Kern-Dateien

| Datei | Rolle |
|-------|--------|
| `crates/mycena-miniapp/src/wasm_runtime.rs` | wasmtime Engine, Fuel, Memory, Epoch, Host-Import |
| `crates/mycena-miniapp/src/runtime.rs` | `GuestRuntime`, `invoke_wasm_run`, Bundle-Helfer |
| `crates/mycena-miniapp/src/manifest.rs` | `runtime: "webview" \| "wasm"`, Entry-Validierung |
| `crates/mycena-miniapp/src/bundle_scan.rs` | `.wasm` erlaubt, Magic-Check, Hybrid-HTML-Scan |
| `crates/mycena-app/src/wasm_bridge_host.rs` | `run_wasm_guest_for_app` + Bridge-Delegation |
| `crates/mycena-app/src/bridge_host.rs` | Unverändertes Permission-/Rate-Modell |
| `crates/mycena-desktop` | `miniapp_get_runtime`, `miniapp_invoke_wasm_run` |
| `crates/mycena-ffi` + Android AIDL | Gleiche FFI für isolierten `:miniapp`-Prozess |
| `crates/miniapp-sdk` | `lint` akzeptiert `wasm` + scan |

### 3.2 Default-Limits (WASM)

| Limit | Wert | Konstante |
|-------|------|-----------|
| Fuel / Invocation | 10⁷ | `DEFAULT_WASM_FUEL_PER_INVOCATION` |
| Linear Memory | 16 MiB | `DEFAULT_WASM_MAX_MEMORY_BYTES` |
| Table elements | 4096 | `DEFAULT_WASM_MAX_TABLE_ELEMENTS` |
| Wall-Clock Timeout | 5 s (deadline, not minimum runtime) | `DEFAULT_WASM_EXECUTION_TIMEOUT_MS` |
| Bridge Rate | 120/min | `bridge_limits.rs` |
| Bridge Chain Budget | 30 s / 30 s Fenster | `bridge_limits.rs` |
| Bundle Size | 10 MiB | `store.rs` |

Fuel und Epoch-Timeout sind **komplementär**: Fuel = deterministisches CPU-Budget; Epoch = Wall-Clock-**Deadline** (der Invoke-Thread wird nicht für die vollen 5 s gehalten).

---

## 4. Bundle-Migration

### 4.1 Bestehende WebView-Miniapps

- `runtime: "webview"` (Default) — **keine Änderung nötig**
- Install, Lint, Desktop/Android-Host laden weiter `entry` HTML

### 4.2 Neue WASM-Miniapps

`mycena-app.json`:

```json
{
  "id": "com.example.logic",
  "runtime": "wasm",
  "entry": "guest.wasm",
  "permissions": ["Identity"]
}
```

Optional Hybrid-UI:

```text
guest.wasm          # Logik (export "run" → i32)
ui/index.html       # Deklarative UI, Bridge für API
mycena-app.json   # runtime: "wasm", entry: "guest.wasm"
```

Gast-Bridge (WAT/ Rust/ TinyGo):

```text
(import "mycena" "bridge_call"
  (func $bridge (param i32 i32 i32 i32 i32 i32) (result i32)))
```

SDK: `miniapp-sdk lint bundle.mxa` prüft WASM-Magic + Scan-Regeln.

---

## 5. Plattform-Dispatch

| Plattform | WebView | WASM |
|-----------|---------|------|
| **Desktop** | iframe + `miniapp_get_entry_html` | `miniapp_invoke_wasm_run` beim Start + optional Hybrid-iframe |
| **Android** | `:miniapp`-WebView | `invokeWasmRun` via AIDL vor WebView-Aufbau |
| **iOS** | — | Nicht angebunden |

---

## 6. Tests

```bash
# WASM-Sandbox (Fuel, Memory, Run)
cargo test -p mycena-miniapp wasm

# Bridge-Guest-Runner
cargo test -p mycena-app wasm

# Gesamte Miniapp-Suite
cargo test -p mycena-miniapp
```

---

## 7. Vollständigkeits-Assessment (Jul 2026)

| Anforderung | Status | Anmerkung |
|-------------|--------|-----------|
| wasmtime-Dependency | ✅ | Workspace `wasmtime = 36`, Feature `wasm-runtime` (default) |
| Fuel-Metering | ✅ | `consume_fuel(true)` + `Store::set_fuel` |
| Memory-Limits | ✅ | `ResourceLimiter` 16 MiB default |
| Execution-Timeout | ✅ | Epoch + Background-Thread |
| Manifest `runtime: "wasm"` | ✅ | Validierung Entry `.wasm` |
| bundle.rs / bundle_scan Migration | ✅ | WASM load + scan |
| WebView-Kompatibilitäts-Fallback | ✅ | `runtime: "webview"` unverändert |
| Bridge an BridgeHost | ✅ | `wasm_bridge_host.rs` |
| Desktop-Host Dispatch | ✅ | Runtime + invoke Commands |
| Android-Host Dispatch | ✅ | AIDL + Kotlin (Bindings regen nötig) |
| miniapp-sdk lint | ✅ | webview + wasm |
| Dokumentation Sicherheitsgewinn | ✅ | Dieses Dokument |
| Android UniFFI-Regenerierung | ⏳ | Manuell: `./scripts/regenerate-android-bindings.sh` |
| iOS WKWebView + WASM | ❌ | Außer Scope |
| WASM-Gast-SDK (Rust/TinyGo Templates) | ⏳ | Host-Import dokumentiert, kein Template-Crate |
| CI-Gate dediziert WASM-Red-Team | ⏳ | Unit-Tests vorhanden, kein sim-Gate |

**Fazit:** Die geforderte wasmtime-Sandbox mit Fuel/Memory/Timeout ist **vollständig in Rust** umgesetzt; WebView-Fallback bleibt für Legacy-Bundles. Offene Punkte sind Android-Binding-Regenerierung (Deployment-Schritt) und optionale SDK/CI-Erweiterungen — keine Blocker für den Kern-Sicherheitsgewinn.

---

## 8. Änderungshistorie

| Datum | Änderung |
|-------|----------|
| Aug 2026 | Fuel default 10⁷, table cap 4096, invoke timeout is a deadline (no sleeper-join) |
| Jul 2026 (früh) | Ist-Analyse WebView-only; WASM als Plan |
| Jul 2026 | **Implementiert:** wasmtime-Sandbox, Hybrid-Runtime, Migration, Host-Dispatch, Sicherheitsmatrix |
