# Hardware test matrix (release freeze)

**Status:** Partial. Pixel 6: HV-01, HV-02, HV-03, HV-04 (notifications), HV-05, HV-12 short, HV-13, HV-16. **All other rows remain `unrun`.** Pixel 6 battery notes in [BATTERY_PROFILING_RESULTS.md](./BATTERY_PROFILING_RESULTS.md) are **not** this matrix.

## Resume (next hardware session)

Checkpoint **2026-08-30**. Device A is Google Pixel 6 (`oriole`), onboarded, Technical Beta `0.1.75` / `76`. **Do not** uninstall, `pm clear`, panic-wipe, `make android-nuke`, or HV-17/HV-18 until G3 is done — that identity is live.

**APK (gitignored — keep on this machine, do not rebuild unless you re-record hashes):**
`artifacts/technical-beta/mycena-0.1.75-technical-beta-android.apk`
SHA-256 `276e653ee6c128445a83b133bf98c9b3b83a306fc461859f4fbb6a9dacf11678`
Signing key is the local JKS under `artifacts/technical-beta/` (also gitignored). **Not** `make android-install` (debug, different key).

When a second Android is available: [HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md) **§2.5** — G3 minimum HV-10/11 (P2P-MT-05–08), then HV-07/08 if time. Capture: `./scripts/android/capture-freeze-evidence.sh start two-phone`.

One-device leftovers (optional, non-destructive first): HV-15 reboot, HV-06 BLE empty scan, HV-19/20.

**How to run:** [HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md)
**Crashes / adb:** [CRASH_REPORTING.md](./CRASH_REPORTING.md)
**Gates:** [RELEASE_FREEZE_PLAN.md](./RELEASE_FREEZE_PLAN.md) §4–§5.1
**Queue:** [RELEASE_FREEZE_BACKLOG.md](./RELEASE_FREEZE_BACKLOG.md) RFB-HW-01, HW-02, HW-03, OEM-01–03

Copy a filled snapshot into `docs/freeze-evidence/` **only** as a summary table (models, SHA, pass/fail). Never commit logcat, bugreports, or chat bodies.

Result vocabulary: `unrun` · `pass` · `fail` · `fail-with-limitation` · `skip-with-disposition`.

---

## 1. Device identity (minimum fleet)

### 1.1 Pixel / reference Android

| Field | Value |
|-------|--------|
| Manufacturer | Google |
| Model | Pixel 6 (`oriole`) |
| Android version | 17 (API 37), build `CP2A.260705.006`, security patch 2026-07-05 |
| One UI version | — (not Samsung) |
| App version (`versionName`) | `0.1.75` (`versionCode` 76) |
| Build identifier (git SHA) | APK built from `fb32b1e5c89b318e14b1adc2a76b7dcefac6b3ec` plus the then-dirty freeze tree (this commit). Identity of the install is the APK SHA-256, not HEAD. |
| APK SHA-256 | `276e653ee6c128445a83b133bf98c9b3b83a306fc461859f4fbb6a9dacf11678` |
| Date (UTC) | 2026-08-30 |
| Tester | local ADB session |
| Capture dir (`artifacts/hardware-runs/…`) | `artifacts/hardware-runs/hv-01-20260830T1709Z`; `hv-03-05-20260830T1714Z`; `hv-16-13-20260830T1721Z` (*not in git*) |
| Fleet result (this device, overall) | `unrun` (HV-01–05, HV-12 short, HV-13, HV-16 recorded; remainder unrun) |

Use a current Pixel or AOSP-like device for G2 “current-API”. An **API 26 emulator** may be recorded as a **separate** identity row for registerReceiver / deny-path smoke; it does not replace a physical phone for G3.

### 1.2 Samsung Galaxy

| Field | Value |
|-------|--------|
| Manufacturer | Samsung |
| Model | |
| Android version | |
| One UI version | |
| App version (`versionName`) | |
| Build identifier (git SHA) | |
| APK SHA-256 | |
| Date (UTC) | |
| Tester | |
| Capture dir | *not in git* |
| Fleet result (this device, overall) | `unrun` |

Until this table has a named model + One UI + SHA, **VERIFIED ON REAL SAMSUNG HARDWARE** stays empty. Public copy: [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md) (G4 option b).

### 1.3 Additional devices (optional)

| Manufacturer | Model | Android | One UI | App version | Git SHA | Date | Tester | Result |
|--------------|-------|---------|--------|-------------|---------|------|--------|--------|
| | | | | | | | | `unrun` |

---

## 2. Scenario results

Record **Device A** as the identity row above. Device B: manufacturer/model/OS in Notes if used.

| ID | Category | Device A | Device B | Result | Evidence | Tester | Date | Notes |
|----|----------|----------|----------|--------|----------|--------|------|-------|
| HV-01 | Fresh install | Pixel 6 | — | `pass` | `artifacts/hardware-runs/hv-01-20260830T1709Z` (`adb install` Success; installed SHA matches freeze APK; not DEBUGGABLE) | local ADB | 2026-08-30 | Prior debug install uninstalled first (different signing key). |
| HV-01 | Fresh install | Samsung | — | `unrun` | | | | |
| HV-02 | First launch | Pixel 6 | — | `pass` | same dir; screenshot `hv-01-after-mainactivity.png`; resumed `OnboardingActivity`; pid present; crash buffer empty | local ADB | 2026-08-30 | Onboarding UI (display name), not an infinite spinner. Airplane mode + Wi-Fi on. Not HV-03 (name not submitted). |
| HV-02 | First launch | Samsung | — | `unrun` | | | | |
| HV-03 | Onboarding | Pixel 6 | — | `pass` | `artifacts/hardware-runs/hv-03-05-20260830T1714Z/hv-03-main-shell.png` (Chat shell: Kontakte empty, QR hint; no `.myc`) | local ADB | 2026-08-30 | Display-name flow completed after unlock (operator). Identity persisted across force-stop. |
| HV-03 | Onboarding | Samsung | — | `unrun` | | | | |
| HV-04 | Permissions (grant) | Pixel 6 | — | `pass` | `runtime-perms.txt`: `POST_NOTIFICATIONS` granted=`true` (`USER_SET`). FGS noti id 7761. | local ADB | 2026-08-30 | Location / Nearby / Bluetooth still **denied** (HV-06 / HV-20). No crash on grant. |
| HV-04 | Permissions (grant) | Samsung | — | `unrun` | | | | |
| HV-05 | Mesh startup | Pixel 6 | — | `pass` | `MeshService` `isForeground=true` after two `am force-stop` + relaunch; screenshots `hv-05-cold-1.png` / `hv-05-cold-2.png`; crash buffer empty | local ADB | 2026-08-30 | Airplane mode on, Wi-Fi on. No `nodeFailed` spinner. |
| HV-05 | Mesh startup | Samsung | — | `unrun` | | | | |
| HV-06 | BLE | | | `unrun` | | | | OEM-MT-05 / BLE-air |
| HV-07 | Wi-Fi / LAN | | | `unrun` | | | | A2-1 / B1 |
| HV-08 | Messaging | | | `unrun` | | | | no message bodies in git |
| HV-09 | Offline operation | | | `unrun` | | | | A2-6 or BLE-air |
| HV-10 | Phone-to-phone sharing | | | `unrun` | | | | P2P-MT-05; mode used |
| HV-11 | Phone-to-phone installation | | | `unrun` | | | | P2P-MT-06–08; OEM-MT-06/07 |
| HV-12 | Background / foreground | Pixel 6 | — | `pass` | HOME ~30 s: pid `14316` survived; `MeshService` still `isForeground=true`; resume screenshot `hv-12-after-fg.png` | local ADB | 2026-08-30 | Short bg only. Not 4h idle. |
| HV-12 | Background / idle 4h+ | Samsung | — | `unrun` | | | | OEM-MT-08; do not “fix” with battery exemption |
| HV-13 | Screen lock / unlock | Pixel 6 | — | `pass` | `artifacts/hardware-runs/hv-16-13-20260830T1721Z`: KEYCODE_SLEEP 30s `mDreamingLockscreen=true`; pid `15482` + `MeshService` `isForeground=true`; after wake `MainActivity` + Chat shell; crash buffer empty | local ADB | 2026-08-30 | PIN pad not observed on wake (USB + charging). Not P2P-MT-12 (no Share session). |
| HV-14 | Process restart | | | `unrun` | | | | A2-5 |
| HV-15 | Reboot | Pixel | — | `unrun` | | | | |
| HV-15 | Reboot | Samsung | — | `unrun` | | | | |
| HV-16 | Update (`install -r`) | Pixel 6 | — | `pass` | `artifacts/hardware-runs/hv-16-13-20260830T1721Z`: `adb install -r` Success; `firstInstallTime` 19:09:17 kept; `lastUpdateTime` → 19:21:29; `MainActivity` not Onboarding; Chat shell same; FGS after pid `14316`→`15482` | local ADB | 2026-08-30 | Same Technical Beta key. No contact/sent message existed (identity + empty Kontakte kept). |
| HV-17 | Uninstall / reinstall | | — | `unrun` | | | | |
| HV-18 | Panic / wipe | Pixel | — | `unrun` | | | | |
| HV-18 | Panic / wipe | Samsung | — | `unrun` | | | | |
| HV-19 | Degraded network | | | `unrun` | | | | relay and/or LAN-no-relay |
| HV-20 | Denied permissions | Pixel | — | `unrun` | | | | |
| HV-20 | Denied permissions | Samsung | — | `unrun` | | | | |

---

## 3. Plan §5.1 minimum scenarios (G6 summary)

Cross-check after HV rows are filled. Leave blank until run.

| ID | Scenario | Device A | Device B | OS | Build SHA | Result | Logs / hash | Tester | Date |
|----|----------|----------|----------|----|-----------|--------|-------------|--------|------|
| A2-1 | LAN only chat | | | | | `unrun` | | | |
| A2-2 | Relay only | | | | | `unrun` | | | |
| A2-5 | Reconnect after force-quit | | | | | `unrun` | | | |
| B1 | LAN no QR | | | | | `unrun` | | | |
| G3 | Phone-to-phone APK + bootstrap | | | | | `unrun` | | | |
| G4 | Samsung idle / FGS survival | | — | | | `unrun` | | | |
| Panic | Wipe + relaunch | | — | | | `unrun` | | | |
| Reinstall | `install -r` / uninstall disposition | | — | | | `unrun` | | | |
| BLE-air | Airplane + BLE chat | | | | | `unrun` | | | |

---

## 4. Cross-walk to older IDs

| This matrix | Existing IDs |
|-------------|--------------|
| HV-10, HV-11 | P2P-MT-01–16 |
| HV-02, HV-06, HV-10–12, HV-20 | OEM-MT-01–09 |
| HV-07, HV-08, HV-14 | mvp-smoke-test A2-1, A2-5, B1 |
| HV-18 | Plan Panic row / RFB-PERS-01 |
| HV-16, HV-17 | RFB-PERS-04 |
