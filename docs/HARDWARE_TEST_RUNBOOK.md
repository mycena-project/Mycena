# Hardware test runbook (release freeze)

**Purpose:** Another developer can execute the freeze Definition of Done (Android hardware gates) without asking the original author.

**Does not replace:** [RELEASE_FREEZE_PLAN.md](./RELEASE_FREEZE_PLAN.md) (gates), [RELEASE_FREEZE_BACKLOG.md](./RELEASE_FREEZE_BACKLOG.md) (queue).
**Results live in:** [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md).
**Crashes / logcat:** [CRASH_REPORTING.md](./CRASH_REPORTING.md).

This freeze is **Android-first, GitHub / sideload APK**. Play Store listing is out of scope. Do not claim G2–G6 pass until the matrix rows are filled (or skipped with a written disposition).

---

## 1. What “done” means for a hardware session

Each scenario in this runbook must end as one of:

| Result | Meaning |
|--------|---------|
| **pass** | Expected result observed on the recorded device(s). |
| **fail** | Reproducible miss vs expected; treat as a freeze defect unless accepted. |
| **fail-with-limitation** | Failed for a documented OEM/OS reason; user-visible copy matches; no crash. |
| **skip-with-disposition** | Not run; owner, date, and why (no second phone, no Samsung, relay down, …). |
| **unrun** | Default. Not evidence. |

Never write “should work.” Never mark **VERIFIED ON REAL SAMSUNG HARDWARE** without a named Galaxy + One UI + app SHA + log pointer.

Existing specialized procedures (run as cited; do not copy them here):

| Topic | Document | IDs |
|-------|----------|-----|
| Phone-to-phone share/install | [PHONE_TO_PHONE_MANUAL_TEST.md](./PHONE_TO_PHONE_MANUAL_TEST.md) | P2P-MT-* |
| Samsung / OEM crash & idle | [SAMSUNG_STABILITY_AUDIT.md](./SAMSUNG_STABILITY_AUDIT.md) | OEM-MT-* |
| LAN / QR / reconnect (Level 0) | [mvp-smoke-test.md](./mvp-smoke-test.md) Phase A/B | A1–A5, B1–B3 |
| Share behavior (code, not evidence) | [OFFLINE_DISTRIBUTION.md](./OFFLINE_DISTRIBUTION.md) | — |
| Public Samsung exception | [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md) | until G4(a) |

---

## 2. Developer workflow (copy/paste)

### 2.1 Machine

- `adb` (Android platform-tools)
- USB debugging authorized; **one** device unless you set `ANDROID_SERIAL`
- Freeze APK: **Technical Beta** universal `assembleRelease` APK (not debug, not a Play split). Build or install:

```bash
# from Mycelium repo root
./scripts/build-technical-beta-apk.sh
adb install -r artifacts/technical-beta/mycelium-*-technical-beta-android.apk
./scripts/android/capture-freeze-evidence.sh apk-sha
```

Do **not** use `make android-apk` / `assembleDebug` as the freeze hardware binary (different signing key; phone-to-phone will refuse to share it).

Record **git SHA** (`git rev-parse HEAD`) and **APK SHA-256** in the matrix. Settings shows the in-app version name.

UniFFI Kotlin is gitignored. For local JVM tests from a clean checkout: `./scripts/android-test.sh` ([android/README.md](../android/README.md)). Hardware validation does **not** wait on that; G5 is a separate gate.

### 2.2 Capture (every session)

```bash
./scripts/android/capture-freeze-evidence.sh info
./scripts/android/capture-freeze-evidence.sh start   # optional: start my-run-id
# … perform HV-* tests on the device …
./scripts/android/capture-freeze-evidence.sh stop
```

Observational pre-check (no install, no wipe, does **not** mark HV PASS):

```bash
./scripts/android/run-hardware-tests.sh inspect
# or: make android-hw-inspect
```

If the app crashes:

```bash
./scripts/android/capture-freeze-evidence.sh crash
```

Paste **device identity + result + path to `artifacts/hardware-runs/<id>/`** into [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md). **Do not commit** that directory (see crash reporting).

### 2.3 Reset between single-device tests

| Need | Command |
|------|---------|
| Fresh identity / as-if new install data | `make android-nuke` (`adb shell pm clear`) |
| Keep identity, restart process | force-stop from recents or `adb shell am force-stop network.mycelium.app` |
| Reinstall same APK, keep data | `adb install -r <apk>` (same signing key) |

Package: `network.mycelium.app`.

### 2.4 Suggested order (one Pixel + one Samsung, two days)

**Day 1 — each phone alone (G2):** HV-01 → HV-05, HV-20, HV-12, HV-14, HV-18.
**Day 1 — two phones together (G3 + messaging):** HV-07, HV-08, HV-10, HV-11 (P2P-MT-05–08).
**Day 1 optional:** HV-06 (BLE on, same room), HV-09 (airplane + same Wi-Fi or BLE-air).
**Overnight (G4 / OEM-MT-08):** HV-12 idle 4+ hours on Samsung **or** skip-with-disposition + keep KNOWN_LIMITATIONS Samsung-untested.
**Day 2:** HV-13, HV-15, HV-16, HV-17; fill A2-5 / B1 if not already covered by HV-08 / HV-14.

Emulator **API 26** may stand in for HV-01 crash-smoke (`NoSuchMethodError`) as P2P-MT-01 / OEM-MT-01. It does **not** replace a physical Pixel or Galaxy row for G3/G4/G6.

### 2.5 Resume: one Pixel 6 in matrix + optional desktop

**Checkpoint 2026-08-30 — start here when Device B appears.** Pixel 6 already has HV-01–05, HV-12 short, HV-13, HV-16. Keep that install. Full reminder: [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md) (Resume box at top).

**Desktop is not Device B for G3.** Phone-to-phone APK share (HV-10 / HV-11, P2P-MT-05–08) needs **two Android installs**. Tauri / `mycelium-cli` on this Mac can stand in for **LAN peer + chat** (HV-07 / HV-08 / then HV-09 / HV-14) if both are on the **same Wi-Fi** (not client-isolated). Mac/laptop BLE is **not** a freeze BLE-air peer ([KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md)).

**Still doable on Device A alone** (do **not** HV-18 panic before a two-phone window — that wipes identity):

| ID | One-phone meaning |
|----|-------------------|
| HV-06 | BLE mesh on, scan/advertise **no crash**, empty nearby list is honest. Revoke BT while scanning (OEM-MT-05). Not “BLE chat works.” |
| HV-13 | Lock 30 s, unlock (PIN). App + FGS still up or recover. |
| HV-15 | Reboot; open app; identity kept; mesh starts after open. |
| HV-16 | `adb install -r` **same** Technical Beta APK; data kept. |
| HV-17 | Uninstall + reinstall; data **lost** (`allowBackup=false`). Redo onboarding. Do **after** two-phone if identity matters. |
| HV-18 | Panic wipe UI + fail-closed. **Last** on this identity. |
| HV-19 | Airplane / Wi-Fi / relay toggles; record FGS + UI. Two-party chat still needs HV-08. |
| HV-20 | Deny Nearby/location/BT; no crash. Distinct from HV-04 grant. |
| HV-12 4h | Optional overnight screen-off; not required on Pixel if Samsung idle is skipped. |

**10-minute two-Android sprint** (when a second phone appears):

1. Copy `artifacts/technical-beta/mycelium-0.1.75-technical-beta-android.apk` to the second phone (USB / laptop). SHA-256 `276e653ee6c128445a83b133bf98c9b3b83a306fc461859f4fbb6a9dacf11678`. **Not** `make android-install` (debug).
2. Device A: stay onboarded. Airplane **off** or Wi-Fi on same SSID as B. Do not panic-wipe first.
3. Device B: fresh install → onboard (HV-01–03 on B, abbreviated).
4. **G3 minimum:** Device A Share → phone-to-phone → grant Nearby → **P2P-MT-05** (URL host ≠ localhost) → B opens URL → hash match → install (**P2P-MT-06–07**). Then B scans A Contacts QR or `.myc` (**P2P-MT-08**). Then A leaves Share; B cannot download (**P2P-MT-10**).
5. If time: HV-07 LAN peers, HV-08 two-way encrypted chat (no bodies in git), HV-09 airplane+Wi-Fi LAN. HV-06 two-phone BLE only if BT granted on both.
6. Capture: `./scripts/android/capture-freeze-evidence.sh start two-phone` on A; Device B model/API in matrix notes.

---

## 3. Freeze gate map

| Gate | Must include (HV ids) |
|------|------------------------|
| **G2** | HV-01, HV-02, HV-03, HV-04, HV-05, HV-18, HV-20 on ≥1 current-API phone; ideally also API 26-adjacent |
| **G3** | HV-10 + HV-11 on two physical phones (P2P-MT-05–08) |
| **G4** | Samsung identity row + OEM-MT-03/06/07/08 **or** skip-with-disposition + KNOWN_LIMITATIONS |
| **G6** | All HV rows on the matrix: pass / fail-with-limitation / skip-with-disposition |
| **G9** | HV-01 install of the **RC** APK on one matrix device |

---

## 4. Test catalog (HV-01 … HV-20)

Fill pass/fail in the matrix, not here. **Evidence** is the minimum to attach (file path under `artifacts/hardware-runs/`, screenshot optional).

### HV-01 — Fresh install

| Field | Content |
|-------|---------|
| **Gates** | G2, G9 |
| **Preconditions** | APK SHA recorded. Device has USB debugging. Uninstall Mycelium if present (`adb uninstall network.mycelium.app`) so this is not an update. |
| **Steps** | `adb install` of the **Technical Beta** APK (`artifacts/technical-beta/mycelium-*-technical-beta-android.apk`). Do **not** use `make android-install` (that is the debug APK). Open the launcher icon. |
| **Expected** | Install succeeds. App appears. No Play-only split-APK error. |
| **Evidence** | `adb install` output; `device.txt` from the capture script; APK SHA-256. |
| **Notes** | Signature mismatch vs a Play/CI build: uninstall first (script may do this unless `--no-replace`). |

### HV-02 — First launch

| Field | Content |
|-------|---------|
| **Gates** | G2 |
| **Preconditions** | HV-01; no leftover data (`pm clear` if unsure). |
| **Steps** | Open Mycelium. Wait up to **15 s**. |
| **Expected** | Onboarding **or** main UI. Never an infinite spinner. Failure shows error + **Try again** (`mesh_start_failed`). |
| **Evidence** | Observation; on failure `MyceliumStability` lines (`op=MeshService.initNode` or `MainActivity.nodeWait`). |
| **Notes** | OEM-MT-02 is the same check after force-stop. |

### HV-03 — Onboarding

| Field | Content |
|-------|---------|
| **Gates** | G2 |
| **Preconditions** | HV-02; first-run (no existing identity). |
| **Steps** | Complete display-name onboarding. If testing join-from-share, pick `.myc` via the file picker (do not rely on Downloads). |
| **Expected** | Reaches main shell. Identity exists. Sidecar join (if used) still has bootstrap peers after first complete. |
| **Evidence** | Screenshot of main shell (optional); note whether `.myc` was used. |
| **Notes** | RFB-PERS-03: wipe-then-apply-after-wipe. Downloads auto-join is **not** required to pass. |

### HV-04 — Permissions (grant path)

| Field | Content |
|-------|---------|
| **Gates** | G2 |
| **Preconditions** | HV-03. |
| **Steps** | Allow **notifications** if prompted (API 33+). In Settings, enable Bluetooth mesh if testing HV-06; grant Nearby / Bluetooth / location as the OS asks. |
| **Expected** | Prompts appear. Granting does not crash. Mesh notification may show (FGS). |
| **Evidence** | Note which permissions were granted. Deny path is **HV-20**. |
| **Notes** | FGS notification can appear without POST_NOTIFICATIONS (Android 13 exemption). |

### HV-05 — Mesh startup

| Field | Content |
|-------|---------|
| **Gates** | G2 |
| **Preconditions** | HV-03. |
| **Steps** | After onboarding, confirm the mesh service is up: UI usable; optional `adb shell dumpsys activity services network.mycelium.app/.MeshService`. |
| **Expected** | `MeshService` running as foreground service. Cold start twice in a row (plan G2). |
| **Evidence** | Second cold start noted; logcat `MeshService` / `MyceliumStability` if it failed. |
| **Notes** | If `nodeFailed`, retry must start the service again. |

### HV-06 — BLE

| Field | Content |
|-------|---------|
| **Gates** | G6 / RFB-HW-03 |
| **Preconditions** | Two phones **or** one phone + known BLE peer. Bluetooth on. BLE mesh enabled in Settings; Nearby/Bluetooth granted. |
| **Steps** | Enable Bluetooth mesh. Place devices in range. Optional: airplane mode + Bluetooth on for BLE-air (DISASTER_MESH 2.4–2.5). |
| **Expected** | Scan/advertise starts without crash. Nearby list or handoff log, **or** honest “nobody in range.” Revoke Bluetooth in system Settings while scanning → no crash (OEM-MT-05). |
| **Evidence** | `BleScanner` / `BleBootstrap` / `MyceliumStability` (`BleScanner.startScan` SecurityException is a **pass** if UI stops scanning). |
| **Notes** | Pocket-mesh unverified → skip-with-disposition and keep RFB-DOC-05 / KNOWN_LIMITATIONS. Do not claim airplane BLE chat unless this row is **pass**. |

### HV-07 — Wi-Fi (LAN discovery)

| Field | Content |
|-------|---------|
| **Gates** | G6 (A2-1 / B1) |
| **Preconditions** | Two devices on the **same** Wi-Fi (not client-isolation / guest AP). Mesh running on both. |
| **Steps** | Wait ~30 s. Check Connect / peers. Optional: leave invite QR unused (B1). |
| **Expected** | Peer appears and chat can be attempted (HV-08). Guest Wi-Fi: document failure; QR still valid. |
| **Evidence** | Models + SSID type (home vs guest); pass/fail. |
| **Notes** | Relays optional. This is not phone-to-phone APK share (HV-10). |

### HV-08 — Messaging (encrypted chat)

| Field | Content |
|-------|---------|
| **Gates** | G6 (A1, A2-1) |
| **Preconditions** | Two onboarded devices; pair via QR **or** LAN (HV-07). |
| **Steps** | Open chat with the contact. Confirm **End-to-end encrypted** (not “key not yet exchanged”). Send both directions. |
| **Expected** | Messages deliver. No plaintext send on the happy path (SD-081). |
| **Evidence** | Scenario ID + timestamp; do **not** paste message bodies into git. |
| **Notes** | mvp-smoke-test A1. Unsolicited inbound → pending request (A5) is P1 if time allows. |

### HV-09 — Offline operation

| Field | Content |
|-------|---------|
| **Gates** | G6 (A2-6 / BLE-air) |
| **Preconditions** | HV-08 worked on LAN **or** BLE. |
| **Steps** | Enable airplane mode (or disable cellular + internet). Keep Wi-Fi on for LAN-only, **or** Wi-Fi off + BLE on for BLE-air. Send chat again. |
| **Expected** | LAN: mDNS path still works. BLE-air: chat within documented disaster-mesh time **or** skip-with-disposition. |
| **Evidence** | Which radios were on; result. |
| **Notes** | Relay-only (A2-2) is a **different** test: different networks, internet on. Skip if no relay. |

### HV-10 — Phone-to-phone sharing

| Field | Content |
|-------|---------|
| **Gates** | G3 |
| **Preconditions** | Device A has the **universal** APK. Two physical phones. Follow [PHONE_TO_PHONE_MANUAL_TEST.md](./PHONE_TO_PHONE_MANUAL_TEST.md) P2P-MT-02–05, 09–14 as time allows; **minimum G3: P2P-MT-05**. |
| **Steps** | Device A: Share Mycelium → phone-to-phone. Grant Nearby/location. Keep Share screen open. |
| **Expected** | Hotspot, Direct, or documented fallback. Install URL host is **not** `localhost` / `127.0.0.1`. Blank SSID → no Wi-Fi QR. No crash on deny (P2P-MT-02). |
| **Evidence** | Share mode used; photo of URL **host**; logcat `ShareAppViewModel` / `WifiDirectSharer` / `LocalHotspot`. |
| **Notes** | Share is foreground-only. Leaving the screen stops the server (P2P-MT-10). |

### HV-11 — Phone-to-phone installation

| Field | Content |
|-------|---------|
| **Gates** | G3, G4 install UX |
| **Preconditions** | HV-10 serving. Device B has a browser. Samsung: record Auto Blocker on/off (OEM-MT-06/07, P2P-MT-16). |
| **Steps** | Device B joins Wi-Fi/Direct, opens the URL, downloads APK, system installer. Then scan Device A invite QR or pick `.myc`. |
| **Expected** | SHA-256 on landing matches Device A. Install completes **or** OS blocks with landing text (unknown apps / Auto Blocker / Play Protect). **No bypass.** Join path works or is recorded honestly. |
| **Evidence** | APK hash both sides; One UI version if blocked; P2P-MT-06–08 notes. |
| **Notes** | Play split installs must refuse share (P2P-MT-15) if a Play build is available. |

### HV-12 — Background / foreground

| Field | Content |
|-------|---------|
| **Gates** | G2, G4 |
| **Preconditions** | Mesh running (HV-05). |
| **Steps** | Home button (background ~30 s), return. Then (Samsung / OEM-MT-08) leave **4+ hours** screen off. |
| **Expected** | Short background: app resumes; mesh still up **or** recovers on foreground. Long idle: notification still present **or** process killed — **record only**. Do not add battery-exemption UX in this freeze. |
| **Evidence** | Duration; whether `MeshService` survived (`dumpsys activity services`). |
| **Notes** | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is out of freeze. |

### HV-13 — Screen lock / unlock

| Field | Content |
|-------|---------|
| **Gates** | G3 (share), G6 |
| **Preconditions** | Mesh running. Optional: Share session active (P2P-MT-12). |
| **Steps** | Lock 30 s; unlock. If sharing, keep screen-on flag in mind (Share tries to keep the screen on). |
| **Expected** | No crash. Share still serving **or** OEM killed it (record). Chat still opens. |
| **Evidence** | Observation. |
| **Notes** | Not a substitute for HV-12 idle. |

### HV-14 — Process restart

| Field | Content |
|-------|---------|
| **Gates** | G6 A2-5 |
| **Preconditions** | HV-08 succeeded (paired chat). |
| **Steps** | Force-stop both apps (recents swipe or `am force-stop`). Reopen. Do not re-scan. |
| **Expected** | Chat still encrypts after a brief wait. UI not stuck on spinner (HV-02). |
| **Evidence** | Timestamp; pass/fail. |
| **Notes** | mvp-smoke-test A2-5 / B2. |

### HV-15 — Reboot

| Field | Content |
|-------|---------|
| **Gates** | G6 |
| **Preconditions** | HV-05. |
| **Steps** | Reboot the phone. Open Mycelium (do not expect mesh before the user opens the app unless documented otherwise). |
| **Expected** | App launches. Mesh starts after open (no silent FGS-from-boot requirement). Identity kept. |
| **Evidence** | Observation. |
| **Notes** | Sticky FGS after reboot is not a freeze P0. |

### HV-16 — Update (`install -r`)

| Field | Content |
|-------|---------|
| **Gates** | P1 / RFB-PERS-04 |
| **Preconditions** | App installed with identity + a contact or a sent message. **Same signing key** as the new APK. |
| **Steps** | `adb install -r <apk>`. Open app. |
| **Expected** | Data kept (Welcome back / same identity). `allowBackup=false` is unrelated to `install -r`. Debug vs Play keys **cannot** update in place (uninstall + data loss). |
| **Evidence** | `adb install -r` output; identity still present. |
| **Notes** | Document fail-with-limitation if keys differ. |

### HV-17 — Uninstall / reinstall

| Field | Content |
|-------|---------|
| **Gates** | G6 Reinstall |
| **Preconditions** | Identity exists. |
| **Steps** | `adb uninstall network.mycelium.app`. Confirm icon gone. `adb install <apk>`. Open. |
| **Expected** | Onboarding as first launch. Previous identity **gone** (no backup restore). |
| **Evidence** | Uninstall + install output. |
| **Notes** | Contrast with HV-16. `make android-nuke` clears data without uninstall. |

### HV-18 — Panic / wipe

| Field | Content |
|-------|---------|
| **Gates** | G2 Panic |
| **Preconditions** | Identity + some chat or contacts. |
| **Steps** | Settings → Panic wipe → confirm. App should relaunch onboardable. Confirm UI mentions mesh copies can remain on other devices. |
| **Expected** | Launchable. New identity. Old messages gone **on this device**. Limitation visible. |
| **Evidence** | Screenshot of confirm copy (optional); post-wipe first-run. Log tag `LocalDataWiper` OK. |
| **Notes** | Do not log wiped keys. RFB-PERS-01. |

### HV-19 — Degraded network

| Field | Content |
|-------|---------|
| **Gates** | G6 A2-2 / B3 |
| **Preconditions** | HV-08 if testing relay; or HV-07 for LAN-without-relay. |
| **Steps** | **Relay-only:** different networks or Wi-Fi off on one device; internet on; QR already scanned. **LAN, relay unreachable:** block relay / disable internet; Status should allow local mesh. |
| **Expected** | Relay path works **or** skip if relay down. LAN without relay: peers still connect. |
| **Evidence** | Network setup; result. |
| **Notes** | Do not treat relay outage as a client P0 if LAN still works. |

### HV-20 — Denied permissions

| Field | Content |
|-------|---------|
| **Gates** | G2 / RFB-PERM-02 |
| **Preconditions** | Fresh enough to see prompts, or revoke in system Settings. |
| **Steps** | Deny notifications. Deny BLE/Nearby when enabling mesh. Deny camera on QR. Share: deny Nearby (API 33+) or location (API 26–32); optional Don’t ask again. |
| **Expected** | No crash. User-visible explanation. Share still offers Quick Share. Mesh/BLE skipped with UI/log. |
| **Evidence** | Notes per permission; OEM-MT-04/09; P2P-MT-02–04. |
| **Notes** | Two API levels if possible (plan G2). |

---

## 5. After the session

1. Fill [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md) (devices + HV rows).
2. If Samsung was **not** used, leave the Galaxy identity table empty and keep the KNOWN_LIMITATIONS exception — that is G4(b), not a silent skip.
3. Point RFB-HW-01 / OEM-03 evidence at the filled rows.
4. Keep raw logs only under `artifacts/hardware-runs/` (gitignored).
