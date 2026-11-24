#![no_main]

use libfuzzer_sys::fuzz_target;
use mycena_core::transport::DirectMessage;

/// Custody / DTN direct messages (bincode) from untrusted mesh peers.
fuzz_target!(|data: &[u8]| {
    let _ = bincode::deserialize::<DirectMessage>(data);
});
