#![no_main]

use libfuzzer_sys::fuzz_target;
use mycelium_core::invite::parse_invite;

/// QR / deep-link invite strings from cameras and clipboard paste.
fuzz_target!(|data: &[u8]| {
    if let Ok(raw) = std::str::from_utf8(data) {
        let _ = parse_invite(raw);
    }
});
