#![no_main]

use libfuzzer_sys::fuzz_target;
use mycena_core::ble_multiaddr::parse_ble_multiaddr;

/// BLE dial multiaddrs from peer advertisements / invites.
fuzz_target!(|data: &[u8]| {
    if let Ok(addr) = std::str::from_utf8(data) {
        let _ = parse_ble_multiaddr(addr);
    }
});
