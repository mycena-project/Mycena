// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! Shared libp2p protocol identifiers — `mycelium-node` and `mycelium-relay` must stay aligned.

/// libp2p identify agent / protocol string (visible to all peers).
pub const IDENTIFY_PROTOCOL: &str = "mycelium/0.1";

/// Kademlia routing protocol.
pub const KAD_PROTOCOL: &str = "/mycelium/kad/1.0.0";

/// CBOR request-response direct wire path on mesh nodes.
pub const DIRECT_REQUEST_PROTOCOL: &str = "/mycelium/direct/1";

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn protocol_ids_are_non_empty() {
        assert!(!IDENTIFY_PROTOCOL.is_empty());
        assert!(KAD_PROTOCOL.starts_with('/'));
        assert!(DIRECT_REQUEST_PROTOCOL.starts_with('/'));
    }
}
