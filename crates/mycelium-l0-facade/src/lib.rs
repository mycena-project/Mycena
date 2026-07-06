// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! L0 facade — stable boundary over [`mycelium_core`] for app-layer crates.
//!
//! App and mini-app crates should depend on this crate instead of `mycelium-core`
//! directly. Node and transport internals may still use `mycelium-core` for ratchet
//! and mesh protocol details.

pub mod crypto;
pub mod secrets;

pub use mycelium_core::account_identity;
pub use mycelium_core::at_rest::{clear_keyring_master, parse_storage_key_hex};
pub use mycelium_core::bearer_transport;
pub use mycelium_core::ble_mesh;
pub use mycelium_core::bootstrap;
pub use mycelium_core::consistency;
pub use mycelium_core::contact_hello;
pub use mycelium_core::crdt;
pub use mycelium_core::crisis_mode;
pub use mycelium_core::data;
pub use mycelium_core::e2e_direct_wrap;
pub use mycelium_core::energy;
pub use mycelium_core::identity_rotation;
pub use mycelium_core::information_trust;
pub use mycelium_core::invite;
pub use mycelium_core::lock_or_recover;
pub use mycelium_core::privacy_shaping;
pub use mycelium_core::relay_pool;
pub use mycelium_core::relay_registry;
pub use mycelium_core::relay_status;
pub use mycelium_core::resource_map;
pub use mycelium_core::sos;
pub use mycelium_core::volunteer;
pub use mycelium_core::webrtc_signaling;

pub use crypto::{
    decrypt_group, encrypt_for, encrypt_group, parse_x25519_public_hex, public_key_from_bytes,
    EncKey,
};
