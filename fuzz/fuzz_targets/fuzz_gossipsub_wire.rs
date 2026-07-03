#![no_main]

use libfuzzer_sys::fuzz_target;
use mycelium_node::decode_peer_wire_message;

/// Gossipsub and direct-request bincode wire payloads from untrusted peers.
fuzz_target!(|data: &[u8]| {
    let _ = decode_peer_wire_message(data);
});
