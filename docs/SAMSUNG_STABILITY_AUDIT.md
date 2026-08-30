# Samsung / OEM stability audit (static)

**Date:** 2026-08-29
**Scope:** Android app (`android/app`), minSdk 26, targetSdk 34.
**Method:** Code inspection against documented Android API behavior and existing freeze audit. **No Samsung hardware was used.** Do not treat this document as a crash post-mortem.

**Evidence classes**

| Label | Meaning |
|-------|---------|
| **STATICALLY MITIGATED** | Code now handles a documented API/OEM-sensitive failure without swallowing it into an empty `catch`. |
| **NEEDS RUNTIME** | Behavior depends on One UI / OEM policy; cannot be closed without a device log. |
| **VERIFIED ON REAL SAMSUNG HARDWARE** | **None.** Do not fill this column without a named model, One UI version, app SHA, and logcat. |

Manufacturer-specific *guesses* (Knox internals, undocumented SemWifiManager) are **out of scope**. Samsung is treated as: Android + extra battery/FGS/hotspot/install policy (documented by Google OEM guides and One UI user settings: Auto Blocker, unknown apps, Play Protect).

---

## How to use crash logs

Filter: `MyceliumStability`. Lines look like:

`op=<operation> api=<int> mfr=<Build.MANUFACTURER> model=<Build.MODEL> lifecycle=<fg|bg|unknown> subtype=<exception class>`

Followed by a stack trace. **Not logged:** chat/mail/SOS bodies, invite URIs, storage keys, hex blobs ≥32 chars.

Uncaught exceptions are logged then passed to the previous handler (process still dies).

---

## Findings

### S01 — `registerReceiver` three-arg API on minSdk 26

| Field | Content |
|-------|---------|
| **File** | `WifiDirectBearerHost.kt`, `MiniAppProcessActivity.kt` (Share path already uses `ContextCompat`) |
| **Code path** | `Context.registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)` |
| **Platform assumption** | Three-arg `registerReceiver` exists on all supported APIs. It is **API 33+**. |
| **Potential failure** | `NoSuchMethodError` on API 26–32. `Error` is not `Exception` — `MeshService` init `catch (Exception)` does not catch it. BLE-on path constructs `WifiDirectBearerHost` and calls `register()` inside `updateBleBootstrap()`. Mini-app process registers mesh broadcasts the same way. |
| **Mitigation** | `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` (AndroidX implements the pre-33 overload). |
| **Runtime verification** | Required on API 26–32 (any OEM) and on Samsung API 33+. |
| **Status after this task** | STATICALLY MITIGATED. VERIFIED ON REAL SAMSUNG HARDWARE: no. |

`MeshEventBus.meshEvents()` already version-gated; switched to `ContextCompat` for one path.

---

### S02 — `WifiP2pManager` non-null cast + `initialize()`

| Field | Content |
|-------|---------|
| **File** | `WifiDirectShareGroupHelper.kt`, `WifiDirectBearerHost.kt` |
| **Code path** | `getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager` then `manager.initialize(...)` |
| **Platform assumption** | Wi-Fi Direct service and channel always exist. Android: `getSystemService` may be null; `initialize` is `@Nullable` in the framework. Tablets/TV/some OEM builds omit P2P. |
| **Potential failure** | `NullPointerException` / `ClassCastException` when opening Share or enabling BLE mesh — before any user-visible error. |
| **Mitigation** | `WifiP2pSupport.open()`: feature check, safe cast, null channel → no crash; Share/Direct fail with existing UI errors. |
| **Runtime verification** | Samsung without Direct, or Direct disabled in settings. |
| **Status after this task** | STATICALLY MITIGATED. VERIFIED ON REAL SAMSUNG HARDWARE: no. |

---

### S03 — BLE `startScan` / `startAdvertising` / GATT without `SecurityException`

| Field | Content |
|-------|---------|
| **File** | `BleBootstrapScanner.kt`, `BleBootstrapAdvertiser.kt`, `BleInviteGattServer.kt` |
| **Code path** | `BluetoothLeScanner.startScan`; `BluetoothLeAdvertiser.startAdvertising`; `openGattServer` |
| **Platform assumption** | Permission check at BLE-enable time remains valid. Android 12+: these APIs throw `SecurityException` if `BLUETOOTH_SCAN`/`CONNECT`/`ADVERTISE` revoked while running. `ScanSettings.CALLBACK_TYPE_FIRST_MATCH` is optional hardware; `onScanFailed(SCAN_FAILED_FEATURE_UNSUPPORTED)` is documented. Restarting the same settings can loop. |
| **Potential failure** | Process crash on permission revoke; scan fail-loop on stacks that do not support FIRST_MATCH (common complaint on mixed OEM BLE). |
| **Mitigation** | Catch `SecurityException`/`IllegalStateException`/`IllegalArgumentException` at start **and stop** scan/advertise; log via `MyceliumStability`; do not scan. Fallback once to `CALLBACK_TYPE_ALL_MATCHES` when error code is feature-unsupported. Null-safe `BluetoothManager`. |
| **Runtime verification** | Samsung: toggle Bluetooth mesh, revoke Nearby/Bluetooth in Settings while scanning. |
| **Status after this task** | STATICALLY MITIGATED. VERIFIED ON REAL SAMSUNG HARDWARE: no. |

---

### S04 — Foreground service start (API 12+/14)

| Field | Content |
|-------|---------|
| **File** | `MeshService.kt`, `MainActivity.kt`, `SettingsViewModel.kt` |
| **Code path** | `startForegroundService` then `startForeground`; BLE toggle restarts FGS from Settings |
| **Platform assumption** | App is always in a state allowed to start FGS. Android 12+: `ForegroundServiceStartNotAllowedException` (an `IllegalStateException`) from background. Android 14 / target 34: `startForeground` should pass `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` matching the manifest. Samsung One UI additionally kills long-running FGS (policy, not an API). |
| **Potential failure** | Crash on background BLE toggle; `MissingForegroundServiceTypeException` on API 34 if type omitted; after OEM kill, sticky restart may fail init (see S05). |
| **Mitigation** | API 34+ three-arg `startForeground` with `CONNECTED_DEVICE`. Catch `IllegalStateException` on `startForeground` / `startForegroundService`, log, `stopSelf` with `START_NOT_STICKY` or skip BLE update — no empty catch. `nodeFailed` is **not** cleared in `onDestroy` so the retry UI can persist after a failed FGS start. **Do not** add `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. |
| **Runtime verification** | Samsung: enable BLE from Settings, background the app, wait for One UI sleep (OEM-02). |
| **Status after this task** | STATICALLY MITIGATED for API startForeground type + illegal-state. FGS **kill after idle** is NEEDS RUNTIME. VERIFIED ON REAL SAMSUNG HARDWARE: no. |

---

### S05 — Node init failure vs infinite spinner

| Field | Content |
|-------|---------|
| **File** | `MeshService.kt` `catch (Exception)`; `MainActivity.kt` `while (!nodeReady)` |
| **Code path** | JNI/disk/keystore failure on cold start (including after OEM process death) |
| **Platform assumption** | `initNode` always succeeds. Documented in freeze audit RFB-NET-01. |
| **Potential failure** | Hang, not always a crash. Samsung aggressive kill + sticky restart increases hit rate. |
| **Mitigation** | `nodeFailed` flag, `MyceliumStability` log with stack, MainActivity timeout + retry (no infinite loop). |
| **Runtime verification** | Cold start after force-stop; optional storage-full. |
| **Status after this task** | STATICALLY MITIGATED. VERIFIED ON REAL SAMSUNG HARDWARE: no. |

---

### S06 — LocalOnlyHotspot / STA pause

| Field | Content |
|-------|---------|
| **File** | `LocalHotspotHelper.kt`, `WifiPauseHelper.kt`, `ShareAppViewModel.kt` |
| **Code path** | `startLocalOnlyHotspot`; API 29+ `disconnect`/`enableNetwork` |
| **Platform assumption** | Hotspot always starts; pause works on modern APIs. Android: `SecurityException`, blank SSID, `ERROR_TETHERING_DISALLOWED` / `INCOMPATIBLE_MODE` when OEM hotspot/VPN/Knox-like policy applies. API 29+ STA disconnect is a no-op for third-party apps. |
| **Potential failure** | Crash if `SecurityException` uncaught (now caught). Fail-to-share if Samsung blocks SoftAP — must fall back to Direct / Quick Share, not crash. |
| **Mitigation** | Already: exception mapping, skip pause on API 29+, Direct fallback, user copy. No Samsung private API. |
| **Runtime verification** | **Required.** Samsung hotspot + Auto Hotspot / One UI restrictions are OEM policy. |
| **Status after this task** | Prior share work + logging. Hotspot **success** is NEEDS RUNTIME. VERIFIED ON REAL SAMSUNG HARDWARE: no. |

---

### S07 — FileProvider / split APK / install

| Field | Content |
|-------|---------|
| **File** | `ShareIntent.kt`, `file_paths.xml`, `WifiDirectSharer` landing HTML |
| **Code path** | `FileProvider.getUriForFile`; browser package install |
| **Platform assumption** | `sourceDir` is a complete APK; install always allowed. Split installs are incomplete. Samsung Auto Blocker / unknown-sources / Play Protect block sideload (user settings, not bypassable). `getUriForFile` throws `IllegalArgumentException` if the path is not in `file_paths`. |
| **Potential failure** | Incomplete APK; install blocked (not a Mycelium crash); FileProvider crash if cache path wrong. |
| **Mitigation** | Cache-path only; refuse splits; catch `IllegalArgumentException` on share sheet; landing copy for Samsung settings. |
| **Runtime verification** | Install from share on a Samsung with Auto Blocker on (expect **block**, then user changes setting). |
| **Status after this task** | STATICALLY MITIGATED for provider/split. Install UX is NEEDS RUNTIME. VERIFIED ON REAL SAMSUNG HARDWARE: no. |

---

### S08 — Broadcast receivers / lifecycle

| Field | Content |
|-------|---------|
| **File** | `SosViewModel.onCleared`, `MiniAppProcessActivity` dispose, `ThermalMonitor.start` |
| **Code path** | `unregisterReceiver`; `PowerManager.addThermalStatusListener` (never removed) |
| **Platform assumption** | Register always succeeded; thermal listener leak is acceptable. Unregister without matching register → `IllegalArgumentException`. |
| **Mitigation** | `runCatching` on unregister (log if unexpected). Thermal listener leak is not a crash; leave unless a leak report exists. |
| **Runtime verification** | Rotate / leave SOS / leave mini-app. |
| **Status after this task** | STATICALLY MITIGATED for unregister. Thermal leak: accepted. VERIFIED ON REAL SAMSUNG HARDWARE: no. |

---

### S09 — Notifications (API 33+)

| Field | Content |
|-------|---------|
| **File** | `MainActivity` POST_NOTIFICATIONS; `MeshService` `notify()` |
| **Code path** | Chat/mail/SOS notifications |
| **Platform assumption** | FGS notification does not need POST_NOTIFICATIONS (Android 13 FGS exemption). Other notifies may be silently dropped if denied — not typically a crash. |
| **Potential failure** | No crash expected. Samsung may hide FGS notification in a delayed drawer. |
| **Mitigation** | No new permission prompts. Diagnostics do not log notification body text. |
| **Runtime verification** | Deny notifications; confirm no crash (G2). |
| **Status after this task** | No code change required for crash. NEEDS RUNTIME for deny-path evidence. |

---

### S10 — Coroutine scope in `MeshService`

| Field | Content |
|-------|---------|
| **File** | `MeshService.kt` `SupervisorJob()` without `CoroutineExceptionHandler` |
| **Code path** | BLE collect loops, crisis refresh |
| **Platform assumption** | Child failures are ignored. Uncaught in a launched coroutine can cancel the supervisor depending on context; `CoroutineExceptionHandler` is the documented way to observe failures. |
| **Potential failure** | Silent drop of BLE loop; or process crash if exception escapes a thread. |
| **Mitigation** | Handler logs via `MyceliumStability`, does not swallow `CancellationException`. |
| **Runtime verification** | Optional. |
| **Status after this task** | STATICALLY MITIGATED (diagnostics). VERIFIED ON REAL SAMSUNG HARDWARE: no. |

---

### S11 — Wi-Fi Aware / NSD / multicast

| Field | Content |
|-------|---------|
| **File** | `WifiAwareDiscoveryHost.kt`, `WifiMulticastLock.kt` |
| **Code path** | Aware attach; `createMulticastLock` |
| **Platform assumption** | Aware optional (`as?` already). Multicast `as WifiManager` assumed non-null. NSD fallback assumed `NSD_SERVICE` non-null. |
| **Potential failure** | NPE if WIFI_SERVICE or NSD_SERVICE null (rare). Aware unavailable on many Samsungs → NSD fallback already. |
| **Mitigation** | Safe cast on WifiManager for multicast and NsdManager for NSD fallback. |
| **Runtime verification** | Aware on/off on One UI. |
| **Status after this task** | STATICALLY MITIGATED for multicast/NSD null. Aware success: NEEDS RUNTIME. |

---

### S12 — QR encode empty payload

| Field | Content |
|-------|---------|
| **File** | `PeersScreen.kt` `generateQrCode` |
| **Code path** | ZXing `QRCodeWriter.encode` |
| **Platform assumption** | Payload is non-empty. Empty string can throw. |
| **Potential failure** | Crash if a blank invite is shown. |
| **Mitigation** | Guard empty payload. |
| **Runtime verification** | Not Samsung-specific. |
| **Status after this task** | STATICALLY MITIGATED. |

---

### S13 — Package installation APIs

Mycelium does **not** call `PackageInstaller` on Device B (no app yet). Failure is the **system** installer / Auto Blocker. No in-process crash path to fix. Landing page already explains settings. **NEEDS RUNTIME** (P2P-MT-16).

---

### S14 — Storage (`OfflineBootstrap` Downloads)

Listing `DIRECTORY_DOWNLOADS` can throw or return null under scoped storage. Already caught. Not a Samsung-specific crash. Join via QR/SAF. **NEEDS RUNTIME** for whether Samsung Files/Chrome placement differs.

---

### S15 — Location / remaining system-service casts

| Field | Content |
|-------|---------|
| **File** | `LocationHelper.kt`; BLE stop paths; `WifiPauseHelper` |
| **Code path** | `LOCATION_SERVICE` / `BLUETOOTH_SERVICE` non-null cast; `requestLocationUpdates` after a permission check |
| **Platform assumption** | System services always exist; location permission remains valid until the next check. |
| **Potential failure** | NPE on null service; `SecurityException` if the user revokes location while a one-shot request is in flight. |
| **Mitigation** | Safe casts; catch `SecurityException` / `IllegalArgumentException` on location requests; log via `MyceliumStability`. |
| **Runtime verification** | Optional (not Samsung-specific). |
| **Status after this task** | STATICALLY MITIGATED. VERIFIED ON REAL SAMSUNG HARDWARE: no. |

---

## Explicitly not done (no evidence / out of freeze)

- Battery exemption dialog / `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- Samsung Quick Share private APIs
- Catch-all `catch (Exception) {}`
- Claiming One UI idle-kill behavior without an idle test

---

## Manual tests (device-dependent)

IDs for a future Samsung (or any OEM) session. Record model, One UI / Android version, app SHA, pass/fail, logcat `MyceliumStability`.

| ID | Steps | Expected |
|----|--------|----------|
| **OEM-MT-01** | API 26–32 emulator or device: enable Bluetooth mesh (or open a mini-app). | No `NoSuchMethodError`. |
| **OEM-MT-02** | Cold start after force-stop. | UI ready or **error + retry**, never infinite spinner. |
| **OEM-MT-03** | Share phone-to-phone on Samsung. | Hotspot or Direct or Quick Share; URL not localhost; no crash. |
| **OEM-MT-04** | Deny Nearby/location on Share. | Error copy, no crash. |
| **OEM-MT-05** | BLE mesh on, revoke Bluetooth permission in system Settings. | Scan/advertise stops; no crash; `MyceliumStability` line. |
| **OEM-MT-06** | Install APK from share with Auto Blocker **on**. | OS blocks; landing text matches; app does not bypass. |
| **OEM-MT-07** | Same with Auto Blocker off / unknown apps allowed for Chrome. | Install completes or honest failure. |
| **OEM-MT-08** | Leave app idle 4+ hours (screen off). | Mesh notification present **or** killed — record only. Do not “fix” with battery exemption in this freeze. |
| **OEM-MT-09** | POST_NOTIFICATIONS deny. | No crash. |

---

## Freeze backlog

Remaining Samsung **verification** (not more speculative code) is listed under RFB-OEM-01, RFB-OEM-02, and RFB-OEM-03 in `RELEASE_FREEZE_BACKLOG.md`.

Record results in [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md) (Samsung identity + HV / OEM-MT rows). Procedure: [HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md). Logs: [CRASH_REPORTING.md](./CRASH_REPORTING.md).
