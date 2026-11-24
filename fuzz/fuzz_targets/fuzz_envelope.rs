#![no_main]

use libfuzzer_sys::fuzz_target;
use mycena_core::data::Envelope;

/// Bincode envelope bytes as carried inside [`DirectMessage`] / bloom sync payloads.
fuzz_target!(|data: &[u8]| {
    let _ = bincode::deserialize::<Envelope>(data);
});
