# iOS Core Bluetooth constraints for Mycelium mesh

**Status:** Engineering reference  
**Last updated:** 2026-07-04  
**Audience:** iOS host developers, product, support

This document separates **Apple platform limits** (cannot be fixed in app code) from **implementation choices** in `ios/Mycelium/BLE/`. Android’s foreground `MeshService` is the parity target for *behavior*, not for *reliability in background*.

Related: [MOBILE_PLATFORMS.md](./MOBILE_PLATFORMS.md) · [ADR 002 BLE mesh transport](./adr/002-ble-mesh-transport.md) · [DISCOVERY.md](./DISCOVERY.md)

---

## Summary matrix

| Capability | Foreground (app active) | Background (`bluetooth-*` modes + restoration) | Android foreground service |
|------------|-------------------------|--------------------------------------------------|----------------------------|
| **F1** Connectable peripheral + invite GATT read | ✅ Reliable | ⚠️ Best-effort — see §Background peripheral | ✅ Reliable |
| **F1** Central scan + invite read handoff | ✅ Reliable | ⚠️ Scan throttled; handoff latency seconds–minutes | ✅ Reliable |
| **F2** MYBL side-channel relay (GATT write) | ✅ Reliable | ⚠️ Only while iOS keeps BLE session alive | ✅ Reliable |
| **F3** MYLP stream frames over BLE | ✅ Reliable | ⚠️ Same as F2; no guaranteed 2 s flush cadence | ✅ Reliable |
| **Peripheral + central simultaneously** | ✅ Supported | ⚠️ Supported but battery/OS throttled | ✅ Supported |
| **Always-on mesh node (no internet)** | ✅ While app foregrounded | ❌ **Not a platform guarantee** | ✅ Intended design |
| **2 s outbound relay polling** | ✅ `BleMeshHost` timer | ❌ Timers suspended — relay only on BLE wakeups | ✅ Coroutine loop |
| **45 s invite refresh** | ✅ Timer | ⚠️ On GATT read + restoration only | ✅ Timer |
| **Advertise arbitrary 128-bit service UUID** | ✅ | ⚠️ Must be a service the app publishes; no extra UUIDs in background | ✅ |
| **Manufacturer data with peer id** | N/A (privacy E0.3) | ❌ Not used; would not help in background anyway | N/A |
| **User-dismissed app → mesh carrier** | Stops when user swipes away* | ❌ | ✅ Service persists |

\*Unless Bluetooth events or location/VOIP/other BG modes wake the process — **not** dependable for disaster mesh.

---

## What we implemented (not a platform gap)

These exist in `ios/Mycelium/BLE/` and mirror Android `bootstrap/Ble*.kt`:

| Component | Role |
|-----------|------|
| `BleInviteGattServer` | `CBPeripheralManager` GATT server — invite read + mesh-data write |
| `BleBootstrapAdvertiser` | Connectable advertising with Mycelium service UUID + local name |
| `BleBootstrapScanner` | Filtered central scan for `6e400001-…` |
| `BleInviteReader` | Central connect + invite characteristic read |
| `BleMeshHandoff` | Invite → `connectTransportInvite` + link registry |
| `BleMeshDataSession` | Long-lived GATT client for chunked outbound frames |
| `BleMeshRelay` | Drains Rust F2/F3 queues (`drainBleOutbound`, `drainBleStreamOutbound`) |
| `BleMeshHost` | Orchestrator with restoration IDs |

**State Preservation / Restoration identifiers** (required for background BLE):

- Peripheral: `network.mycelium.ble.peripheral`
- Central: `network.mycelium.ble.central`

**Info.plist** (see `ios/Mycelium/Resources/Info.ble.plist.snippet`):

- `NSBluetoothAlwaysUsageDescription`
- `UIBackgroundModes`: `bluetooth-central`, `bluetooth-peripheral`

Rust contract unchanged: `ble_transport.rs`, `ble_mesh.rs`, UniFFI surface in `mycelium.udl`.

---

## Apple platform limits (do not promise otherwise)

### 1. No Android-equivalent foreground service

iOS has **no** API for an always-running mesh relay comparable to Android’s sticky foreground service. When the app is suspended:

- Swift timers (`Timer` for 2 s relay / 45 s invite refresh) **stop**.
- Outbound relay runs only when Core Bluetooth delegate callbacks run (connect, read, write, restoration).
- Product copy must **not** claim “iPhone works as a full mesh router in pocket with screen off” at Android parity.

### 2. Background advertising restrictions

With `bluetooth-peripheral` background mode and a published GATT service:

- iOS **may** continue connectable advertising for services the app is actively hosting.
- Advertising interval in background is **OS-controlled** (much slower than foreground; Apple documents on the order of seconds, not milliseconds).
- You **cannot** rely on advertising **service UUIDs you are not hosting** or arbitrary manufacturer payloads for discovery while backgrounded.
- If the user force-quits the app, peripheral advertising **stops** until the user relaunches.

**Implication:** A nearby Android node may take **much longer** to discover an backgrounded iPhone than another Android device.

### 3. Background central scanning restrictions

With `bluetooth-central`:

- Scanning can continue in background but is **throttled** (duplicate filtering, batching, reduced duty cycle).
- `CBCentralManagerScanOptionAllowDuplicatesKey: false` means repeated handoff attempts depend on rediscovery events.
- Scan + connect handoff while backgrounded is **best-effort**, not real-time.

### 4. Dual role (central + peripheral)

Supported on iOS, but both roles share one Bluetooth controller with **stricter scheduling** when backgrounded. Expect lower throughput and higher latency than Android F1–F3 loops.

### 5. Process lifetime

| Event | Peripheral host | Central scan | Rust node |
|-------|-----------------|--------------|-----------|
| App backgrounded | ⚠️ Restoration may resume | ⚠️ Throttled | ⚠️ Suspended unless BG task / audio hack |
| User swipe-kill | ❌ Stops | ❌ Stops | ❌ Stops |
| Bluetooth toggle off | ❌ Stops | ❌ Stops | — |
| Low Power Mode | ⚠️ Further throttled | ⚠️ Further throttled | ⚠️ |

The **Rust libp2p stack does not run continuously** in background unless separately kept alive (not in scope of BLE module alone).

### 6. No mDNS / Wi‑Fi mesh lock on iOS

Wi‑Fi discovery uses libp2p mDNS without Android’s multicast lock. That is independent of BLE but affects “full offline node” story on iPhone.

---

## What restoration makes *possible* (best-effort, not guaranteed)

When `CBPeripheralManagerOptionRestoreIdentifierKey` / `CBCentralManagerOptionRestoreIdentifierKey` are set and the app was **not** force-quit:

1. **Incoming central connection** to read invite GATT may wake the app briefly — F1 inbound handoff *to* this iPhone can succeed.
2. **Incoming write** to mesh-data characteristic can wake the app — F2/F3 *inbound* frames may be ingested.
3. **Restored scan** may rediscover peers — outbound handoff *from* this iPhone may eventually run.
4. **Restored peripheral connections** may allow continued GATT writes for outbound relay **while iOS keeps the session**.

These are **opportunistic**. They improve disaster-mesh *chance* but do not create an always-on relay.

---

## Foreground expectations (parity with Android)

When the Mycelium iOS app is **active** (or recently active with Bluetooth session):

- iPhone **can** act as a mesh participant: advertise invite, accept mesh-data writes, scan, hand off, and flush outbound queues on the 2 s cadence.
- GATT UUIDs and chunk format match Android/desktop (`BleMeshGatt`, MYLC/MYLP).
- This closes the previous gap where iOS was **central scan only** with no peripheral host.

---

## Product / support guidance

**Safe to say:**

- “On iPhone, Mycelium can join the BLE mesh **while the app is open**.”
- “With Bluetooth permissions, the app may **continue limited mesh activity in the background** when iOS allows.”
- “Background behavior is **best-effort** and varies by iOS version, battery state, and whether the app was force-quit.”

**Do not say:**

- “iPhone is a full offline mesh router like Android.”
- “Messages relay every 2 seconds in the background on iOS.”
- “Background advertising is as fast as foreground.”
- “Force-quit does not affect mesh participation.”

---

## Testing checklist

1. **Foreground F1:** Two devices — central reads invite, `connectTransportInvite` succeeds.
2. **Foreground F2/F3:** Send chat while libp2p path down; verify GATT relay via logs (`BleMeshRelay`, `BleInviteGatt`).
3. **Background F1 inbound:** App backgrounded on peripheral; peer connects and reads invite (may require wait).
4. **Force-quit:** Confirm advertising/scan stop — document for support.
5. **Restoration:** Kill app via Xcode stop (not swipe); relaunch via Bluetooth event — managers restore with correct IDs.
6. **Low Power Mode:** Measure discovery latency increase.

---

## File index

| Path | Purpose |
|------|---------|
| `ios/Mycelium/BLE/BleMeshHost.swift` | Entry point for mesh host |
| `ios/Mycelium/BLE/BleInviteGattServer.swift` | Peripheral GATT + advertising |
| `docs/MOBILE_PLATFORMS.md` | Host strategy |
| `android/.../MeshService.kt` | Android reference orchestration |
