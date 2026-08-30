# Phone-to-phone hardware tests (freeze)

**Harness:** [HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md) HV-10 / HV-11. Record devices in [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md). Logs: [CRASH_REPORTING.md](./CRASH_REPORTING.md).

**Not executed in the 2026-08-29 code change.** Fill rows when devices are available. Logcat filter: `ShareAppViewModel`, `WifiDirectSharer`, `LocalHotspot`, `WifiDirectShare`, `ShareP2P`, `P2P-MT`, `MyceliumStability`.

Install under test: GitHub/debug **universal** APK (not a Play split). Record models, Android version, app SHA, APK SHA-256 from the Share screen.

| Test ID | Steps | Expected | Result | Evidence |
|---------|--------|----------|--------|----------|
| **P2P-MT-01** | API 26 emulator (or device). Deny nothing required, start phone-to-phone so hotspot fails or skip to Direct. | No `NoSuchMethodError` from `registerReceiver`. Process stays up. | *unrun* | logcat + API level |
| **P2P-MT-02** | Fresh install API 33+. Deny Nearby Wi‑Fi. Tap phone-to-phone. | Rationale / error, no crash. Quick Share still offered. | *unrun* | screenshot |
| **P2P-MT-03** | Same as MT-02 then **Don't ask again**. | Permanent-deny copy + Open app settings. No crash. | *unrun* | screenshot |
| **P2P-MT-04** | API 26–32. Location **off**, grant location permission if asked. | Location-disabled copy; hotspot not started. | *unrun* | screenshot |
| **P2P-MT-05** | Grant permissions; start phone-to-phone. | Wi‑Fi QR only if SSID non-blank. Install URL host is **not** `localhost` / `127.0.0.1`. | *unrun* | photo of URL |
| **P2P-MT-06** | Device B joins Device A Wi‑Fi/Direct, opens the URL in Chrome. | Landing page loads; SHA-256 matches Device A. `GET /` not empty. | *unrun* | screenshot both |
| **P2P-MT-07** | Device B taps Install, completes system installer. If unknown-apps prompt: follow landing steps. | APK installs. If blocked, landing text matches the OS dialog (unknown apps / Samsung Auto Blocker / Play Protect). **No bypass.** | *unrun* | notes |
| **P2P-MT-08** | After install, Device B scans Device A **Step 3** invite QR in Contacts (or picks `.myc` on onboarding). | Mesh join path works **or** failure recorded honestly. Downloads auto-import is **not** required to pass. | *unrun* | notes |
| **P2P-MT-09** | Device A: rotate screen while sharing. | Session continues; URL unchanged. | *unrun* | observation |
| **P2P-MT-10** | Device A: Back / leave Share while Device B is on the landing page. | Server stops; Device B cannot download. Matches keep-open hint. | *unrun* | observation |
| **P2P-MT-11** | Device A: Stop. | Hotspot/Direct torn down; STA Wi‑Fi restored only if pause ran (API ≤28). | *unrun* | Wi‑Fi list |
| **P2P-MT-12** | Lock Device A 30s while sharing (screen kept on). | Still serving **or** OEM killed it (record). | *unrun* | notes |
| **P2P-MT-13** | Wi‑Fi off, start Direct fallback (force hotspot fail if needed). | Wi‑Fi disabled copy or Direct failure; no crash. | *unrun* | notes |
| **P2P-MT-14** | Quick Share APK from Share screen (universal install). | Chooser attaches APK; `file_paths.xml` has no `root-path`. | *unrun* | observation |
| **P2P-MT-15** | Play split install (if available): tap Quick Share / phone-to-phone. | `split_apk` error. No incomplete base APK sent. | *unrun* | optional |
| **P2P-MT-16** | Samsung (if device exists): Auto Blocker on, attempt install from landing. | Install blocked; user can open settings and retry. App does not crash or attempt bypass. | *unrun* | One UI version |

G3 freeze pass requires MT-05, MT-06, MT-07 plus an honest MT-08 on two physical phones ([RELEASE_FREEZE_PLAN.md](./RELEASE_FREEZE_PLAN.md) G3).

Samsung / OEM crash and idle checks are **OEM-MT-01–09** in [SAMSUNG_STABILITY_AUDIT.md](./SAMSUNG_STABILITY_AUDIT.md) (logcat `MyceliumStability`). Those are not G3; they are G4 / RFB-OEM-03.
