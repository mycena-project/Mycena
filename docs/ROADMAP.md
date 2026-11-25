# Roadmap

Mycena is a public technical beta. The core works: end-to-end encrypted chat and groups,
automatic connection on the same Wi-Fi, Bluetooth links between Android phones, an optional
relay, and a crisis mode with help requests, a resource map and volunteer tasks. Store-and-forward
delivery and direct Wi-Fi links exist as **experimental prototypes**, tested in simulation and on
one device type (Pixel 6).

The next six months turn these prototypes into something people can rely on: measured delivery
over many hops, a stable second radio link on phones from several manufacturers, one switch for
offline use, an emergency interface that works under stress, and field exercises with real
devices. Status is updated here as work lands; limits stay documented in
[KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md). Earlier, completed phases: [ROADMAP_2026_H1.md](ROADMAP_2026_H1.md).

| # | Milestone | Months | Status |
|---|-----------|--------|--------|
| M1 | [Measurement setup: simulation with 50+ devices](#m1-measurement-setup) | 1 | Planned |
| M2 | [Store-and-forward: from prototype to reliable delivery](#m2-store-and-forward) | 1–3 | Planned |
| M3 | [Direct Wi-Fi: a stable second radio link](#m3-direct-wi-fi) | 2–3 | Planned |
| M4 | [Disaster profile: one switch for offline use](#m4-disaster-profile) | 3–4 | Planned |
| M5 | [Emergency interface for non-experts](#m5-emergency-interface) | 4–5 | Planned |
| M6 | [Field exercises with 10–50 devices, F-Droid release](#m6-field-exercises-and-f-droid) | 5–6 | Planned |

---

## M1 Measurement setup

**Goal:** a reproducible setup that every later milestone is measured against: delivery rate,
latency and battery cost with 50+ virtual devices.

**Starting point:** `crates/mycena-sim` already has scenarios for custody/store-and-forward, dense
clusters and SOS floods, plus CI gates (`sim_ci_gates.rs`). Larger topologies, common metrics and
comparable reports are missing.

- [ ] Scenarios with 50, 100 and 200 nodes (movement models: neighbourhood, event, commuters)
- [ ] Common metrics: delivery rate, median/P95 latency, copies per message, estimated energy use
- [ ] Machine-readable report (JSON) and comparison with the previous run
- [ ] Nightly run with thresholds; results documented in `docs/`

## M2 Store-and-forward

**Goal:** tune and harden the existing custody layer (spray-and-wait) so messages measurably
arrive across many hops, with help requests first.

**Starting point:** custody queue, encounter log, copy budget (`max_custody_spray`), 64 hops,
48 h TTL, custody only for confirmed contacts. See [CUSTODY_DTN.md](CUSTODY_DTN.md).

- [ ] Copy budget and forwarding strategy tuned from M1 measurements, with the reasoning documented
- [ ] Priority for SOS/crisis traffic in the custody queue, with fairness limits against spam
- [ ] Target delivery rate reached in the M1 scenarios and locked in as a CI gate
- [ ] Privacy of the encounter log (local only, TTL, delete) reviewed and documented

## M3 Direct Wi-Fi

**Goal:** take Wi-Fi Aware (discovery) and Wi-Fi Direct (bulk data) from experimental prototype to
a stable second radio link next to Bluetooth, on phones from several manufacturers.

**Starting point:** `WifiAwareDiscoveryHost` (with NSD fallback) and `WifiDirectBearerHost` exist
in the Android app; see [ADR 007](adr/007-wifi-direct-bulk-bearer.md) and
[ADR 009](adr/009-wifi-aware-discovery-bearer.md).

- [ ] Automatic switch between BLE and Wi-Fi Direct by payload size, with fallback on failure
- [ ] Device matrix: at least 5 models from 3+ manufacturers, recorded in [HARDWARE_TEST_MATRIX.md](HARDWARE_TEST_MATRIX.md)
- [ ] Defined and tested behaviour on devices without Wi-Fi Aware
- [ ] Battery cost measured and used by the disaster profile (M4)

## M4 Disaster profile

**Goal:** one switch that prepares Mycena for an outage: internet off, Bluetooth and direct Wi-Fi
on, store-and-forward on, battery-friendly scan intervals, a clear "offline mesh active" indicator.

**Starting point:** crisis mode (priority for SOS, background traffic paused) is in place
([ADR 008](adr/008-resource-map-crisis-mode.md)); the preset itself is still open
([DISASTER_MESH_ROADMAP.md](DISASTER_MESH_ROADMAP.md), item 3.4).

- [ ] Profile on Android and desktop, easy to switch on and off, with settings before/after visible
- [ ] Scan intervals adapt to battery level and movement (using M1/M3 measurements)
- [ ] Status indicator distinct from "no internet"
- [ ] Two phones in airplane mode with Bluetooth exchange messages within 60 s

## M5 Emergency interface

**Goal:** make the existing crisis features usable by people without prior knowledge, under
stress, with a trust indicator for reports (German and English).

**Starting point:** help requests, resource map and volunteer tasks exist on Android and desktop.
The trust indicator is prepared in the [information trust track](#information-trust-track).

- [ ] Reworked flows for sending a help request, adding a resource and taking a task (at most three steps)
- [ ] Trust indicator integrated (T4–T6 below)
- [ ] User test with at least five people without prior knowledge, results documented
- [ ] German and English texts reviewed; accessibility (text size, contrast, TalkBack) checked

## M6 Field exercises and F-Droid

**Goal:** two field exercises with 10–50 devices from different manufacturers, results published,
and distribution through F-Droid.

- [ ] Exercise plan (scenario, procedure, measurement points, participant consent)
- [ ] Two exercises run, M1 metrics collected, simulation compared with the field
- [ ] Public report in `docs/` including limits and open points
- [ ] Reproducible build and inclusion in F-Droid

---

## Information trust track

Help requests and resource pins can be wrong or deliberately false. This track lets the app show,
in plain words, how many independent sources confirm a report, without collecting GPS traces or
chat content. It feeds the trust indicator in M5.

| # | Item | Status |
|---|------|--------|
| T1 | Trust core model and scoring hardening (config review, test gaps) | Base in place |
| T2 | Tracker persistence: same result after restart; panic wipe clears trust state | Store in place, restart tests open |
| T3 | Every received SOS/resource report records an observation on all relay paths | Mostly wired, audit open |
| T4 | Plain-language badges (unconfirmed / partly / confirmed / conflicting), no raw scores | German/English done, other locales open |
| T5 | "What does this mean?" help next to SOS and resources | Open |
| T6 | Trust status in notifications; confirmed reports sorted first | Open |
| T7 | Disinformation scenarios (Sybil SOS flood, conflicting pins) enforced as CI gates | Scenarios done, CI enforcement open |
| T8 | Multi-node convergence: independent observers reach the same status | Open |
| T9 | Field drill with trust metrics (share confirmed vs unverified, time to corroboration) | Open (with M6) |
| T10 | Opt-in pilot feedback on how understandable the badges are | Open |
| T11 | Short guide for responders and NGOs (German/English) | Open |
| T12 | Final report on the trust track | Open |

References: [MYCENALAB.md](MYCENALAB.md), [THREAT_MODEL.md](THREAT_MODEL.md),
`crates/mycena-sim/src/sos_disinfo_scenarios.rs`, `crates/mycena-sim/src/resource_disinfo_scenarios.rs`.

## Not planned

- Million-node mesh or guaranteed delivery under adversarial partition
- Replacing emergency numbers, satellite or SMS for life-critical communication
- iOS background Bluetooth mesh (Apple restricts it); iOS stays groundwork
- MeshCoin as money: it remains an experimental research preview ([MESHCOIN_POSITIONING.md](MESHCOIN_POSITIONING.md))
