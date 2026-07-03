// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! L1 facade — stable boundary over [`mycelium_node`] for app-layer crates.
//!
//! App crates should depend on this crate instead of `mycelium-node` directly.
//! Transport internals (`storage`, `ratchet_store`, mesh behaviour) remain private
//! inside `mycelium-node`.

pub use mycelium_l0_facade::consistency;
pub use mycelium_node::connectivity::{
    kad_action_for_mode, ConnectivityMonitor, KadConnectivityAction,
};
pub use mycelium_node::secrets::{
    self, bootstrap_identities, encode_signed_invite_v3, load_or_create_enc_keypair,
    IdentityBootstrap, StorageKey,
};
pub use mycelium_node::ConnectivityMode;
pub use mycelium_node::{
    load_or_create_keypair, load_or_create_keypair_from_env, DirectMessage, NodeCommand,
    NodeConfig, NodeHandle, NodeMetrics, NodeRunner, WireMessage,
};
