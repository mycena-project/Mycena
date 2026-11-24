# Fuzzing Mycena wire parsers

Mycena accepts untrusted bytes from BLE, LAN (libp2p gossipsub / direct), and relay hops. Fuzz targets exercise **only public deserialization entry points** that a remote peer can reach — they must never panic on malformed input.

Targets live under [`fuzz/fuzz_targets/`](../fuzz/fuzz_targets/) and are built with [cargo-fuzz](https://github.com/rust-fuzz/cargo-fuzz) (`libfuzzer-sys`, Rust **nightly**).

## Current targets

| Target | Crate / module | Peer-visible entry point |
|--------|----------------|--------------------------|
| `fuzz_envelope` | `mycena-core::data::Envelope` | Bincode envelope inside `DirectMessage` / sync payloads |
| `fuzz_ble_frame` | `mycena-node::decode_peer_ble_wire_frame` | MYBL BLE side-channel frames (`ble_mesh`) |
| `fuzz_ble_stream` | `mycena-node::decode_peer_ble_stream_frame` | MYLP BLE stream frames (`ble_stream`) |
| `fuzz_ble_multiaddr` | `mycena-core::ble_multiaddr::parse_ble_multiaddr` | BLE dial multiaddrs from ads / invites |
| `fuzz_gossipsub_wire` | `mycena-node::decode_peer_wire_message` | Gossipsub & direct bincode `WireMessage` |
| `fuzz_sync_bloom` | `mycena-core::sync` + sync wire variants | `BloomFilter::from_bytes`, `SyncDigest`, `WireMessage::Sync*` |
| `fuzz_invite` | `mycena-core::invite::parse_invite` | QR / deep-link invite strings |
| `fuzz_coin_payload` | `mycena-coin::decode_peer_coin_payload` | MeshCoin gossip / voucher payloads |
| `fuzz_custody_envelope` | `mycena-core::transport::DirectMessage` | Custody / DTN direct messages (bincode) |
| `fuzz_miniapp_bundle` | `mycena-miniapp::bundle`, `bundle_signature` | Store `.mxa` ZIP bundles & signature sidecars |

## Prerequisites

```bash
rustup toolchain install nightly
cargo install cargo-fuzz --locked
```

## Run locally

```bash
cd fuzz
cargo fuzz run fuzz_envelope          # open-ended
cargo fuzz run fuzz_envelope -- -max_total_time=60   # 60s cap
```

PR-equivalent smoke (all targets, 5 min each):

```bash
./scripts/fuzz-smoke.sh
FUZZ_SECONDS=30 ./scripts/fuzz-smoke.sh   # shorter local run
```

## CI

Repository variable **`MYCENA_CI_TIER`**: `budget` (default, private pilot) or `full` (public pilot). See [CI_PUBLIC_TRANSITION.md](./CI_PUBLIC_TRANSITION.md).

| Workflow | Trigger | Duration per target |
|----------|---------|---------------------|
| [`.github/workflows/fuzz-pr.yml`](../.github/workflows/fuzz-pr.yml) | PR + push to `main` | **90s** — `fuzz_envelope`, `fuzz_ble_frame`, `fuzz_coin_payload`, `fuzz_custody_envelope` only |
| [`.github/workflows/fuzz-nightly.yml`](../.github/workflows/fuzz-nightly.yml) | Daily 02:00 UTC when `MYCENA_CI_TIER=full`; always via **manual** `workflow_dispatch` | 4 hours (14400s) — all targets |

Prepared OSS-Fuzz submission (not filed until repo is public): [`integrations/oss-fuzz/`](../integrations/oss-fuzz/).

Nightly uploads crash artifacts when a target fails.

## Adding a target for a new wire format

1. **Expose a panic-free public decoder** in the owning crate (e.g. `pub fn decode_peer_foo(payload: &[u8]) -> Result<Foo, FooError>`). Fuzz the same function production uses — do not duplicate parsing logic in the fuzz crate.

2. **Create** `fuzz/fuzz_targets/fuzz_<name>.rs`:

   ```rust
   #![no_main]
   use libfuzzer_sys::fuzz_target;

   fuzz_target!(|data: &[u8]| {
       let _ = my_crate::decode_peer_foo(data);
   });
   ```

   For UTF-8 text formats (invites, multiaddrs):

   ```rust
   if let Ok(s) = std::str::from_utf8(data) {
       let _ = my_crate::parse_foo(s);
   }
   ```

3. **Register** the binary in [`fuzz/Cargo.toml`](../fuzz/Cargo.toml):

   ```toml
   [[bin]]
   name = "fuzz_<name>"
   path = "fuzz_targets/fuzz_<name>.rs"
   test = false
   doc = false
   bench = false
   ```

4. **Add dependencies** on the crate under test in `fuzz/Cargo.toml` if not already present.

5. **Update this table** and both CI workflow matrices (`fuzz-pr.yml`, `fuzz-nightly.yml`).

6. **Add a unit test** beside the decoder proving malformed input returns `Err` (not panic) — fuzzing complements but does not replace that guard.

7. **Optional seed corpus**: after finding interesting inputs, add files under `fuzz/corpus/fuzz_<name>/` and commit them to speed up CI convergence.

## Regressions

When libfuzzer finds a crash:

```bash
cd fuzz
cargo fuzz run fuzz_<name> fuzz/artifacts/fuzz_<name>/crash-<hash>
```

Fix the decoder, then minimize:

```bash
cargo fuzz cmin fuzz_<name>
```

Commit minimized corpus entries if they help prevent recurrence.

## Scope rules

- Fuzz **decoding / parsing only** — no signing, no network I/O, no filesystem writes beyond in-memory ZIP parsing.
- Cap expensive paths (e.g. bundle size) inside the target when needed to keep CI bounded.
- Do **not** fuzz private helpers; if the only path is private, add a thin `pub fn decode_peer_*` wrapper used by the node and fuzz that.
