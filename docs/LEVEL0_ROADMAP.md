# Level 0 Mesh — Roadmap

**Status:** Active  
**Milestone reached:** Mac desktop and Android discover each other, connect via QR, and exchange **E2E encrypted** chat messages.

This document tracks phased work from the current milestone toward the Level 0 vision:

> When the internet fails, devices within reach automatically find each other and form a safe, fast mesh. Identities rotate to resist tracking. Emergency help works without prior QR pairing, with abuse controls.

See also: [CONNECT.md](./CONNECT.md), [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md), [PRIVACY_ROADMAP.md](./PRIVACY_ROADMAP.md), [ARCHITECTURE_RISKS.md](./ARCHITECTURE_RISKS.md).

---

## Recommendations (summary)

| Topic | Recommendation |
|-------|----------------|
| **E2E chat** | Keep X25519 + ChaCha20-Poly1305; refuse send without peer key (SD-081). |
| **QR invites** | Default **v1** (peer ID only); key arrives via authenticated `PeerInfo`. v2 optional later with signed binding. |
| **Inbound chat** | **Pending** contacts until user accepts — do not auto-accept unknown senders. |
| **Enc key binding** | Reject `PeerInfo` if it conflicts with a key seeded from invite (TOFU). |
| **Daily ID rotation** | Not shipped — track as Phase C (SD-190). |
| **Emergency help** | MeshAid + future SOS scope in main app (Phase E). |
| **Festival scale** | Not supported today — Phase D (gossip tuning, relay pool, crowd mode). |

---

## Phase A — Lock in the milestone

**Goal:** Make what works today reliable, tested, and documented.

| ID | Task | Status |
|----|------|--------|
| A1 | Encryption acceptance tests (crypto roundtrip + dispatch; refuse send without key) | Done |
| A2 | Manual test matrix (LAN, relay, QR v1/v2, reconnect, offline) | Done (doc) — manual sign-off pending |
| A3 | `PeerInfo` ↔ invite enc-key binding (reject mismatch) | Done |
| A4 | Default QR to **invite v1** (Android + desktop); v2 parse retained | Done |
| A5 | Inbound chat → **Pending** until accept (align with DISCOVERY.md) | Done |

**Exit criteria:** `cargo test --workspace` green; manual matrix passed on Mac + Pixel; pending contacts visible in Chat UI.

**Manual tests:** [mvp-smoke-test.md § Phase A](./mvp-smoke-test.md#phase-a--milestone-validation-mac--android)

---

## Phase B — Level 0 LAN mesh

**Goal:** Internet down → nearby devices connect without QR.

| ID | Task | Status |
|----|------|--------|
| B1 | mDNS reliability (redundant dial, guest-network docs) | Done |
| B2 | Persist learned X25519 keys to sled (survive restart) | Done |
| B3 | BLE → mesh handoff hint (stretch) | Deferred |
| B4 | Connectivity mode UX (mesh-only / internet assist) | Done |

**Exit criteria:** Two devices on same Wi‑Fi connect with no QR within 30s; chat works after app restart without re-scan.

---

## Phase C — Privacy & trust

**Goal:** Rotating identities and safer unsolicited messaging.

| ID | Task | Status |
|----|------|--------|
| C1 | SD-190 spike — rotatable transport sub-identity | Planned |
| C2 | Signed invite `(peer_id, enc_pubkey, epoch)` | Planned |
| C3 | Emergency scope + rate limits in main app | Planned |
| C4 | Block list + reputation in UI | Planned |

**Exit criteria:** New public ID daily without losing contact book mapping; malformed QR rejected.

---

## Phase D — Scale & resilience

**Goal:** Festival-scale behavior and multi-relay.

| ID | Task | Status |
|----|------|--------|
| D1 | Gossipsub mesh params for dense clusters | Planned |
| D2 | Regional relay pool + client rotation (SD-071) | Planned |
| D3 | Crowd mode — cap mDNS, prioritize bulletin over full mesh | Planned |
| D4 | Rendezvous selective dial (bucketed, no storms) | Planned |

**Exit criteria:** Sim soak at 500+ nodes without relay collapse; relay failover drill passes.

---

## Phase E — Emergency product path

**Goal:** Help without QR, safe from spam.

| ID | Task | Status |
|----|------|--------|
| E1 | First-class SOS in main app (geohash + short message) | Planned |
| E2 | Opt-in “listen for nearby SOS” (default off) | Planned |
| E3 | Per-scope rate limits + anti-spam for SOS | Planned |
| E4 | MeshAid remains for coordination threads | Planned |

**Exit criteria:** Field drill: SOS visible to opt-in peers within range; spam soak test passes.

---

## Gap tracker (vision vs today)

| Requirement | Today | Target phase |
|-------------|-------|--------------|
| Auto-discovery without QR (LAN) | Partial (mDNS) | B |
| Auto-discovery without QR (remote) | QR required; rendezvous no auto-dial | D |
| Daily ID rotation | Stable PeerId | C |
| Safe mesh participation | Partial (E2E send; auto-accept was weak) | A, C |
| Fast connect/disconnect | Moderate | B, D |
| Festival 100k | Not supported | D |
| Emergency without QR | MeshAid mini-app only | E |

---

## Working through phases

1. Complete **Phase A** exit criteria before starting B.
2. Update the **Status** column in this file when tasks complete.
3. Run manual tests from [mvp-smoke-test.md](./mvp-smoke-test.md) at each phase boundary.
4. Link PRs/commits in commit messages (`Phase A3: …`).
