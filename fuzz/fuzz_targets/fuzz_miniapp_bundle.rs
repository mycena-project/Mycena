#![no_main]

use libfuzzer_sys::fuzz_target;
use mycena_miniapp::bundle::MiniAppBundle;
use mycena_miniapp::bundle_signature::parse_bundle_signature;

const MAX_BUNDLE_BYTES: usize = 4 * 1024 * 1024;

/// Mini-app ZIP bundles and detached signature sidecars from the store mesh.
fuzz_target!(|data: &[u8]| {
    if data.len() <= MAX_BUNDLE_BYTES {
        let _ = MiniAppBundle::load_from_bytes(data);
    }
    let _ = parse_bundle_signature(data);
});
