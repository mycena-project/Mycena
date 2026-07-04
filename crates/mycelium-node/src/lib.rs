// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
#![deny(clippy::unwrap_used, clippy::expect_used)]

mod bearer_ble;
mod bearer_mesh;
pub mod behaviour;
mod ble_transport;
pub mod connectivity;
mod custody;
mod encounter_log;
mod error;
mod forwarding;
mod ingest_guard;
mod node;
mod node_consistency;
mod peer_enc_keys;
mod peer_trust;
mod ratchet_store;
pub mod secrets;
pub mod security;
mod storage;
mod transport;
mod transport_meta;

pub use ratchet_store::RatchetSessionStore;
pub use transport::{DirectPeerCap, PeerAdmitAction};

pub use connectivity::{kad_action_for_mode, ConnectivityMonitor, KadConnectivityAction};
pub use custody::{CustodyConfig, CustodyForwardPlan, CustodyQueue, DEFAULT_MAX_CUSTODY_SPRAY};
pub use encounter_log::{EncounterLogStore, EncounterRecord, ENCOUNTER_LOG_TTL_MS};
pub use error::{
    decode_peer_bearer_frame, decode_peer_ble_stream_frame, decode_peer_ble_wire_frame,
    decode_peer_wire_message, NodeError, NodeResult,
};
pub use mycelium_core::at_rest::parse_storage_key_hex;
pub use mycelium_core::transport::{ConnectivityMode, DirectMessage, StoreStats, WireMessage};
pub use mycelium_l0_facade::EncKey;
pub use node::{NodeCommand, NodeConfig, NodeHandle, NodeMetrics, NodeRunner, SYSTEM_SCOPES};
pub use peer_trust::PeerReputationSnapshot;
pub use secrets::{
    bootstrap_identities, encode_signed_invite_v3, load_or_create_enc_keypair, IdentityBootstrap,
    StorageKey,
};
pub use security::{PeerTrust, PeerTrustStore};

/// Load or create the libp2p identity using OS keyring / encrypted storage (no explicit storage key).
pub fn load_or_create_keypair(path: &str) -> anyhow::Result<libp2p::identity::Keypair> {
    secrets::load_or_create_keypair(path, None)
}

/// Like [`load_or_create_keypair`], but reads `MYCELIUM_STORAGE_KEY` (64 hex chars) when set.
/// Required on headless servers (Fly.io) where OS keyring does not persist across restarts.
pub fn load_or_create_keypair_from_env(path: &str) -> anyhow::Result<libp2p::identity::Keypair> {
    let storage_key = match std::env::var("MYCELIUM_STORAGE_KEY") {
        Ok(hex) => Some(parse_storage_key_hex(&hex)?),
        Err(std::env::VarError::NotPresent) => None,
        Err(e) => return Err(e.into()),
    };
    secrets::load_or_create_keypair(path, storage_key)
}
