# Relay operator guide

This document explains how an **independent third party** (not the Mycelium core team) can run a public libp2p relay for the Mycelium mesh and get listed in the federated bootstrap registry **without** letting a malicious operator Sybil-flood the relay list.

## Anti-censorship bootstrap (not redundancy)

Mycelium ships **seven equal default bootstrap relays** in every client (`STANDARD_BOOTSTRAP_RELAYS` in `crates/mycelium-core/src/standard_bootstrap.rs`). This is a deliberate **anti-censorship** measure:

- There is **no primary relay** and no “fallback behind Fly.io”. All slots are listed with the same weight.
- Each slot includes **both** `/ip4/…` and `/dns4/…` multiaddrs (plus QUIC). Blocking DNS alone is insufficient; censors must block every IP **and** every hostname embedded in the binary.
- Slots use **different hosting providers, regions, and legal jurisdictions** — no shared operator company across the default set.
- Independent operators **claim a reserved slot** by deploying to the slot’s DNS + IPv4 and opening a signed registry update PR.

This is **not** classic HA redundancy (N+1 for uptime). It is **shutdown resistance**: no single DNS takedown or cloud-provider pressure should isolate users who have not configured custom bootstrap peers.

Related code:

- Standard slots: `crates/mycelium-core/src/standard_bootstrap.rs`
- Registry format: `crates/mycelium-core/src/relay_registry.rs`
- Client pool + failover: `crates/mycelium-core/src/relay_pool.rs`, `crates/mycelium-node/src/transport.rs`
- Reference relay binary: `crates/mycelium-relay`

### Default bootstrap slots (embedded)

| Slot | Region | Provider (target) | DNS | IPv4 (slot) | Live |
|------|--------|-------------------|-----|-------------|------|
| `sin-fly` | AP-Southeast | Fly.io (SG) | `mycelium-relay.fly.dev` | `149.248.194.198` | yes |
| `eu-fra` | EU-Central | Hetzner (DE) | `bootstrap-eu-fra.mycelium.network` | `243.185.187.10` | reserved |
| `eu-ams` | EU-West | OVH (NL) | `bootstrap-eu-ams.mycelium.network` | `243.186.187.10` | reserved |
| `na-east` | NA-East | Vultr (US-East) | `bootstrap-na-east.mycelium.network` | `243.187.187.10` | reserved |
| `na-west` | NA-West | Akamai/Linode (US-West) | `bootstrap-na-west.mycelium.network` | `243.188.187.10` | reserved |
| `ap-syd` | AP-Southeast | DigitalOcean (AU) | `bootstrap-ap-syd.mycelium.network` | `243.189.187.10` | reserved |
| `sa-sao` | SA-East | Hostinger (BR) | `bootstrap-sa-sao.mycelium.network` | `243.190.187.10` | reserved |

Reserved slots use non-routed `243.x` placeholder IPv4 until an operator deploys and replaces the address in a release. PeerIds for reserved slots are **deterministic** from slot seeds — operators must deploy with the listed identity.

---

## 1. What a bootstrap relay does

A bootstrap relay is **public infrastructure**, not a user device:

- Accepts direct TCP/QUIC connections from nodes behind NAT.
- Grants libp2p **circuit reservations** so peers can reach each other via `/p2p-circuit`.
- Optionally participates in Kademlia as a stable rendezvous point.

Relays see **transport metadata** (IP addresses, connection timing). They do **not** decrypt E2E chat payloads.

---

## 2. Minimum requirements

| Requirement | Rationale |
|-------------|-----------|
| **Dedicated public IPv4** (or stable DNS → IPv4) | Shared reverse proxies break the Noise handshake; raw libp2p TCP must terminate on your host. |
| **TCP 4001** (+ optional **UDP 4001 QUIC**) open inbound | Default listen addrs in `mycelium-relay`. |
| **≥99% monthly uptime** target | Clients track consecutive dial failures and rotate away after 3 failures. |
| **Independent operator account** | Hosting, DNS, and billing must not share credentials with other listed relays (Sybil policy). |
| **Unique /24 IPv4 prefix** per dialable relay | Prevents one VPS provider block from dominating the pool. |
| **Ed25519 operator key** (offline-capable) | Signs your registration; distinct from the libp2p relay peer key. |
| **Curator endorsement** | At least one signature from a known curator (see §5). |

Recommended: 1 vCPU, 512 MiB RAM, 20 Mbit/s. Public health is `GET /health`. Full telemetry is `GET /status` with `Authorization: Bearer` (`MYCELIUM_RELAY_STATUS_TOKEN`, ≥16 chars). Without a token, `/status` returns 401.

---

## 3. Run the relay

Build and start the reference server:

```bash
cargo build -p mycelium-relay --release
export MYCELIUM_RELAY_STATUS_TOKEN='replace-with-a-long-random-token'
./target/release/mycelium-relay \
  --listen /ip4/0.0.0.0/tcp/4001 \
  --listen-quic /ip4/0.0.0.0/udp/4001/quic-v1 \
  --keypair-path /var/lib/mycelium-relay/identity \
  --status-port 8080 \
  --status-token "$MYCELIUM_RELAY_STATUS_TOKEN"
```

Note the **PeerId** printed at startup. Your bootstrap multiaddr looks like:

```text
/ip4/<YOUR_PUBLIC_IP>/tcp/4001/p2p/<RELAY_PEER_ID>
```

Verify locally:

```bash
curl -s http://127.0.0.1:8080/health | jq .
# {"ok":true,"version":"…"} — Fly/load-balancer health checks should use this path.

curl -s -H "Authorization: Bearer $MYCELIUM_RELAY_STATUS_TOKEN" \
  http://127.0.0.1:8080/status | jq .
# Includes "wire_protocol": "mycelium/0.1" — must match mesh nodes (`mycelium_core::protocol::IDENTIFY_PROTOCOL`).
```

---

## 4. Registration format (`mycelium-relay-registry/v2`)

Each relay is a **signed JSON entry** with **public accountability** and **verifiable operating history** — anonymous entries are rejected.

### Independence requirements (anti-Sybil)

| Rule | Default | Purpose |
|------|---------|---------|
| `min_operating_duration_ms` | 14 days | Relay must be publicly observable before standard-list inclusion |
| `batch_registration_window_ms` | 7 days | Blocks one operator registering multiple relays in a burst |
| `max_registrations_per_operator_in_window` | 1 | No batch Sybil from a single source |
| `min_public_history_snapshots` | 4 | Public uptime history (not just a one-shot signature) |
| `min_history_reachable_ratio` | 0.75 | Majority of history snapshots must be reachable |
| `operator_label` + `operator_contact` | required | Public identity — URL or email, no anonymous slots |
| `asn` | required | BGP ASN for client-side infrastructure diversity |

Client-side [`relay_diversity`](../../crates/mycelium-core/src/relay_diversity.rs) enforces parallel dial batches that never use **only** one IPv4 /24 or ASN when alternatives exist, and reserves quota for minority operators — so a single actor listing many relays still leaves minimum reachability to independent infrastructure.

### JSON schema

```json
{
  "format": "mycelium-relay-registry/v2",
  "issued_at_ms": 1700000000000,
  "policy": {
    "min_curator_endorsements": 1,
    "max_relays_per_operator": 2,
    "max_relays_per_ipv4_prefix": 1
  },
  "registrations": [
    {
      "peer_id": "12D3KooW…",
      "multiaddrs": ["/ip4/203.0.113.10/tcp/4001/p2p/12D3KooW…"],
      "operator_pubkey_hex": "<libp2p protobuf pubkey hex>",
      "operator_label": "your-org-slug",
      "operator_contact": "ops@your-org.example",
      "asn": 64500,
      "first_observed_at_ms": 1699000000000,
      "region": "eu-central",
      "registered_at_ms": 1700000000000,
      "uptime_history": [
        { "observed_at_ms": 1700003600000, "uptime_secs": 3600, "reachable": true }
      ],
      "operator_signature_hex": "<hex>",
      "endorsements": [
        {
          "curator_id": "community-mesh-eu",
          "curator_pubkey_hex": "<hex>",
          "signed_at_ms": 1700000000000,
          "signature_hex": "<hex>"
        }
      ],
      "catalog_only": false
    }
  ]
}
```

### Operator signature

Sign the canonical payload (see `RelayRegistration::signable_bytes`):

- Prefix: `MYCEL/RELAY/REG/v1\0`
- Fields: `peer_id`, each `multiaddr`, `operator_pubkey_hex`, `operator_label`, `region`, `registered_at_ms` (LE u64), `catalog_only` (u8)

Use the same Ed25519 key as `operator_pubkey_hex`. In Rust:

```rust
use mycelium_core::relay_registry::{build_signed_registration, SignedRegistrationParams};

let reg = build_signed_registration(SignedRegistrationParams {
    operator_key: &operator_keypair,
    curator_id: "community-mesh-eu", // curator that will co-sign
    peer_id: &relay_peer_id,
    multiaddrs: vec![multiaddr],
    operator_label: "your-org-slug",
    region: "eu-central",
    registered_at_ms,
    catalog_only: false, // catalog_only = false for production dial
})?;
```

### Uptime history

Optional but recommended: append snapshots when your monitor probes `/status`. Curators may reject entries with empty history or chronic `reachable: false`.

---

## 5. Curator endorsements (Sybil resistance)

**Self-registration alone is insufficient.** Every dialable entry needs ≥ `min_curator_endorsements` valid signatures from **known curator IDs**:

| Curator ID | Role |
|------------|------|
| `mycelium-release` | Core release train (production Fly relay) |
| `community-mesh-eu` | Independent EU community operator |
| `community-mesh-na` | Independent NA community operator |

Endorsement payload (`MYCEL/RELAY/ENDORSE/v1\0` + registration digest + peer_id + signed_at_ms).

### Why this limits Sybil attacks

A malicious operator cannot unilaterally:

1. **Mint curator keys** — client code embeds deterministic curator pubkeys; unknown curator IDs are rejected.
2. **Fill the list** — policy caps **2 relays per operator pubkey** and **1 per IPv4 /24**.
3. **Pose as many independent relays** — without endorsements from multiple curators (future: raise `min_curator_endorsements` to 2+ for production).

New community curators require a **client release** adding their ID to `FEDERATED_RELAY_CURATOR_IDS`. That is intentional: the bar for list control is social + release governance, not anonymous self-serve.

### How to get endorsed (third party)

1. Operate a stable relay for ≥2 weeks with public `/health`, operator-only `/status`, and contact info in `operator_label`.
2. Open a PR or ticket to a community curator (`community-mesh-eu` / `community-mesh-na`) with:
   - Your signed registration JSON (operator sig only, endorsements empty).
   - Proof of independent hosting (ASN, billing entity ≠ existing operators).
   - Uptime probe logs.
3. Curator verifies out-of-band, signs endorsement, and either:
   - Merges your entry into the embedded manifest via core release, **or**
   - Publishes a signed fragment you ship alongside your app build.

---

## 6. Client-side listing and failover

### Embedded + local merge

Clients load:

1. **Embedded manifest** (signed entries shipped with the app).
2. Optional merge: `<db_path>/relay_registry.json` (same format; deduped by `peer_id`).

Set `MYCELIUM_INCLUDE_CATALOG_RELAYS=1` to dial `catalog_only` / TEST-NET template entries (203.0.113.x).

### Selection and rotation

- Each node picks a relay via stable hash of its PeerId (load spread).
- On dial failure or disconnect, the client marks failures and **rotates** to the next healthy relay.
- After **3 consecutive failures**, a relay is skipped until it succeeds again.

Override order for bootstrap peers remains: `MYCELIUM_BOOTSTRAP_PEERS` → `bootstrap.txt` → federated registry (embedded standard slots) → [`standard_bootstrap_multiaddrs()`].

---

## 7. Staging without a release

Use `"catalog_only": true` for TEST-NET documentation entries. They appear in the manifest for audit but are **not dialed** unless `MYCELIUM_INCLUDE_CATALOG_RELAYS=1`.

For private testing, place a verified manifest at:

```text
~/.mycelium-node/relay_registry.json
```

---

## 8. Checklist before asking for production listing

- [ ] Dedicated IPv4, ports open, `/health` reachable; `/status` token-gated
- [ ] Operator key backed up offline
- [ ] Registration signed, multiaddr `/p2p/` matches relay PeerId
- [ ] `operator_label` is a unique, accountable slug
- [ ] Uptime history populated
- [ ] Curator endorsement obtained
- [ ] Hosting is independent of other listed relays (account + /24)
- [ ] Privacy policy / abuse contact published (AR-10 operator liability)

---

## 9. Logging, privacy, and audit (SD-003, SD-210)

**Policy:** Relay operators must **not** retain client IP addresses or per-circuit logs beyond what the hosting platform requires for abuse mitigation (typically ≤72 h if unavoidable).

| Requirement | Implementation |
|-------------|----------------|
| Public `/health` | JSON only: `{"ok": true, "version": "…"}` — no peer counts or PeerId |
| Full telemetry | Restrict `/status` to operator monitoring (firewall / VPN), not public internet |
| Access logs | Disable Fly.io / nginx / CDN access logs where possible, or aggregate without IP retention |
| Legal requests | Document jurisdiction and contact in operator privacy notice |

**Last engineering audit (reference deploy):** July 2026 — verify on your host after each deploy.

Checklist:

- [ ] `/health` returns minimal JSON (no `peer_id`, no connection counts)
- [ ] `/status` not exposed on untrusted interfaces
- [ ] Platform access logging reviewed and minimized
- [ ] Abuse contact published (email or issue tracker)

See also [PRIVACY_ROADMAP.md](./PRIVACY_ROADMAP.md) Phase B · [THREAT_MODEL.md](./THREAT_MODEL.md) §4.5.

---

## 10. Further reading

- `docs/ARCHITECTURE_RISKS.md` — AR-01 (concentration), AR-10 (operator liability)
- `docs/DISASTER_MESH_ROADMAP.md` — Phase D relay pool
- `crates/mycelium-relay` — reference implementation
