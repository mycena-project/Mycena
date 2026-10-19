# Mycelium MVP Smoke Test

This document gives a copy/paste validation flow for:
- two local nodes (CLI)
- chat
- bulletin board
- mesh mail
- REST API checks
- **Phase A:** Mac + Android milestone validation (encrypted chat, QR, LAN)

**Level 0 phased plan:** [LEVEL0_ROADMAP.md](./LEVEL0_ROADMAP.md)

## Prerequisites

- Build and tests are green:

```bash
cargo test --workspace
cargo clippy --workspace -- -D warnings
```

- Optional (for prettier JSON):

```bash
brew install jq
```

## 1) Start Two Nodes

Open two terminals.

### Terminal A

```bash
cargo run -p mycelium-cli -- --db /tmp/node-a --name Alice --api-port 7760
```

### Terminal B

```bash
cargo run -p mycelium-cli -- --db /tmp/node-b --name Bob --api-port 7761
```

Both terminals print a line like:

```text
local peer id: <PEER_ID>
```

Copy both peer IDs.

## 2) Verify Peer Discovery

In both terminals:

```text
/peers
```

You should see the other peer eventually.

## 3) Mesh-Mail Test

Send a mail from A to B:

```text
/mail <PEER_ID_OF_B> Hallo | Das ist eine Test-Mail ueber Mycelium.
```

REST calls need the bearer token from node B's log (`TOKEN_B`):

```bash
curl -s -H "Authorization: Bearer $TOKEN_B" http://127.0.0.1:7761/api/v1/mail/inbox | jq
```

Expected: at least one mail entry with `subject = "Hallo"`.

Mark the message as read (replace `<MAIL_ID>`):

```bash
curl -s -H "Authorization: Bearer $TOKEN_B" -X PUT http://127.0.0.1:7761/api/v1/mail/<MAIL_ID>/read | jq
```

## 4) Chat Test

From A to B:

```text
/chat <PEER_ID_OF_B> Hi Bob!
```

In B terminal:

```text
/chat:history <PEER_ID_OF_A>
```

REST check:

```bash
curl -s -H "Authorization: Bearer $TOKEN_B" http://127.0.0.1:7761/api/v1/chat/<PEER_ID_OF_A> | jq
```

Expected: latest chat contains `"Hi Bob!"`.

## 5) Bulletin Test

In A terminal:

```text
/bulletin berlin/mitte Markt | Frisches Brot am Platz
```

In B terminal:

```text
/bulletin:list berlin/mitte
```

REST check:

```bash
curl -s -H "Authorization: Bearer $TOKEN_B" http://127.0.0.1:7761/api/v1/bulletin/berlin%2Fmitte | jq
```

Expected: one bulletin with title `Markt`.

## 6) API Status and Settings

The loopback API requires a bearer token (printed at node start, or set `MYCELIUM_API_TOKEN` before launch). `GET /api/v1/health` is unauthenticated.

```bash
export TOKEN='<token from node A log>'
AUTH=(-H "Authorization: Bearer $TOKEN")
```

Check status:

```bash
curl -s "${AUTH[@]}" http://127.0.0.1:7760/api/v1/status | jq
curl -s -H "Authorization: Bearer $TOKEN_B" http://127.0.0.1:7761/api/v1/status | jq
```

Check settings:

```bash
curl -s "${AUTH[@]}" http://127.0.0.1:7760/api/v1/settings | jq
```

Update display name:

```bash
curl -s "${AUTH[@]}" -X PUT http://127.0.0.1:7760/api/v1/settings/name \
  -H 'content-type: application/json' \
  -d '{"display_name":"AliceMesh"}' | jq
```

Update energy state:

```bash
curl -s "${AUTH[@]}" -X PUT http://127.0.0.1:7760/api/v1/settings/energy \
  -H 'content-type: application/json' \
  -d '{"state":"intermittent"}' | jq
```

## 7) Expected MVP Outcome

- Node A and Node B discover each other.
- A can send chat/mail to B.
- Bulletin posts are visible from B.
- REST endpoints return valid JSON.
- Status endpoint returns peer ID, display name, and node metrics.

## 8) Troubleshooting

- If no peers appear:
  - wait 10-30s for mDNS discovery
  - ensure both terminals run on same host/LAN
- If REST fails:
  - verify API port in startup command
  - check `GET /api/v1/health` then `GET /api/v1/status` with `Authorization: Bearer`
- If message not delivered:
  - verify peer ID copied correctly
  - run `/peers` before `/chat` or `/mail`

---

## Phase A — Milestone validation (Mac + Android)

Run after Phase A code changes land. Check off each case on a **Mac desktop build** and **Android debug APK**.

Android freeze DoD (HV-01–20, Pixel + Samsung slots): [HARDWARE_TEST_RUNBOOK.md](./HARDWARE_TEST_RUNBOOK.md). This Phase A checklist remains the Level 0 A2 script.

### Prerequisites

```bash
cd Mycelium
cargo test --workspace
make android-install   # or android-deploy
# Desktop: cd crates/mycelium-desktop/ui && npm ci && npm run build
# then run desktop app from IDE or `cargo run -p mycelium-desktop`
```

Relay health (optional):

```bash
curl -s 'http://relay.example.invalid:8080/health' | python3 -m json.tool
```

Expect `"ok": true` from `/health`. Connection counts are on `/status` and require the operator token.

### A1 — Encryption indicators

| Step | Expected |
|------|----------|
| Pair devices via QR (Connect tab) | Contact appears |
| Open Chat with paired contact | Badge shows **End-to-end encrypted** (not “key not yet exchanged”) |
| Send message both directions | Delivered; Mac log: `sent encrypted chat` / `encrypted direct delivered` |
| Try send before key ready (fresh install, no scan) | Error / “Schlüsselaustausch ausstehend” — **not** plaintext send |

### A2 — Connection matrix

| # | Scenario | Setup | Pass criteria |
|---|----------|-------|---------------|
| 1 | **LAN only** | Same Wi‑Fi; relay reachable or not | Connect tab shows peer; chat works without re-scan |
| 2 | **Relay only** | Different networks (or disable Wi‑Fi on one device) | QR scan connects via circuit; chat works |
| 3 | **QR v3** | Default QR shows **v3 signed** invite (`mycelium://invite/v3#…`); E2E badge after connect |
| 4 | **QR v2 (legacy)** | Paste v2 invite manually if testing legacy codes | Still parses; enc key seeded from QR |
| 5 | **Reconnect** | Force-quit both apps; reopen | Chat works without re-scan (may need brief wait for dial) |
| 6 | **Airplane mode** | Wi‑Fi on, cellular off (or full offline LAN) | mDNS path still finds peer on same LAN |

### A3 — Enc key binding

Manual spot-check (automated in `cargo test`):

- Scan valid QR from trusted peer → chat encrypts.
- If testing with forged v2 (wrong enc key in invite): connection may succeed but decrypt should fail or key mismatch logged — do not treat as trusted.

### A4 — QR default v3 (signed)

| Platform | Check |
|----------|-------|
| Android Connect → Show QR | Payload starts with `mycelium://invite/v3#` |
| Desktop Connect → QR | Same v3 signed prefix |
| Scan still accepts v1/v2 pasted invites | Manual paste of legacy strings still connects |

### A5 — Pending inbound contacts

| Step | Expected |
|------|----------|
| Device B sends first chat to A **without** A having scanned B | A sees **pending contact request** in Chat tab |
| A taps Accept | Contact moves to accepted; messages visible |
| A taps Reject | Contact removed; no further notifications |

**Note:** Contacts you add by scanning **their** QR remain **accepted** immediately (you initiated trust).

### Phase A sign-off

- [ ] All six connection matrix cases pass
- [ ] E2E badge on paired chat
- [ ] Pending flow works for unsolicited inbound chat
- [ ] `cargo test --workspace` and CI green

---

## Phase B — LAN mesh (no QR on same Wi‑Fi)

| # | Scenario | Pass criteria |
|---|----------|---------------|
| 1 | Same Wi‑Fi, both apps fresh | Connect within ~30s without QR; chat works |
| 2 | Restart both apps | Chat still encrypts without re-scan (keys persisted) |
| 3 | Relay unreachable | Status shows **Local mesh**; LAN peers still connect |
| 4 | Guest/isolated Wi‑Fi | Document failure; QR still works |

---

## Phase C — Privacy & trust

| # | Scenario | Pass criteria |
|---|----------|---------------|
| 1 | Desktop + Android QR | Shows **v3 signed** invite (`mycelium://invite/v3#…`); tampered paste rejected |
| 2 | Block contact | Block in Chat (desktop + Android) → inbound messages ignored |
| 3 | Reputation | Chat shows strike count from mesh ingest (desktop + Android) |
| 4 | SOS (opt-in) | **SOS tab** → listen off by default; post SOS with geohash; rate limit under spam |
| 5 | Identity rotation | After 24h (or `MYCELIUM_TRANSPORT_ROTATION_SECS=0` test), restart → new PeerId, contact book keeps account mapping |

---

## Phase D — Scale & resilience

| # | Scenario | Pass criteria |
|---|----------|---------------|
| 1 | Dense gossip | `MYCELIUM_MESH_PROFILE=dense` or crowd mode → node logs dense mesh; `cargo test -p mycelium-core mesh_profile` green |
| 2 | Relay pool | Multiple TCP bootstrap relays in `bootstrap.txt` → client dials each; circuit uses hash-rotated relay |
| 3 | Relay failover | Kill primary relay TCP session → logs `relay failover`; circuit still works via next pool entry |
| 4 | Crowd mode | Settings → crowd mode on, **restart app** → mDNS dial cap (3/min), max ~20 direct peers, bulletin scopes subscribed first (desktop + Android) |
| 5 | Rendezvous dial | Both opt in → logs `rendezvous bucket X/Y: dialing N` (≤3 peers/tick), not full-list storm |
| 6 | Sim soak | `cargo test -p mycelium-sim festival_relay_soak` — 500 virtual nodes, no single relay >45% load |

Automated: `cargo test --workspace` including `mycelium-core::relay_pool` and `mycelium-sim::scenarios`.

---

## Phase E — Emergency product path

| # | Scenario | Pass criteria |
|---|----------|---------------|
| 1 | SOS tab | Desktop **SOS** nav + Android **SOS** tab — post geohash + message |
| 2 | Listen opt-in | Default off; enable listen → subscribe to `mycelium/sos/v1`; disable → unsubscribe |
| 3 | Receive SOS | Two devices: A posts, B listening → B sees post + notification (Android) / `sos-updated` (desktop) |
| 4 | Rate limits | Spam >6/min from one peer or >24/min same geohash → dropped at ingest (`ingest_guard` tests) |
| 5 | MeshAid | SOS screen → **Open MeshAid** launches `network.mycelium.meshaid` mini-app |
| 6 | Not 911 | UI disclaimer visible on SOS screens |

Automated: `cargo test -p mycelium-node ingest_guard::tests::sos_*` and `cargo test -p mycelium-core sos`.
