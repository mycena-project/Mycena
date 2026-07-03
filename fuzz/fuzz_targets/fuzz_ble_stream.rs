#![no_main]

use libfuzzer_sys::fuzz_target;
use mycelium_node::decode_peer_ble_stream_frame;

/// MYLP length-prefixed BLE stream frames (`ble_stream::decode_stream_frame`).
fuzz_target!(|data: &[u8]| {
    let _ = decode_peer_ble_stream_frame(data);
});
