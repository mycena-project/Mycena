# Mycelium — work remaining (audit 2026-07-05)

Master tracker from a full-repo scan.  
**Legend:** ✅ done · ⏳ outstanding

---

## Category 1 — Needs human review or input

| Item | Source |
|------|--------|
| Privacy policy GitHub Pages URL live | PRE_RELEASE_GAPS |
| Play Console Data Safety + checklist | PLAY_CONSOLE_SUBMIT_CHECKLIST |
| Sign ACCEPTED_SECURITY_RESIDUALS | Pre-release |
| External security review | EXTERNAL_SECURITY_REVIEW |
| Red-team mini-apps | SECURITY_ROADMAP Phase 0 |
| Relay no-IP-retention ops audit | PRIVACY_ROADMAP Phase A |
| Hardware-bound identity keys (StrongBox / Secure Enclave) | SECURE_HARDWARE_STATUS, SD-120/SD-131 |
| User-auth gate before key use | IMPLEMENTATION_STATUS §3 |
| Root/jailbreak detect + refuse | SD-120 |
| MeshCoin legal positioning | MESHCOIN_POSITIONING |
| MLS / group PSK replacement | SD-101 |
| Tor / Nym transport | SD-180, Phase 5 |
| Manual Phase A smoke matrix (Mac + Pixel) | LEVEL0_ROADMAP A2 |
| Field drill playbook (10–50 nodes) | DISASTER_MESH 4.1 |
| iOS host shell + Keychain | ios/README.md |
| Auto GPS geohash (vs manual prefix) | Product/privacy decision — manual prefix UX shipped |

---

## Category 2 — Needs hardware and devices

| Item | Source |
|------|--------|
| Android BLE-only chat drill (airplane + BLE) | DISASTER_MESH Phase 2.4–2.5 |
| Mac ↔ Android BLE F1 pairing demo | DISASTER_MESH Phase 1 |
| Wi‑Fi Direct / NFC / WebRTC cross-host field validation | ADR 007/010 |
| Wi‑Fi Aware discovery bearer | ADR 009 |
| iOS CoreBluetooth host | MOBILE_PLATFORMS |
| StrongBox validation on physical devices | SECURE_HARDWARE_STATUS |
| Optional 520-node libp2p soak (nightly CI) | AR-06 |
| Play closed-beta device fleet | BETA_SECURITY_ASSESSMENT |

---

## Category 3 — Software backlog

### ✅ Completed (2026-07-05 audit passes)

| ID | Item |
|----|------|
| — | `MeshProfile::Crowd` for manual crowd mode |
| — | `crowd_density_active` in metrics + UI |
| — | Geohash prefix Settings + FFI `set_local_geohash_prefix` |
| — | Wi‑Fi Direct inbound verified (AR-06 doc fix) |
| **P0** | **Gossip `mesh_n` hot-swap on density backoff** — vendored libp2p-gossipsub patch + `gossip_mesh.rs` |
| **P1** | **Multi-shard SOS listen** — parent geohash prefixes (`listen_shard_prefixes`, default min 3) |
| **P1** | **Bulletin geohash on post API** — `post_bulletin(..., geohash?)` + publish hint |
| **P1** | **Crisis drop metrics** — `crisis_dropped_background_{gossip,forward,sync}` in NodeMetrics |
| **P1** | **Android offline mesh indicator** — `StatusViewModel.offlineMeshActive` banner |
| **P2** | **Desktop `postMessage` origin lock (SD-051)** — MiniApp iframe uses `"null"` origin, validates inbound |
| **P2** | **Wi‑Fi Aware / WebRTC metrics in `NodeMetrics`** — ADR 009/010 bearer observability |
| **P2** | **MyceliumLab geohash eclipse / custody sims** — CI gates for geohash-clustered sybil scenarios |
| **P3** | **Volunteer coordination (phase 2)** — `mycelium/volunteer/v1` + Android/Desktop SOS UI + FFI |
| **P3** | **Relay wire semantic parity** — shared `mycelium_core::protocol` IDs on node + relay |
| **P3** | **Desktop release runbook** — signing, local build, updater smoke in `RELEASING.md` |
| **P3** | **CLI groups / wallet / panic** — subcommands + `--headless` + HTTP API routes |

### ⏳ Priority backlog (remaining)

_All Category 3 software backlog items through Iteration E are complete. Remaining work is Category 1 (sign-offs, review, legal) and Category 2 (field hardware drills)._

| P | Item | Notes |
|---|------|-------|
| — | *(none)* | See Category 1 & 2 above |

---

## Iteration plan (updated)

1. ~~**A** — Gossip hot-swap (P0)~~ ✅  
2. ~~**B** — Multi-shard SOS + bulletin geohash + crisis metrics + offline mesh (P1)~~ ✅  
3. ~~**C** — SD-051 origin lock (P2 partial)~~ ✅  
4. ~~**D** — Bearer metrics (P2) + MyceliumLab sims (P2)~~ ✅  
5. ~~**E** — P3 CLI/desktop/relay backlog~~ ✅  

After **E**, remaining work is **Category 1** (sign-offs, review, legal) and **Category 2** (field hardware drills).

---

## Related docs

- [AR-06_REMAINING_GAPS.md](./AR-06_REMAINING_GAPS.md)
- [IMPLEMENTATION_STATUS_2026-07-05.md](./IMPLEMENTATION_STATUS_2026-07-05.md)
- [DISASTER_MESH_ROADMAP.md](./DISASTER_MESH_ROADMAP.md)
- [third-party/libp2p-gossipsub/README-MYCELIUM.md](../third-party/libp2p-gossipsub/README-MYCELIUM.md)
