# Battery profiling — pocket / no peers (Android)

**Status:** Zwei **VALID**-Läufe. **Mesh-only** (`20260712T154530Z`): App **731 mAh** / 13,4 h — Mobile Radio weiterhin ~94 % der UID trotz Internet/Rendezvous aus. BLE weiterhin gering.

| Session | Ergebnis |
|---------|----------|
| `20260710T181213Z` | Scan-Stats brauchbar; mAh **invalid** (AC während Lauf) |
| `20260711T015622Z` | Collect fehlgeschlagen (Terminal/Job beendet) |
| `20260711T160244Z` | **VALID** — Vollprofil: 2311 mAh Gerät, 1145 mAh App; Internet+Rendezvous **an** |
| **`20260712T154530Z`** | **VALID** — **Mesh-only**: 1311 mAh Gerät, 731 mAh App; siehe §4.2 |

---

## Schnellstart — Overnight (empfohlen)

**Ablauf:** Abends USB für Setup → **Kabel ab** → über Nacht in der Tasche → morgens USB wieder an (Collect).

```bash
cd Mycelium

# Abends (USB angeschlossen ist OK — adb braucht das Kabel):
./scripts/android/battery-profile-pocket.sh mesh-only
# → prepare + 8h Scheduler, BLE an, Internet/Rendezvous aus

# Sofort danach: USB abziehen, Handy pocketen, schlafen gehen.

# Morgens optional Status:
./scripts/android/battery-profile-pocket.sh status
# Collect läuft automatisch — oder manuell:
# OUT_DIR=artifacts/battery-profile/<id> ./scripts/android/battery-profile-pocket.sh collect
```

Kürzerer Lauf (4 h):

```bash
./scripts/android/battery-profile-pocket.sh all
```

**Wichtig:** Während der **Messnacht** muss das Gerät **ohne Ladung** laufen. USB nur für Setup/Collect — dann zählt Android die Entladung. Am Ladegerät die ganze Nacht = `VALID_RUN=no`, 0 mAh.

Nach Collect: `artifacts/battery-profile/<id>/docs-snippet.md` → Sektion 4.
`VALID_RUN=yes` = ≥3 h on-battery seit dem Abziehen.

---

## Was das Skript verbessert

| Problem (alt) | Fix |
|---------------|-----|
| Gerät am Netzteil → 0 mAh | Messung zählt erst **nach Abziehen**; Collect prüft ≥3 h on-battery |
| Prepare blockierte bei USB | Setup **mit** Kabel erlaubt — Hinweis statt Abbruch |
| Hintergrund-Job stirbt | `schedule` / `overnight` mit `nohup` |
| Settings.xml überschrieben | BLE-Flag per Python in bestehende Prefs gemerged |
| Fehlende Wi‑Fi-Permission | `NEARBY_WIFI_DEVICES` + Location beim Grant |
| Manuelles Docs-Formatieren | `parse-bugreport-battery.sh` → `docs-snippet.md` + `VALID_RUN` |

---

## Regel

Für belastbare **Sektion 4.2** (mAh): entladener ≥4-h-Pocket-Lauf, BLE bootstrap an, keine Peers.
Optional danach **Kontrolllauf** (`control`) mit BLE aus → Differenz in 4.6.

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

Automatisierung:

```bash
./scripts/android/battery-profile-pocket.sh mesh-only   # empfohlen
./scripts/android/battery-profile-pocket.sh mesh-overnight
./scripts/android/battery-profile-pocket.sh schedule
./scripts/android/battery-profile-pocket.sh status
./scripts/android/battery-profile-pocket.sh control-overnight  # BLE aus, Vergleich
```

Siehe **Schnellstart** oben. Legacy manuell:

```bash
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
| Umschaltung LOW_POWER | Duty-Loop + Wi‑Fi-Aware-Präferenz | `BleScanDutyCycleRunner.kt` |
| Eigenes Duty-Cycling | **Ja** — Fenster + Pause + adaptives Backoff | `BleScanDutyCycle.kt` |

**Erwartung für Messung:** Batterystats/Historian sollten überwiegend **BALANCED** oder **LOW_POWER** zeigen (je nach Wi‑Fi-Aware-Verfügbarkeit), nicht LOW_LATENCY.

### 3.2 BLE Advertising

| Aspekt | Wert |
|--------|------|
| Modus | `ADVERTISE_MODE_BALANCED` |
| TX Power | `ADVERTISE_TX_POWER_MEDIUM` |
| Timeout | `0` (dauerhaft, solange Service läuft) |

Quelle: `BleBootstrapAdvertiser.kt`.

### 3.3 Hintergrund-Loops (`MeshService`) — nach Duty-Cycle-Fix

| Intervall | Funktion |
|-----------|----------|
| Duty-Loop | Scan-Fenster + Pause (`BleScanDutyCycleRunner`), adaptives Backoff |
| 1 s | Discovery-State-Tick |
| 2 s | `BleMeshRelay` / Wi‑Fi Direct flush |
| 45 s | Wi‑Fi-Aware / NSD Announcement refresh |

~~Stale-Watchdog 30 s Dauerscan~~ **ersetzt** durch Duty-Cycling (siehe 4.3.1).

### 3.4 Weitere Radio-/CPU-Quellen

| Komponente | Verhalten |
|------------|-----------|
| Foreground Service | `MeshService`, Typ `connectedDevice` — hält Prozess aktiv, **kein** explizites App-`WakeLock` |
| Wi‑Fi Multicast Lock | Für libp2p mDNS (LAN), beim Service-Start acquired | `WifiMulticastLock.kt` |
| Wi‑Fi Aware / NSD | Discovery-Bearer parallel zu BLE | `WifiAwareDiscoveryHost.kt` |
| libp2p TCP | Listener `/ip4/0.0.0.0/tcp/7761`, Rendezvous optional | `MeshService.kt` / `NodeConfig` |
| Explizite Wakelocks im App-Code | **Keine** (`PowerManager` nur ThermalMonitor) |

### 3.5 Hypothesen — Stand nach Messungen

| Hypothese | `20260711T160244Z` (Vollprofil) | `20260712T154530Z` (Mesh-only) |
|-----------|----------------------------------|--------------------------------|
| Dominanter Verbraucher | Mobile Radio **~92 %** UID; BLE **~0,5 %** | Mobile Radio **~94 %** UID; BLE nicht in UID-Zeile (~5 mAh global) |
| Internet/Rendezvous aus hilft? | — | App-UID **−36 %** (1145 → 731 mAh); Radio **−35 %** (1052 → 688 mAh), dominiert aber weiter |
| Scan No-Peer | 0 Ergebnisse | 0 Ergebnisse |
| App-Wakelocks | Nur FGS-Notification | Nur FGS-Notification |

**Offen:** Warum **688 mAh Mobile Radio** bei `internet_enabled=false` / `rendezvous_enabled=false`? Mögliche Ursachen: LTE-Modem wach durch FGS/Wi‑Fi-Wakelocks, Android-Fehlattribution, versteckter Hintergrundtraffic — mit `dumpsys telephony.registry` / Historian nachverfolgen.

---

## 4. Messergebnisse

### 4.1 Gerät & Session — primär: `20260712T154530Z` (Mesh-only)

| Feld | Wert |
|------|------|
| Gerätemodell | Google Pixel 6 (oriole) |
| Android-Version / Build | 16 / CP1A.260405.005 |
| On battery | **13 h 23 min** (99,8 %) |
| Start SOC / End SOC (%) | **100 % → 71 %** |
| Geräte-Entladung (modelliert) | **1311 mAh** (Kapazität 4256 mAh) |
| App-Version / Commit | v0.1.72–0.1.73 / `dbf193e` |
| Szenario | **`mesh_only`** — BLE bootstrap **an**, Internet + Rendezvous **aus** |
| Peers | **0** BLE-Scan-Ergebnisse |
| MeshService FGS | ~13 h 23 min im Hintergrund |
| Collect | Manuell 13.07. 05:37 UTC (Auto-Collect 12.07. 20:27 — kein USB) |
| Artefakte | `artifacts/battery-profile/20260712T154530Z/` |

### 4.1b Vergleich — Vollprofil `20260711T160244Z`

| Feld | Vollprofil | Mesh-only | Δ |
|------|------------|-----------|---|
| On battery | 20 h 17 min | 13 h 23 min | — |
| SOC | 80 → 29 % | 100 → 71 % | — |
| Gerät (mAh) | 2311 | 1311 | **−43 %** |
| App-UID (mAh) | 1145 | 731 | **−36 %** |
| Mobile radio (UID) | 1052 | 688 | **−35 %** |
| CPU (UID) | 65 | 37 | −43 % |
| Wi‑Fi (UID) | 23 | 6 | −74 % |
| BLE (UID / global) | 4,8 | ~5 global | ≈ gleich |

### 4.2 Verbrauchsanteile — Mesh-only (`20260712T154530Z`, UID u0a430)

**Belastbar** (`VALID_RUN=yes`).

| Kategorie | mAh (App-UID) | Anteil UID |
|-----------|---------------|------------|
| **Mobile radio** | **688** | ~94 % |
| CPU | 37 | ~5 % |
| Wi‑Fi | 6,4 | ~1 % |
| Bluetooth / BLE | *(nicht in UID-Zeile; global ~4,7 mAh)* | — |
| **UID gesamt (modelliert)** | **731** | — |

Geräte-Gesamt: 1311 mAh → Mycelium-UID ~**56 %** der modellierten Entladung.

**Interpretation:** Internet/Rendezvous aus spart messbar (~400 mAh App über ~13 h), aber **LTE-Attribution bleibt dominant**. BLE-Mesh allein ist weiterhin ein kleiner Posten.

### 4.2b Verbrauchsanteile — Vollprofil (`20260711T160244Z`, UID u0a429)

| Kategorie | mAh (App-UID) | Anteil UID |
|-----------|---------------|------------|
| **Mobile radio** | **1052** | ~92 % |
| CPU | 65 | ~6 % |
| Wi‑Fi | 23 | ~2 % |
| **Bluetooth / BLE** | **4,8** | **<0,5 %** |
| **UID gesamt** | **1145** | — |

### 4.3 BLE Scan-Modus

`dumpsys bluetooth_manager`-Statistik ist **kumulativ seit Boot** — dieselben 1963 Sessions erscheinen in beiden Läufen; für fensterbezogene Scan-Duty **Logcat `BleScanDuty`** nötig (bisher 0 Zeilen).

Referenzwerte (kumulativ, nicht pro Session-Fenster):

| Metrik | Beide Läufe (kumulativ) |
|--------|-------------------------|
| Dominanter Modus | LOW_POWER ~99,9 % |
| LOW_LATENCY | 0 ms |
| Scan-Ergebnisse | **0** (No-Peer) |

### 4.3.1 Duty-Cycle-Konfiguration (Code)

| Energie-Stufe | Scan-Fenster | Pause (Basis) |
|---------------|--------------|---------------|
| Active | 10 s | 5 s |
| Intermittent | 8 s | 22 s |
| Passive | 5 s | 55 s |

- Wi‑Fi-Aware bevorzugt → Pause ×2, `SCAN_MODE_LOW_POWER`
- Adaptives Backoff ab 3 leeren Zyklen (max 10 min Pause)
- Thermal-Drosselung layered on top (Critical: 3 s / 180 s)

### 4.4 Wakelocks

| Quelle | Befund |
|--------|--------|
| App | `NotificationManagerService:post:network.mycelium.app` (FGS) |
| Wi‑Fi | Häufige `dhdpcie_host_wake` mit Attribution `Wifi [u0a429 FGS]` |

### 4.5 Logcat / No-Peer

| Metrik | Wert |
|--------|------|
| Logcat (`BleScanDuty`) | **Leer** in beiden Läufen (Collect erweitern) |
| Wi‑Fi Aware / NSD | Aktiv (Teil des BLE-Bootstrap-Pfads) |

### 4.6 Kontrolllauf (optional)

| Lauf | Mycelium mAh | Δ vs. Mesh-only |
|------|--------------|-----------------|
| BLE bootstrap aus (`control-overnight`) | _TBD_ | _TBD_ |
| ~~Mesh-only (ohne Rendezvous)~~ | **731** | *(Referenz `20260712T154530Z`)* |

---

### Archiv: Session `20260710T181213Z` (Scan-only, invalid mAh)

| Feld | Wert |
|------|------|
| Laufzeit App | ~7,7 h |
| SOC | 86 % (AC) → 100 % (AC) |
| mAh | **invalid** (`Time on battery: 0 ms`) |
| Scan-Muster (pre-fix) | ~1110 × ~30 s LOW_POWER Dauerscan |

---

### 4.1 (legacy) Gerät & Session — `20260710T181213Z`

| Feld | Wert |
|------|------|
| Gerätemodell | Google Pixel 6 (oriole) |
| Android-Version / Build | 16 / CP1A.260405.005 |
| Messdauer (h) | ~7,7 h App-Laufzeit; **0 h brauchbares Batterie-Messfenster** |
| Start SOC / End SOC (%) | 86 % (AC) / 100 % (AC) |
| Branch / Commit | main / `f150d1a` |
| `OUT_DIR` / bugreport | `artifacts/battery-profile/20260710T181213Z/` |

### 4.2 (legacy) — invalid

Siehe Archiv oben; mAh nicht verwertbar.

### 4.3 (legacy) BLE Scan — `20260710T181213Z` (pre-fix Dauerscan)

| Metrik | Wert |
|--------|------|
| Dominanter Scan-Mode | **`SCAN_MODE_LOW_POWER`** (99,8 %) |
| Gesamt BLE-Scan-Zeit | ~9,25 h |
| Scan-Sessions | 1111; ~30 s Zyklus (Stale-Watchdog) |
| Scan-Ergebnisse | 0 |

---

## 5. Schlussfolgerung & nächste Schritte

**Gesichert:**
- **BLE-Mesh-Discovery verbraucht wenig** (Größenordnung ~5 mAh) — auch über 13–20 h No-Peer
- **Mobile Radio** dominiert die App-UID in beiden Szenarien; Mesh-only spart ~35–36 %, löst das Problem nicht
- No-Peer bestätigt (0 Scan-Ergebnisse); LOW_POWER, kein LOW_LATENCY

**Offen / empfohlen:**
1. **`control-overnight`** — BLE aus, gleiche Dauer → isoliert Mesh/BLE-Anteil in §4.6
2. **Mobile-Radio-Attribution klären** bei ausgeschaltetem Internet (Code-Review + Battery Historian)
3. **Logcat bei Collect** — `BleScanDuty`-Tag sichern; Bluetooth-Dumpsys ist boot-kumulativ
4. **Auto-Collect:** morgens USB **vor** Scheduler-Ende, oder `collect` manuell (2× fehlgeschlagen)

Parser: `parse-bugreport-battery.sh` liest `20h 17m`-Format + App-BLE-Block korrekt (`VALID_RUN`).

---

## Referenzen

- `scripts/android/battery-profile-pocket.sh`
- `scripts/android/parse-bugreport-battery.sh`
- `android/.../bootstrap/BleBootstrapScanner.kt`
- `android/.../bootstrap/BleBootstrapAdvertiser.kt`
- `android/.../app/MeshService.kt`
- ADR 009 (Wi‑Fi Aware vs. BLE discovery tradeoff): `docs/adr/009-wifi-aware-discovery-bearer.md`
