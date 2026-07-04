# Disaster mesh roadmap

**Status:** Active  
**Goal:** When cellular, Wi‑Fi, and internet fail, people within physical reach can still find each other, exchange messages, and coordinate — without assuming a working relay or prior QR scan for every hop.

This document is the **long-horizon plan** beyond Level 0 LAN + Android BLE (ADR 002). Phases are ordered by user value and engineering risk.

See also: [CONNECTIVITY_MODES.md](./CONNECTIVITY_MODES.md), [LEVEL0_ROADMAP.md](./LEVEL0_ROADMAP.md), [ADR 002](./adr/002-ble-mesh-transport.md), [ADR 003](./adr/003-desktop-ble-and-disaster-mesh.md), [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md).

---

## What works today (baseline)

| Capability | Status |
|------------|--------|
| Same-subnet Wi‑Fi auto-connect (mDNS) | Shipped |
| QR / signed invite v3 + durable contacts | Shipped |
| Relay + opt-in rendezvous (internet required) | Shipped |
| Android BLE F1 invite handoff | Shipped |
| Android BLE F2 WireMessage side-channel | Shipped |
| Android BLE F3 libp2p `/ble/…` overlay | Shipped |
| Desktop BLE | **Active on macOS/Linux** (blew host) | Phase 1b ✓ |
| Multi-hop epidemic routing at city scale | **Not supported** |
| Store-and-forward DTN custody | **Phase 3 shipped (core)** — see [CUSTODY_DTN.md](./CUSTODY_DTN.md) |
| Configurable hop limits > 8 for disaster profile | **Custody messages default 32 hops** |

**Honest limit:** Pocket-to-pocket mesh between **Android phones** in BLE range is the near-term disaster story. Laptops without BLE transport do not extend the mesh yet. Hundreds of hops across a city is **out of scope** until routing, custody, and battery policies are designed (Phase 3–4).

---

## Phase 1 — Desktop parity + BLE host (current)

**Goal:** Same Settings semantics as Android; visible status; start desktop BLE bridge.

| ID | Task | Status |
|----|------|--------|
| 1.1 | Desktop immediate-apply network toggles + `settings-updated` | Done |
| 1.2 | Status bar `connectivity_detail` + Connect copy | Done |
| 1.3 | Contact durability UX (QR once, paths ephemeral) | Done |
| 1.4 | `CONNECTIVITY_MODES.md` + this roadmap | Done |
| 1.5 | ADR 003 desktop BLE architecture | Done |
| 1.6 | `DesktopBleHost` stub + `ble_bootstrap` setting | Done |
| 1.7 | **blew** GATT scan/advertise → same NodeCommand as Android | Done (macOS/Linux) |
| 1.8 | macOS Bluetooth permission (`Info.plist`) | Done |

**Exit criteria:** User can opt into BLE on Mac; logs show host lifecycle; F1 parity demo Mac ↔ Android when 1.7 lands.

**Reasonable decision:** Phase 1 ships **UX + stub + ADR** first so disaster docs and Settings are truthful; btleplug wiring is the next PR, not blocked on DTN.

---

## Phase 2 — Pocket mesh hardening (Android-first)

**Goal:** Reliable BLE-only sessions between phones when all IP paths fail.

| ID | Task | Target |
|----|------|--------|
| 2.1 | BLE relay loop tuning (scan duty, battery tiers) | Energy-aware scan intervals tied to `NodeState` |
| 2.2 | F2 backpressure + queue caps on GATT | Avoid RAM blow-up when many peers advertise |
| 2.3 | F3 stream reconnection + link registry cleanup | Stale `/ble/` addrs dropped when GATT drops |
| 2.4 | Integration test: two emulators / two Pixels BLE-only | CI optional job |
| 2.5 | User-facing “offline mesh” indicator on Android | Distinct from “internet off” |

**Exit criteria:** Two phones in airplane mode + BLE on exchange chat within 60s without prior TCP.

---

## Phase 3 — Delay-tolerant custody (DTN)

**Goal:** Messages **wait** on intermediate devices until a path appears — not just direct BLE/TCP neighbors.

| ID | Task | Notes |
|----|------|-------|
| 3.1 | Custody envelope flag + hop budget separate from gossip `max_hops` | Disaster profile: 32–64 hops **documented default**, not 500 |
| 3.2 | Encounter log (who we met, when) for forward scheduling | Privacy: local-only, TTL, user clear |
| 3.3 | Epidemic / spray-and-wait routing module | Research-backed; cap copies per message |
| 3.4 | “Disaster profile” Settings preset | Internet off, BLE on, custody on, rendezvous off |

**Reasonable decisions:**

- **Hop limit default 32** for disaster custody (configurable up to 128 for drills) — balances reach vs spam/battery; not unbounded.
- **No global store-and-forward without consent** — nodes only custody for contacts or explicit scopes (SOS, bulletin opt-in).
- **Duplicate suppression** via existing envelope IDs + bloom where affordable.

**Exit criteria:** Sim scenario: A → B → C store-and-forward over hours with intermittent BLE encounters; delivery without live end-to-end TCP.

---

## Phase 4 — Scale, drills, and ops

**Goal:** Field exercises and honest capacity claims.

| ID | Task |
|----|------|
| 4.1 | Field drill playbook (10–50 nodes, neighborhood scale) |
| 4.2 | Metrics: custody queue depth, BLE bytes, encounter rate |
| 4.3 | Desktop + iOS BLE parity (iOS host per ADR 001) |
| 4.4 | Optional Wi‑Fi Direct / AWDL research spike | Platform-specific; not blocking BLE |
| 4.5 | Documentation pass for responders / NGOs | Link from [security-for-users.md](./security-for-users.md) |

**Explicit non-goals (Phase 4):**

- Million-node mesh
- Guaranteed delivery under adversarial partition
- Replacing satellite / SMS for life-critical comms

---

## Architecture sketch (target end state)

```
┌──────────────── App (chat, SOS, bulletin) ─────────────────┐
│  Settings: internet / rendezvous / crowd / disaster profile │
└───────────────────────────┬────────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────────┐
│  mycelium-node — single pipeline for all transports         │
│    TCP · QUIC · relay · mDNS · BLE ingest (F2) · BLE p2p (F3) │
│    + Phase 3: custody queue · epidemic forwarder            │
└───────────────────────────┬────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
   Android host        Desktop host         iOS host (future)
   GATT F1–F3          btleplug F1–F3       CoreBluetooth
```

---

## Risk register

| Risk | Mitigation |
|------|------------|
| BLE battery drain | Energy modes; scan duty cycle; user off switch |
| Custody spam / illegal content | Scope limits; SOS rate limits; no anonymous global custody |
| Privacy (encounter log) | Local TTL; export/delete; no cloud sync |
| False disaster readiness claims | [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md) + this roadmap status column |
| macOS Bluetooth sandbox | Tauri entitlements + documented permission flow (Phase 1b) |

---

## How this relates to LEVEL0_ROADMAP

- **Phase B3** (BLE handoff) → shipped on Android (ADR 002).
- **Phase 1 here** → desktop UX + BLE host path (extends B3 to macOS).
- **Phases 3–4 here** → new work **after** pocket mesh hardening; update LEVEL0 gap tracker when Phase 3 starts.

When completing a task, update the **Status** column and link the PR in the commit message (`Disaster mesh 1.1: …`).
