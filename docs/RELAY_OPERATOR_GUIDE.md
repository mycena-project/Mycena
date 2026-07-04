# Relay operator guide

This document explains how an **independent third party** (not the Mycelium core team) can run a public libp2p relay for the Mycelium mesh and get listed in the federated bootstrap registry **without** letting a malicious operator Sybil-flood the relay list.

Related code:

- Registry format: `crates/mycelium-core/src/relay_registry.rs`
- Client pool + failover: `crates/mycelium-core/src/relay_pool.rs`, `crates/mycelium-node/src/transport.rs`
- Reference relay binary: `crates/mycelium-relay`

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

Recommended: 1 vCPU, 512 MiB RAM, 20 Mbit/s, monitoring on `:8080/status` (built into `mycelium-relay`).

---

## 3. Run the relay

Build and start the reference server:

```bash
cargo build -p mycelium-relay --release
./target/release/mycelium-relay \
  --listen /ip4/0.0.0.0/tcp/4001 \
  --listen-quic /ip4/0.0.0.0/udp/4001/quic-v1 \
  --keypair-path /var/lib/mycelium-relay/identity \
  --status-port 8080
```

Note the **PeerId** printed at startup. Your bootstrap multiaddr looks like:

```text
/ip4/<YOUR_PUBLIC_IP>/tcp/4001/p2p/<RELAY_PEER_ID>
```

Verify locally:

```bash
curl -s http://127.0.0.1:8080/status | jq .
```

---

## 4. Registration format (`mycelium-relay-registry/v1`)

Each relay is a **signed JSON entry** in a manifest:

```json
{
  "format": "mycelium-relay-registry/v1",
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
use mycelium_core::relay_registry::build_signed_registration;

let reg = build_signed_registration(
    &operator_keypair,
    "community-mesh-eu",           // curator that will co-sign
    &relay_peer_id,
    vec![multiaddr],
    "your-org-slug",
    "eu-central",
    registered_at_ms,
    false,                         // catalog_only = false for production dial
)?;
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

1. Operate a stable relay for ≥2 weeks with public `/status` and contact info in `operator_label`.
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

Override order for bootstrap peers remains: `MYCELIUM_BOOTSTRAP_PEERS` → `bootstrap.txt` → federated registry → legacy constants.

---

## 7. Staging without a release

Use `"catalog_only": true` for TEST-NET documentation entries. They appear in the manifest for audit but are **not dialed** unless `MYCELIUM_INCLUDE_CATALOG_RELAYS=1`.

For private testing, place a verified manifest at:

```text
~/.mycelium-node/relay_registry.json
```

---

## 8. Checklist before asking for production listing

- [ ] Dedicated IPv4, ports open, `/status` reachable
- [ ] Operator key backed up offline
- [ ] Registration signed, multiaddr `/p2p/` matches relay PeerId
- [ ] `operator_label` is a unique, accountable slug
- [ ] Uptime history populated
- [ ] Curator endorsement obtained
- [ ] Hosting is independent of other listed relays (account + /24)
- [ ] Privacy policy / abuse contact published (AR-10 operator liability)

---

## 9. Further reading

- `docs/ARCHITECTURE_RISKS.md` — AR-01 (concentration), AR-10 (operator liability)
- `docs/DISASTER_MESH_ROADMAP.md` — Phase D relay pool
- `crates/mycelium-relay` — reference implementation
