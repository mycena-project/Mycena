#![no_main]

use libfuzzer_sys::fuzz_target;
use mycelium_core::sync::{BloomFilter, SyncDigest};
use mycelium_core::transport::WireMessage;

/// Bloom sync bytes and related wire variants (`SyncBloom`, `SyncIds`, …).
fuzz_target!(|data: &[u8]| {
    let _ = BloomFilter::from_bytes(data);
    let _ = bincode::deserialize::<SyncDigest>(data);
    if let Ok(wire) = bincode::deserialize::<WireMessage>(data) {
        match wire {
            WireMessage::SyncBloom { bloom, .. } => {
                let _ = BloomFilter::from_bytes(&bloom);
            }
            WireMessage::SyncIds { ids } => {
                for id in ids {
                    let _ = id.len();
                }
            }
            WireMessage::SyncRequest { ids } => {
                for id in ids {
                    let _ = id.len();
                }
            }
            WireMessage::SyncData { messages } => {
                for msg in messages {
                    let _ = msg.body.len();
                }
            }
            _ => {}
        }
    }
});
