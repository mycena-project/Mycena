#![no_main]

use libfuzzer_sys::fuzz_target;
use mycena_node::decode_peer_ble_wire_frame;

/// MYBL BLE side-channel frames (`ble_mesh::decode_frame` via node entry point).
fuzz_target!(|data: &[u8]| {
    let _ = decode_peer_ble_wire_frame(data);
});
