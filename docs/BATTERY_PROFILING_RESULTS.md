# Battery profiling — pocket / no peers (Android)

**Status:** Messung **teilweise** — Scan-Verhalten gesichert (~9 h), **Verbrauchsanteile (mAh) weiterhin unbrauchbar** (Gerät am Netzteil, Batterystats-Fenster 0 ms on-battery).

| Session | Ergebnis |
|---------|----------|
| `20260710T181213Z` | Bugreport + Collect ~05:27 MESZ; BLE-Scan-Stats brauchbar; mAh-Split **invalid** (AC, Reset 03:56) |
| `20260711T015622Z` | Collect **fehlgeschlagen** (Hintergrund-Job gestorben) |

**Regel:** Für Duty-Cycling-Fixes fehlt weiterhin **Sektion 4.2** (entladener ≥4-h-Pocket-Lauf). Scan-Modus (4.3) ist **ausreichend dokumentiert**.

### Session `20260710T181213Z` (Teilergebnis, Scan-only)

| | |
|--|--|
| Gerät | Google Pixel 6, Android 16 (CP1A.260405.005) |
| App | `network.mycelium.app` v0.1.69, Commit `f150d1a` (main) |
| Laufzeit App | ~7,7 h (20:12 MESZ 10.07. → 03:54 MESZ 11.07.), MeshService läuft noch |
| SOC | Start 86 % (AC) → Ende 100 % (AC) — **kein Entladeprofil** |
| BLE bootstrap | an, 0 Scan-Ergebnisse (No-Peer bestätigt) |
| Dominanter Scan-Modus | **`SCAN_MODE_LOW_POWER`** (~99,99 % der Scan-Zeit), kein LOW_LATENCY |
| Scan-Muster | ~924 Stop/Start-Zyklen à ~30 s (Stale-Watchdog + LOW_POWER) |
| Wi‑Fi Aware | aktiv (LOW_POWER-Umschaltung bestätigt) |
| Artefakte | `artifacts/battery-profile/20260710T181213Z/` (nur Startup, kein Bugreport) |

---

## 1. Szenario

| Parameter | Wert |
|-----------|------|
| App | `network.mycelium.app` (debug), Branch zum Messzeitpunkt: _TBD_ |
| Dauer | ≥ 4 h (empfohlen 4–8 h) |
| Peers | Keine Mycelium-Geräte in BLE-Reichweite |
| Gerätezustand | Bildschirm aus, App im Hintergrund, Gerät in Tasche/Rucksack, nicht force-stopped |
| BLE bootstrap | In App-Einstellungen **an** (Standard für Mesh-Discovery) |
| Kontrolllauf (optional) | Gleiche Dauer mit BLE bootstrap **aus** → Differenz isoliert Mesh-Overhead |

---

## 2. Messprotokoll

Automatisierung im Repo:

```bash
# Gerät per USB, USB-Debugging autorisiert
./scripts/android/battery-profile-pocket.sh prepare
# Gerät pocketen, 4+ Stunden warten
OUT_DIR=artifacts/battery-profile/<timestamp> ./scripts/android/battery-profile-pocket.sh collect
```

Ausgabe: `bugreport.zip`, `batterystats-*.txt`, `summary.txt`, Logcat-Ausschnitt.

**Battery Historian:** `bugreport.zip` hochladen auf [bathist.ef.lc](https://bathist.ef.lc/) oder lokal:

```bash
docker run -p 9999:9999 gcr.io/android-battery-historian/stable:3.0
# Browser: http://localhost:9999
```

In Historian dokumentieren:

1. **App-Auswahl** → `network.mycelium.app`
2. **System stats** → CPU / Bluetooth / Wi‑Fi / Mobile radio / Wakelocks über den Messzeitraum
3. **BLE Scan**-Panel (falls vorhanden) → Scan-Modi und Gesamt-Scanzeit
4. Screenshot oder exportierte Zahlen in Sektion 4 eintragen

---

## 3. Code-Baseline (statische Analyse, unverifiziert)

Diese Tabelle beschreibt **konfigurierten** Verhalten im Quellcode — **nicht** gemessenen Batterieverbrauch.

### 3.1 BLE Scan-Modus

| Aspekt | Implementierung | Datei |
|--------|-----------------|-------|
| Standard-Scan-Modus | `SCAN_MODE_BALANCED` (Android API; **nicht** `LOW_LATENCY`) | `BleBootstrapScanner.kt` |
| Alternativ-Modus | `SCAN_MODE_LOW_POWER`, wenn `setLowPowerScan(true)` | gleiche Datei |
| Umschaltung LOW_POWER | Alle 30 s: `mycelium.preferWifiAwareForDiscovery()` → `setLowPowerScan(prefer)` | `MeshService.kt` |
| Callback-Typ | `CALLBACK_TYPE_FIRST_MATCH` (kein eigenes Duty-Cycling im App-Code) | `BleBootstrapScanner.kt` |
| `SCAN_MODE_LOW_LATENCY` | **Nirgends** referenziert | — |
| Eigenes Duty-Cycling | **Nein** — durchgehender Scan mit periodischem **Restart** (stop/start), nicht Intervall-Pause | `restartScanSilently()` |

**Erwartung für Messung:** Batterystats/Historian sollten überwiegend **BALANCED** oder **LOW_POWER** zeigen (je nach Wi‑Fi-Aware-Verfügbarkeit), nicht LOW_LATENCY.

### 3.2 BLE Advertising

| Aspekt | Wert |
|--------|------|
| Modus | `ADVERTISE_MODE_BALANCED` |
| TX Power | `ADVERTISE_TX_POWER_MEDIUM` |
| Timeout | `0` (dauerhaft, solange Service läuft) |

Quelle: `BleBootstrapAdvertiser.kt`.

### 3.3 Hintergrund-Loops (`MeshService`)

| Intervall | Funktion |
|-----------|----------|
| 1 s | Discovery-State-Tick; BLE-Scan-Restart wenn 30 s ohne Fortschritt (`PeersDiscoveryUi.SCAN_RESTART_STALE_MS`) |
| 2 s | `BleMeshRelay` / Wi‑Fi Direct flush |
| 30 s | Wi‑Fi-Aware-Präferenz → BLE LOW_POWER-Toggle; SOS crisis auto-refresh |
| 45 s | Wi‑Fi-Aware / NSD Announcement refresh |

### 3.4 Weitere Radio-/CPU-Quellen

| Komponente | Verhalten |
|------------|-----------|
| Foreground Service | `MeshService`, Typ `connectedDevice` — hält Prozess aktiv, **kein** explizites App-`WakeLock` |
| Wi‑Fi Multicast Lock | Für libp2p mDNS (LAN), beim Service-Start acquired | `WifiMulticastLock.kt` |
| Wi‑Fi Aware / NSD | Discovery-Bearer parallel zu BLE | `WifiAwareDiscoveryHost.kt` |
| libp2p TCP | Listener `/ip4/0.0.0.0/tcp/7761`, Rendezvous optional | `MeshService.kt` / `NodeConfig` |
| Explizite Wakelocks im App-Code | **Keine** (`PowerManager` nur ThermalMonitor) |

### 3.5 Zu prüfende Hypothesen (nur nach Messung bestätigen)

1. Dominanter Anteil **Bluetooth** (Scan + Advertise + GATT-Server) vs. **CPU** (1 s/2 s Loops, Rust-Node) vs. **Wi‑Fi** (Multicast / Aware / NSD).
2. Scan-Restarts alle ~30 s im No-Peer-Fall erhöhen kurze BLE-Aktivitätsspitzen (Logcat + Historian).
3. System-Wakelocks (Bluetooth stack, FGS) ohne App-eigene `WakeLock`-Einträge.

---

## 4. Messergebnisse

### 4.1 Gerät & Session

| Feld | Wert |
|------|------|
| Gerätemodell | Google Pixel 6 (oriole) |
| Android-Version / Build | 16 / CP1A.260405.005 |
| Messdauer (h) | ~7,7 h App-Laufzeit; **0 h brauchbares Batterie-Messfenster** |
| Start SOC / End SOC (%) | 86 % (AC) / 100 % (AC) |
| Branch / Commit | main / `f150d1a` |
| `OUT_DIR` / bugreport | `artifacts/battery-profile/20260710T181213Z/` — **kein bugreport.zip** |

### 4.2 Verbrauchsanteile (Battery Historian, Mycelium-UID)

**Nicht belastbar.** Bugreport `20260710T181213Z` (Collect 05:27 MESZ): `Time on battery: 0 ms`, `Discharge: 0 mAh`, Gerät am Netzteil (100 %). Modell-Schätzung `u0a411` über ~1,5 h FGS (seit Reset 03:56) ist **nicht** der Pocket-Lauf:

| Kategorie | Modell u0a411 (nur Hinweis, nicht verifiziert) | Verlässlich? |
|-----------|--------------------------------------------------|--------------|
| Mobile radio | ~75 mAh (~92 %) | Nein — AC, Rendezvous/LTE-Attribution |
| CPU | ~4,9 mAh (~6 %) | Nein |
| Wi‑Fi | ~1,6 mAh (~2 %) | Nein |
| Bluetooth / BLE | **0** in UID-Zeile (global ~1,8 mAh, apps: 0) | Nein |
| Wakelocks | Nur `NotificationManagerService:post:network.mycelium.app` | Teilweise |

→ **Neuer entladener Lauf nötig**, bevor Root-Cause nach mAh formuliert wird.

### 4.3 BLE Scan-Modus (gemessen, `dumpsys bluetooth_manager`)

Quelle: Bugreport-Collect `20260710T181213Z` (~05:27 MESZ), kumulative LE-Scan-Statistik.

| Metrik | Wert |
|--------|------|
| Dominanter Scan-Mode | **`SCAN_MODE_LOW_POWER`** (33 230 141 ms ≈ **99,8 %**) |
| Anteil BALANCED | 60 818 ms (2 Starts) |
| LOW_LATENCY | **0 ms** |
| Gesamt BLE-Scan-Zeit | 33 290 959 ms (~**9,25 h**) |
| Anzahl Scan-Sessions | 1111 / 1110 Stop/Start |
| Callback-Typ | `FIRST_MATCH` |
| Scan-Ergebnisse | **0** (No-Peer) |
| Scan-Zyklus | ~**30 s** pro Session (Stale-Watchdog) |
| Eigenes Duty-Cycling? | **Ja** (seit Fix: Fenster+Pause pro Energie-Stufe, adaptives Backoff) |

### 4.3.1 Implementierter Fix (nach Scan-Diagnose)

Diagnose: **Dauerscan** (~9 h, LOW_POWER), nicht LOW_LATENCY. Fix in `BleScanDutyCycle.kt` / `BleScanDutyCycleRunner.kt`:

| Energie-Stufe | Scan-Fenster | Pause (Basis) |
|---------------|--------------|---------------|
| Active | 10 s | 5 s |
| Intermittent | 8 s | 22 s |
| Passive | 5 s | 55 s |

- **TransportScore:** Wi‑Fi-Aware bevorzugt → Pause ×2, `SCAN_MODE_LOW_POWER`
- **Adaptives Backoff:** ab 3 leeren Zyklen Pause ×1,6 pro weiterem Zyklus (max 10 min), **unabhängig** von Stufe
- **Peer erkannt:** Backoff sofort auf 0, kurze Intervalle
- Stale-Watchdog-Restart (~30 s Dauerscan) **ersetzt** durch Duty-Loop

### 4.4 Wakelocks

**Nicht verfügbar** (kein Bugreport; Batterystats-Fenster nur ~20 s on-battery).

### 4.5 Logcat / Verhalten No-Peer

| Metrik | Wert |
|--------|------|
| BLE scan restarts (ca.) | ~1110 |
| `setLowPowerScan(true)` Anteil | ~100 % nach Erststart |
| Wi‑Fi Aware aktiv? | Ja |

### 4.6 Kontrolllauf (optional)

| Lauf | Mycelium mAh | Δ vs. BLE an |
|------|--------------|--------------|
| BLE bootstrap aus | _TBD_ | _TBD_ |

---

## 5. Schlussfolgerung & nächste Schritte

**Für mAh-Verifikation:** entladener Re-Run empfohlen. **Scan-Fix:** Duty-Cycling implementiert (Sektion 4.3.1).

**Gesichert (Diagnose):**
- Dauerscan in **LOW_POWER** (nicht LOW_LATENCY), ~30 s Restarts
- Fix adressiert **kontinuierlichen Scan**, nicht fälschlich LOW_LATENCY

**Implementiert:**
- `BleScanDutyCycle` — Stufen Active / Intermittent / Passive
- Adaptives Backoff + TransportScore Wi‑Fi-Aware-Pause
- Peer → sofort Reset auf kurze Intervalle

---

## Referenzen

- `scripts/android/battery-profile-pocket.sh`
- `scripts/android/parse-bugreport-battery.sh`
- `android/.../bootstrap/BleBootstrapScanner.kt`
- `android/.../bootstrap/BleBootstrapAdvertiser.kt`
- `android/.../app/MeshService.kt`
- ADR 009 (Wi‑Fi Aware vs. BLE discovery tradeoff): `docs/adr/009-wifi-aware-discovery-bearer.md`
