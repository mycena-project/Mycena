#![no_main]

use libfuzzer_sys::fuzz_target;
use mycelium_coin::decode_peer_coin_payload;

/// MeshCoin gossip payloads from untrusted peers.
fuzz_target!(|data: &[u8]| {
    let _ = decode_peer_coin_payload(data);
});
