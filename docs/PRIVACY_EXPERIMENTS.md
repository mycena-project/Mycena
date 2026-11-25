# Mycena — Experimental Privacy Layer (Anti-Metadata R&D)

**Codename:** **Shade** (experimental overlay on `mycena-node`, not a separate app)  
**Status:** Research & incremental prototype  
**Last updated:** May 2026  
**Companion:** [PRIVACY_ROADMAP.md](./PRIVACY_ROADMAP.md) · [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) · [ARCHITECTURE_RISKS.md](./ARCHITECTURE_RISKS.md)

---

## 0. Why this exists

Mycena already has **content** protection (X25519 E2E, signed wire frames) but leaks **metadata** aggressively: stable libp2p PeerIds, bloom sync, gossip scopes, timing, fanout patterns, BLE manufacturer data, relay IP paths.

**Shade** is deliberately **not** “Signal with a mesh transport.” It treats the network as an **untrusted broadcast medium** and applies delay, mixing, cover traffic, and identity fragmentation **only where mesh constraints allow**—Bluetooth and WiFi Direct first, Internet relay second.

**Design rule:** Every mechanism must ship as an **opt-in mode** with measurable battery/bandwidth cost and a fallback to today’s behaviour.

---

## 1. Threat model (Shade-specific)

### 1.1 Adversaries

| Adversary | Capability | What they learn today | Shade target |
|-----------|------------|----------------------|--------------|
| **Local passive** (café, protest) | BLE/WiFi scan, LAN capture | PeerId prefix in BLE adv; mDNS; packet sizes | No stable IDs on air; padded cells |
| **Relay operator** | IP + circuit metadata | Who dials relay, when, volume | Rotating relay circuits; cover cells |
| **Mesh peer** | Store-and-forward, gossip | Bloom shape, scopes, forward graph | Probabilistic + delayed forwards; blind sync |
| **ISP / state** | Flow analysis, DNS block | TCP/QUIC timing to relay | Burst windows; cover traffic; multi-relay |
| **Global passive** | Long-term correlation | Social graph from stable PeerId | Ephemeral routing IDs; crowd blending |

### 1.2 Assets

| Asset | Priority |
|-------|----------|
| **Social graph** (who talks to whom) | P0 |
| **Timing / volume** (when, how much) | P0 |
| **Route attribution** (who forwarded) | P1 |
| **Scope interest** (which boards you read) | P1 |
| **Physical presence** (who is near whom) | P1 |
| **Message content** | Already E2E (out of Shade scope) |

### 1.3 Non-goals (honest)

- Shade does **not** guarantee Tor-grade anonymity on Internet path in v1 experiments.
- Shade does **not** hide that *some* Mycena activity occurred on a device (RF power, app foreground).
- Shade cannot erase gossip copies on other peers’ disks.

### 1.4 Security invariant (must not regress)

> Shade metadata tricks must **never** weaken signature requirements on app-layer frames (`EncryptedDirect`, signed `Data`).

---

## 2. Architecture overview

```text
┌─────────────────────────────────────────────────────────────┐
│ App (chat / bulletin / mail) — unchanged payload semantics   │
└────────────────────────────┬────────────────────────────────┘
                             │ Envelope + E2E (existing)
┌────────────────────────────▼────────────────────────────────┐
│ Shade overlay (NEW, opt-in)                                  │
│  · Cell encoder (fixed-size frames)                          │
│  · Stochastic delay queue                                    │
│  · Cover traffic scheduler (privacy budget)                  │
│  · Ephemeral route / scope IDs                               │
│  · Mixing epoch coordinator                                  │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│ Existing: libp2p + BLE bootstrap + WiFi Direct + relay       │
└─────────────────────────────────────────────────────────────┘
```

**Incremental hook points in current code:**

| Existing component | Shade extension |
|--------------------|-----------------|
| `ProbabilisticForwardingPolicy` | Delay + directed fanout caps |
| `pending` queue (`node.rs`) | Per-cell random hold time |
| `WireMessage::SyncBloom` | Encrypted-interest variant |
| `BleBootstrapAdvertiser` | No PeerId in manufacturer data |
| `ConnectivityMode` | Shade profiles per mode |
| `mycena-sim` | Metadata oracle tests |

---

## 3. Mechanisms (creative + shippable)

Each entry: **idea → packet/behaviour → impacts → attacks → ship slice**

---

### 3.1 Probabilistic forwarding (extend existing)

**Idea:** Forwarding is already blake3-seeded probabilistic (`forwarding.rs`). Shade makes probability **privacy-aware**: lower fanout when graph privacy matters; **never** fanout-to-all on unknown `to_peer` (fixes SD-031).

| Dimension | Detail |
|-----------|--------|
| **Packet** | Unchanged envelope inside cell |
| **Relay** | Max 2 forwards per cell; exclude last-hop peer |
| **Reputation** | Strikes only on **verified** bad sigs, not on random drop |
| **Battery** | ↓ vs flood (fewer TX) |
| **Bandwidth** | ↓ |
| **UX** | Higher latency; show “mesh privacy mode” |
| **Attack** | Malicious peer hoards messages → mitigated by TTL + alternate paths |
| **Ship** | **P0** — policy change only |

---

### 3.2 Delayed stochastic propagation

**Idea:** Every forwardable cell enters `ShadeHoldQueue` with delay  
`Δ = base(mode) + Exp(λ)` — exponential jitter breaks timing correlation across hops.

| Dimension | Detail |
|-----------|--------|
| **Packet** | `ShadeHeader { hold_until_ms, epoch, cell_id }` prepended (cleartext size fixed) |
| **Relay** | Forward only after `hold_until`; relays cannot infer urgency |
| **Reputation** | N/A |
| **Battery** | ↑ radio on-time spread; better than burst flood |
| **Bandwidth** | Neutral |
| **UX** | “Delayed delivery” badge; user picks profile: Fast / Balanced / Paranoid |
| **Attack** | Flooding hold queue → cap queue + drop Low priority |
| **Ship** | **P1** — extend `PendingMessage` |

**Parameters (starting point):**

| Profile | base Δ | λ (mean extra) |
|---------|--------|----------------|
| Fast | 0 ms | 200 ms |
| Balanced | 500 ms | 2 s |
| Paranoid | 2 s | 30 s |

---

### 3.3 Fake cover packets (crowd blending)

**Idea:** Emit **cover cells** indistinguishable from real cells (same size, encrypted payload bucket). Peers cannot tell if you’re idle or active.

| Dimension | Detail |
|-----------|--------|
| **Packet** | `WireMessage::ShadeCell { epoch, size_class, ciphertext }` — ciphertext is random or duplicated dummy |
| **Relay** | Treat cover same as real; forward probabilistically |
| **Reputation** | Do not strike on cover-only peers |
| **Battery** | ↑↑ proportional to budget |
| **Bandwidth** | ↑↑ |
| **UX** | “Privacy budget” slider (MB/day); default 0 in beta |
| **Attack** | Cover-only DoS → rate limit cover TX per peer |
| **Ship** | **P2** — needs cell format |

---

### 3.4 Rolling relay identities (Internet path)

**Idea:** Daily (or per-session) **ephemeral libp2p sub-key** for relay circuits only; long-term PeerId used only for signed app auth inside cells.

| Dimension | Detail |
|-----------|--------|
| **Packet** | `PeerInfo` includes `route_pubkey` + signed binding to stable id |
| **Relay** | Sees ephemeral ID; correlation across days harder |
| **Reputation** | Reputation keyed on stable id inside signed inner frame |
| **Battery** | Neutral |
| **Bandwidth** | +1 handshake per epoch |
| **UX** | Transparent |
| **Attack** | Binding forgery → require signed link in Envelope |
| **Ship** | **P2** |

---

### 3.5 Scoped anonymous subscriptions

**Idea:** User subscribes to `H(scope_secret, epoch)` instead of `mycena/bulletin/berlin`. Only holders of secret see matching gossip.

| Dimension | Detail |
|-----------|--------|
| **Packet** | Gossip topic = 16-byte truncated hash; invite QR shares secret |
| **Relay** | Sees opaque topic id |
| **Reputation** | Scope moderator keys (future) |
| **Battery** | ↓ if client filters early |
| **Bandwidth** | Neutral |
| **UX** | “Join board” = scan secret; no public scope string on wire |
| **Attack** | Brute topic id → 128-bit space |
| **Ship** | **P2** — gossipsub topic change |

---

### 3.6 Peer mixing (mesh convergence)

**Idea:** When **k ≥ 3** peers are connected (BLE + WiFi Direct + LAN), run a **mixing round**: swap cell batches in random order before re-forwarding (onion-lite without global paths).

| Dimension | Detail |
|-----------|--------|
| **Packet** | `MixBatch { epoch, cells: Vec<ShadeCell> }` — batch size fixed (e.g. 8 cells) |
| **Relay** | Not applicable on Fly relay; **LAN/BLE only** in v1 |
| **Reputation** | Exclude mixers with high strike count |
| **Battery** | Spike during mix window |
| **Bandwidth** | 8× temporary |
| **UX** | “Crowd present — enhanced privacy” when k≥3 |
| **Attack** | Malicious mixer drops or reorders → cell ACK inside epoch |
| **Ship** | **P3** — research heavy |

---

### 3.7 Onion-inspired mesh forwarding (Shade onion-lite)

**Idea:** For multi-hop to recipient **R**, sender wraps cell in layers for hop1, hop2 using **pairwise keys** established at contact (not global Tor). Each hop peels one layer; inner signature still verified at end.

| Dimension | Detail |
|-----------|--------|
| **Packet** | `[layer1 ciphertext][layer2 ciphertext][payload]` fixed total size |
| **Relay** | Sees only prev-hop ephemeral id |
| **Reputation** | Per-hop contracts (trust ring) |
| **Battery** | ↑ crypto per hop |
| **Bandwidth** | ↑ overhead ~15% per layer |
| **UX** | Only for “Paranoid” + known path |
| **Attack** | Traffic confirmation if fanout small → combine with prob. forward |
| **Ship** | **P3** |

**Contrast with Tor:** No directory; paths built from **recent encounter graph** (BLE/WiFi meetings).

---

### 3.8 Local dead-drop message pools

**Idea:** Bulletin/mail can be deposited in **scope dead drops**: encrypted blobs keyed by `drop_id` published to gossip; fetchers use blind id. Author need not be online at fetch time.

| Dimension | Detail |
|-----------|--------|
| **Packet** | `DeadDropPut { drop_id, epoch, ciphertext }` / `DeadDropGet` via encrypted bloom interest |
| **Relay** | Forwards opaque blobs |
| **Reputation** | Size quota per peer per epoch |
| **Battery** | Fetch in burst window only |
| **Bandwidth** | Pool sync during burst |
| **UX** | “Post to board” = dead drop; no direct peer link on metadata |
| **Attack** | Spam drops → PoW sticker on put (lightweight hashcash) |
| **Ship** | **P2** — maps well to bulletin |

---

### 3.9 Burst synchronization windows

**Idea:** Replace continuous bloom sync with **scheduled sync epochs** (e.g. every 7 min ± 90 s jitter). Radio wakes only in window.

| Dimension | Detail |
|-----------|--------|
| **Packet** | `SyncBloom` only in `SyncWindowOpen` phase |
| **Relay** | Observes periodic traffic, not per-message |
| **Reputation** | Standard |
| **Battery** | ↓↓↓ major win |
| **Bandwidth** | Bursty |
| **UX** | “Last synced 3 min ago”; manual “sync now” burns budget |
| **Attack** | Window fingerprinting → jitter + cover syncs |
| **Ship** | **P1** — timer in `node.rs` |

---

### 3.10 Encrypted interest bloom filters

**Idea:** Peer A sends encrypted bloom of **wanted** drop_ids; peer B returns only matching ciphertexts using simple PIR-lite (hash-based keyed buckets) — no plaintext id list on wire.

| Dimension | Detail |
|-----------|--------|
| **Packet** | `EncInterestBloom { epoch, ebf_bytes }` + `EncSyncReply` |
| **Relay** | Opaque |
| **Reputation** | N/A |
| **Battery** | Crypto CPU in burst window |
| **Bandwidth** | Slightly larger than plain bloom |
| **UX** | Transparent |
| **Attack** | False-positive leakage → standard bloom FP analysis |
| **Ship** | **P3** (crypto design) / **P2** naive XOR mask prototype |

**P2 prototype:** XOR bloom with pairwise key — weak privacy, fast to ship for lab eval.

---

### 3.11 Trust-fragmented routing

**Idea:** Forward only through peers tagged **TrustRing** (out-of-band QR handshake). Strangers can receive cover cells but not your real forwards.

| Dimension | Detail |
|-----------|--------|
| **Packet** | Inner envelope includes `min_trust_level`; forwarder checks local trust store |
| **Relay** | Untrusted relays excluded in path selection |
| **Reputation** | Trust decays over time |
| **Battery** | ↓ if ring small |
| **Bandwidth** | May reduce reach |
| **UX** | “Trusted mesh only” toggle |
| **Attack** | Sybil ring infiltration → human-scale trust (≤ 20 peers) |
| **Ship** | **P2** |

---

### 3.12 Decoy anti-entropy traffic

**Idea:** Periodically exchange **fake** `SyncIds` / `SyncRequest` for random ids — poisons peer inference about your real backlog.

| Dimension | Detail |
|-----------|--------|
| **Packet** | Same as sync wire types; ids are random 32-byte |
| **Relay** | Wastes bandwidth globally |
| **Reputation** | Low priority drop |
| **Battery** | ↑ |
| **Bandwidth** | ↑ |
| **UX** | Off by default |
| **Attack** | Amplification if peer responds to all → cap responses |
| **Ship** | **P1** |

---

### 3.13 Adaptive packet padding (Shade cells)

**Idea:** All Shade traffic uses **size classes** {512, 1024, 2048, 4096} bytes; payload padded with PKCS#7-like marker. Class chosen for **next** message, not current (reduces length correlation).

| Dimension | Detail |
|-----------|--------|
| **Packet** | `ShadeCell { class_id, ciphertext_fixed }` |
| **Relay** | Cannot infer message type from size |
| **Reputation** | N/A |
| **Battery** | ↑ TX time on radio |
| **Bandwidth** | ↑ 10–40% |
| **UX** | Hidden |
| **Attack** | Class oracle over many messages → rotate class independently |
| **Ship** | **P1** |

---

### 3.14 Rotating scope identifiers

**Idea:** Scope string `berlin/news` → public handle rotates each epoch: `scope:v3:8f2a…`. Old epochs invalid after TTL.

| Dimension | Detail |
|-----------|--------|
| **Packet** | `ScopeAnnounce` lists hashed handles only |
| **Relay** | Cannot map to human scope |
| **Reputation** | Moderator publishes epoch rotation signed |
| **Battery** | Neutral |
| **Bandwidth** | +announce per rotation |
| **UX** | Users refresh board link weekly |
| **Attack** | Leak mapping in invite URL → secret in QR not URL |
| **Ship** | **P2** |

---

### 3.15 Privacy budgets

**Idea:** Device-wide **daily byte budget** for cover + decoy traffic (Android Data Saver meets differential privacy). When exhausted, Shade degrades to delay-only mode.

| Dimension | Detail |
|-----------|--------|
| **Packet** | N/A (scheduler) |
| **Relay** | Less cover noise |
| **Reputation** | N/A |
| **Battery** | Predictable cap |
| **Bandwidth** | User-controlled |
| **UX** | Settings: “Privacy budget: 5 MB/day” |
| **Attack** | User sets 0 → no cover for crowd blending |
| **Ship** | **P2** with cover cells |

---

### 3.16 Ephemeral routing identities (pairwise)

**Idea:** For each contact, derive `route_id = H(pairwise_secret, epoch)` used in forward headers instead of stable PeerId on outer layer.

| Dimension | Detail |
|-----------|--------|
| **Packet** | Outer: `route_id`; inner signed Envelope still has real `from_peer` |
| **Relay** | Cannot link epochs without pairwise secret |
| **Reputation** | Map route_id → stable id locally after verify |
| **Battery** | Neutral |
| **Bandwidth** | +8 bytes per cell |
| **UX** | Transparent |
| **Attack** | Inner id leak → padding inner separately |
| **Ship** | **P2** |

---

### 3.17 Crowd blending (metaphor → mechanism)

**Idea:** Forward probability **increases** with local crowd size `k` (more noise candidates); **decreases** when alone (don't stand out).

```text
P(forward) = min(0.9, 0.3 + 0.1 * k)
P(cover)   = max(0, 0.5 - 0.08 * k)   // less cover when crowd already noisy
```

| Dimension | Detail |
|-----------|--------|
| **Battery** | Smart: saves power when alone |
| **UX** | “Blending with 4 nearby devices” |
| **Ship** | **P1** with prob. forwarding |

---

## 4. Unified packet structure (proposed)

### 4.1 Shade cell (wire extension)

```text
WireMessage::ShadeCell {
  version: u8,                    // = 1
  epoch: u32,                     // rotates daily
  cell_class: u8,                 // 0..3 → size bucket
  route_id: [u8; 16],             // ephemeral, not PeerId
  mix_generation: u8,             // incremented each mix round
  hold_until_ms: u64,             // stochastic delay
  flags: u16,                     // COVER | REAL | DEAD_DROP | MIXED
  mesh_signature: Vec<u8>,        // libp2p hop sig (like EncryptedDirect)
  ciphertext: Vec<u8>,            // fixed len per cell_class
}
```

**Inner plaintext** (after decrypt at destination):

```text
ShadeInner {
  stable_from: String,            // real PeerId
  stable_to: Option<String>,
  payload: Vec<u8>,               // existing AppMessage / Envelope bytes
  inner_sig: Vec<u8>,             // author signature
}
```

### 4.2 Dead drop extension

```text
WireMessage::DeadDropPut { drop_id: [u8; 32], epoch, ciphertext }
WireMessage::DeadDropInterest { enc_bloom: Vec<u8> }
```

### 4.3 Backward compatibility

- Non-Shade peers ignore `ShadeCell` (drop or pass-through unsigned forbidden).
- Shade peers **dual-stack** during migration: real messages wrapped in cells; legacy `Data` disabled in Paranoid profile.

---

## 5. Relay behaviour under Shade

| Topic | Today | Shade |
|-------|-------|-------|
| Payload visibility | Opaque (libp2p) | Same |
| IP logging | Hosting risk | Multi-relay + encourage mesh-only |
| Circuit correlation | Per PeerId | Per ephemeral route_id |
| Rate limit | Circuit caps | + cell rate cap per circuit |
| App awareness | None | Optional: drop non-`ShadeCell` on privacy relay |

**Hostile relay model:** Relay can drop, delay, or size-correlate — **not** decrypt. Shade assumes **path diversity** (≥ 2 relays, mesh escape hatch).

---

## 6. Peer reputation interactions

| Event | Current | Shade adjustment |
|-------|---------|------------------|
| Invalid sig | Strike | Unchanged |
| Random drop | Strike | **Do not strike** (prob. forward intentional) |
| Cover flood | — | Throttle only |
| Mix batch reorder | — | Strike if authenticated bad ACK |
| Trust ring breach | — | Manual revoke + panic wipe |

**New field:** `PeerShadeStats { mix_rounds, cells_forwarded, cover_ratio }` for UI trust hints only — not consensus.

---

## 7. Transport-specific notes

### 7.1 Bluetooth mesh / BLE

| Risk | Mitigation |
|------|------------|
| Manufacturer data leaks PeerId prefix | Remove prefix; advertise only service UUID + rotating 4-byte beacon |
| BLE is identifiable | Randomize advertise interval ±30% |
| Low bandwidth | Prefer 512-byte cells; burst sync |

**Existing code:** `BleBootstrapAdvertiser.kt` — **P0 experiment:** stop embedding peer id in manufacturer data.

### 7.2 WiFi Direct

| Opportunity | Use |
|-------------|-----|
| High bandwidth local | Mixing rounds, dead-drop pool sync |
| P2P group | Crowd blending k large |

**Existing code:** `WifiDirectSharer.kt` — Shade mixing coordinator hooks on group formation.

### 7.3 Internet relay

| Constraint | Approach |
|------------|----------|
| High latency | Longer hold delays acceptable |
| Traffic analysis | Cover cells + padding mandatory in Paranoid |
| Censorship | Dead drops + scope rotation |

---

## 8. Battery & bandwidth summary

| Mechanism | Battery | Bandwidth | Default profile |
|-----------|---------|-----------|-----------------|
| Prob. forward + crowd blend | ↓ | ↓ | Balanced |
| Stochastic delay | ↔ | ↔ | Balanced |
| Burst sync | ↓↓↓ | ↔ | All |
| Padding cells | ↑ | ↑ | Paranoid |
| Cover packets | ↑↑ | ↑↑ | Opt-in budget |
| Mix rounds | ↑ spike | ↑↑ | Paranoid + k≥3 |
| Decoy sync | ↑ | ↑ | Off |
| Dead drops | ↔ | ↔ | Bulletin |

**Target:** Balanced profile ≤ **15%** extra battery vs today on typical chat day (model in sim).

---

## 9. UX implications

| User-facing | Behaviour |
|-------------|-----------|
| **Shade off** | Current Mycena |
| **Shade Balanced** | Delay + prob. forward + burst sync + no BLE id leak |
| **Shade Paranoid** | + padding + cover budget + trust ring only |
| Indicators | “Blending”, “Delayed”, “Budget 62% left” |
| Onboarding | Explain: metadata ≠ content; mesh copies remain |
| Failure | If delivery > TTL, show “not delivered — privacy mode” not silent drop |

**Do not promise:** “Untraceable” — use **“harder to trace”** with concrete sliders.

---

## 10. Attack tradeoffs (meta)

| Defence | Offence enabled |
|---------|-----------------|
| Delay | Real-time abuse reporting harder |
| Cover traffic | Bandwidth exhaustion |
| Prob. forward | Lower delivery probability |
| Dead drops | Unmoderated blob hosting |
| Trust rings | Exclusion / elitism |
| Encrypted blooms | Slightly weaker sync reliability |

**Principle:** Prefer **delivery failure** over **silent metadata leak** in Paranoid mode; prefer **delivery success** in Fast mode.

---

## 11. Ideas backlog (short)

| Idea | Feasibility | Notes |
|------|-------------|-------|
| Nym / Tor bridge transport | M | Plug-in under libp2p, not mesh-native |
| PSI full sync | L | Academic; heavy mobile CPU |
| RF steganography in BLE | L | Fun; regulatory grey |
| Ultrasound pairing | L | Covert channel risk |
| Homomorphic witness counts | L | MeshCoin future |
| Differential privacy bulletin counts | M | Aggregate posts per epoch |
| QR proximity proof of meeting | M | Strengthen trust ring |
| Chaos clock sync without NTP | M | Burst windows without metadata to NTP |

---

## 12. Open research questions

1. **Optimal delay distribution** for DTN mesh with E2E deadlines (chat vs mail)?
2. **Mixing on Android BLE** — practical batch sizes without GATT timeout?
3. **Encrypted bloom false-positive rate** vs battery under 10k dead drops?
4. **Witness correlation** — can Shade hide coin gossip metadata without killing MeshCoin preview?
5. **Legal** — dead-drop pools and cover traffic: operator liability in EU/US?
6. **Game theory** — do rational peers free-ride on cover traffic (always budget=0)?
7. **Confirmation attacks** on onion-lite when fanout ≤ 2?
8. **Epoch synchronization** without central time — can mesh epochs drift safely?

---

## 13. Prototype roadmap (incremental ship)

### Phase E0 — Lab flags (4–6 weeks)

| # | Deliverable | Mechanism | Code touch |
|---|-------------|-----------|------------|
| E0.1 | `ShadeMode` enum in node config | Off / Balanced / Paranoid | `NodeConfig` |
| E0.2 | Fanout cap + no broadcast-all | §3.1 | `node.rs`, `forwarding.rs` |
| E0.3 | BLE adv without PeerId | §7.1 | `BleBootstrapAdvertiser.kt` |
| E0.4 | Burst sync timer | §3.9 | `node.rs` |
| E0.5 | Metadata oracle test in sim | §14 | `mycena-sim` |

### Phase E1 — Shade cells (8–12 weeks)

| # | Deliverable | Mechanism |
|---|-------------|-----------|
| E1.1 | `ShadeCell` wire type + padding | §3.13, §4.1 |
| E1.2 | Stochastic hold queue | §3.2 |
| E1.3 | Crowd blending probabilities | §3.17 |
| E1.4 | Decoy sync (off default) | §3.12 |
| E1.5 | Android UX toggle + budget stub | §9 |

### Phase E2 — Social graph hiding (12–20 weeks)

| # | Deliverable | Mechanism |
|---|-------------|-----------|
| E2.1 | Rotating scope handles | §3.14 |
| E2.2 | Dead-drop bulletin | §3.8 |
| E2.3 | Trust ring store + routing | §3.11 |
| E2.4 | Ephemeral route_id | §3.16 |
| E2.5 | Cover traffic + privacy budget | §3.3, §3.15 |

### Phase E3 — Research (20+ weeks)

| # | Deliverable | Mechanism |
|---|-------------|-----------|
| E3.1 | LAN mixing rounds | §3.6 |
| E3.2 | Onion-lite multi-hop | §3.7 |
| E3.3 | Encrypted interest bloom (PIR-lite) | §3.10 |
| E3.4 | Rolling relay sub-keys | §3.4 |

---

## 14. Test methodology

### 14.1 Metadata oracle (automated)

Extend `mycena-sim` with **OracleAdversary**:

| Test | Pass condition |
|------|----------------|
| Graph inference | Given N users chatting, oracle cannot reconstruct ≥ 80% edges from wire metadata alone |
| Timing correlation | Cross-correlation of send vs observe times < 0.3 at 95% confidence |
| Volume fingerprint | Message length classifier accuracy < 55% (random baseline 50%) |
| Scope leakage | ScopeAnnounce does not contain plaintext `berlin` |
| Relay attribution | Same stable PeerId not seen across epochs in Paranoid |

### 14.2 Passive capture lab (manual)

1. Two phones BLE + WiFi Direct only (airplane + radios on).
2. Wireshark / Android BLE snoop.
3. **Before Shade:** PeerId prefix visible.
4. **After E0.3:** Only service UUID.

### 14.3 Red team week (beta gate)

| Scenario | Expected |
|----------|----------|
| Malicious relay drops 50% cells | App retries; user notified |
| Sybil flood cover | Budget throttle |
| Trust ring infiltrator | User revokes trust QR |
| State DNS block relay | Mesh-only still works |

### 14.4 Battery benchmark

- 24h scripted chat: Balanced vs Off on Pixel / mid Android.
- Accept if ≤ 15% battery delta (Balanced).

### 14.5 Legal / product review

- Dead-drop abuse scenario walkthrough with [ABUSE_RESPONSE_PLAYBOOK.md](./ABUSE_RESPONSE_PLAYBOOK.md).
- Privacy policy update for cover traffic + delayed delivery.

---

## 15. Success metrics (12-month)

| Metric | Target |
|--------|--------|
| Metadata oracle graph accuracy | < 40% |
| Delivery rate Balanced vs Off | ≥ 85% |
| BLE PeerId leakage incidents | 0 |
| User opt-in Paranoid | < 5% (expected niche) |
| Published Shade spec version | ≥ 1.0 |

---

## 16. Document maintenance

- Update when a mechanism moves E0 → shipped.
- Link new debt rows in [SECURITY_DEBT_REGISTER.md](./SECURITY_DEBT_REGISTER.md) (prefix `SD-3xx` Shade).
- Align [PRIVACY_ROADMAP.md](./PRIVACY_ROADMAP.md) Phase C with Phase E2+.

---

## 17. Summary

**Shade** is Mycena’s experimental answer to: *store-and-forward mesh is a metadata hose.* We do not fix that by copying Signal—we fix it by **delaying, padding, mixing, rotating, and budgeting** metadata on **local radios first**, while keeping **signed, E2E inner payloads** intact.

**First code to write:** E0.2 (fanout cap), E0.3 (BLE), E0.4 (burst sync), E0.5 (sim oracle)—no new cryptography required.

**Hardest open problem:** Encrypted interest sync without killing battery—worth a focused prototype in E3.
