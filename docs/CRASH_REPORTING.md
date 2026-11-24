# Crash reporting (Android freeze)

How to collect **enough** diagnostics to identify a Mycena crash, and **where** to store them so secrets and chat never land in git.

**Related:** [HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md), [SAMSUNG_STABILITY_AUDIT.md](./SAMSUNG_STABILITY_AUDIT.md) (`MycenaStability`), [SECURITY.md](../SECURITY.md) (vulnerability reports).

This is **not** a crash-analytics product. There is no in-app telemetry upload.

---

## 1. What a useful report contains

| Include | Do not include |
|---------|----------------|
| Operation / HV id (e.g. HV-05, P2P-MT-05) | Chat, mail, SOS, bulletin **bodies** |
| Lifecycle (`fg` / `bg` if logged) | Invite URIs (`mycena://…`) |
| Android API / One UI / model | Storage keys, hex blobs ≥32 chars |
| Exception type + stack | Private keys, tokens, `.myc` contents |
| APK SHA-256 + git SHA | Full `bugreport.zip` in the repo |
| Filtered logcat path (local) | Unfiltered `*:V` dumps in PRs |

In-app tag **`MycenaStability`** already records: `op=`, `api=`, `mfr=`, `model=`, `lifecycle=`, `subtype=` and a **redacted** stack. Uncaught exceptions are logged then handed to the previous handler (process still dies).

---

## 2. Exact commands (this repo)

Package: `io.github.mycena_project.app`.
Multiple devices: `export ANDROID_SERIAL=<serial>` (`adb devices`).

### 2.1 Preferred: capture script

```bash
# Mycena repo root
./scripts/android/capture-freeze-evidence.sh info
./scripts/android/capture-freeze-evidence.sh start          # optional run-id argument
# reproduce the crash
./scripts/android/capture-freeze-evidence.sh stop
./scripts/android/capture-freeze-evidence.sh crash           # extra dump to stdout
./scripts/android/capture-freeze-evidence.sh apk-sha
```

Output directory: `artifacts/hardware-runs/<id>/` (gitignored via `artifacts/`).

`stop` also writes:

- `logcat.txt` — filtered live capture
- `logcat-crash.txt` — `logcat -b crash`
- `dropbox-data_app_crash.txt` / `dropbox-tombstone.txt` — may be empty without root

### 2.2 Manual adb (same filters as the script)

Clear, then reproduce, then dump:

```bash
adb logcat -c

adb logcat -v threadtime \
  MycenaStability:I MeshService:I AndroidRuntime:E DEBUG:I libc:F \
  ShareAppViewModel:I WifiDirectSharer:I LocalHotspot:I WifiDirectShare:I \
  WifiPauseHelper:I OfflineBootstrap:I BleBootstrap:I BleScanner:I BleScanDuty:I \
  BleInviteGatt:I WifiDirectBearer:I LocalDataWiper:I WifiAwareDiscovery:I \
  *:S
```

Crash buffer only:

```bash
adb logcat -b crash -d -v threadtime
```

System drop box (last app crash; often empty on production builds):

```bash
adb shell dumpsys dropbox --print data_app_crash
adb shell dumpsys dropbox --print SYSTEM_TOMBSTONE
```

Tombstones under `/data/tombstones` usually need a **rooted** or `userdebug` image. Prefer `adb bugreport` locally if dropbox is empty and you must have a native crash (see §3).

### 2.3 ANR

```bash
adb pull /data/anr/traces.txt ./traces.txt   # often permission-denied on user builds
adb bugreport ./artifacts/hardware-runs/bugreport.zip
```

Store the zip **only** under `artifacts/`. Bugreports contain more than Mycena (accounts, nearby Wi-Fi names). Do not attach them to public GitHub issues.

### 2.4 Confirm the process / FGS

```bash
adb shell dumpsys activity services io.github.mycena_project.app/.MeshService
adb shell pidof io.github.mycena_project.app
```

### 2.5 Device identity for the matrix

```bash
./scripts/android/capture-freeze-evidence.sh info
# equivalent props:
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.oneui    # Samsung; empty on Pixel
adb shell dumpsys package io.github.mycena_project.app | grep -E 'versionName|versionCode'
```

---

## 3. Where to store evidence

| Location | What | In git? |
|----------|------|---------|
| `artifacts/hardware-runs/` | Raw logcat, dropbox, optional bugreport | **No** (`artifacts/` in `.gitignore`) |
| [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md) | Model, OS, SHA, HV result, **path name** of the run dir | Yes (no log bodies) |
| GitHub issue / private advisory | Redacted stack + HV id + SHA | Yes, after redact |
| `docs/freeze-evidence/` | Optional later **summary** note (plan §5) | Yes, if it has **no** logs |

If you must share a snippet in a PR: copy **only** `MycenaStability` / `AndroidRuntime` lines after checking they contain no invite URI or `storageKeyHex`.

Redact locally (same rules as `StabilityDiagnostics.redact`): drop `mycena://` URIs, `storageKeyHex=…`, long hex.

---

## 4. Filing a freeze crash

1. HV / P2P-MT / OEM-MT id.
2. Manufacturer, model, Android, One UI if Samsung.
3. Git SHA + APK SHA-256.
4. Result: crash / hang / spinner / OEM block.
5. Pointer to local `artifacts/hardware-runs/<id>/` (not committed).
6. Backlog: new row only if it is a **reproducible P0** not already covered (e.g. RFB-NET-01). Do not open a second roadmap.

Security-sensitive crashes (key material in logs, auth bypass): [SECURITY.md](../SECURITY.md) / GitHub private advisory — not a public issue with attachments.
