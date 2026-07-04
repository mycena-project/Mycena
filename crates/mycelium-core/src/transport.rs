// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
use crate::data::{Envelope, Priority};
use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use std::time::Duration;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DirectMessage {
    pub envelope: Envelope,
    pub body: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum WireMessage {
    Data(DirectMessage),
    SyncBloom {
        bloom: Vec<u8>,
        count: u64,
    },
    SyncIds {
        ids: Vec<String>,
    },
    SyncRequest {
        ids: Vec<String>,
    },
    SyncData {
        messages: Vec<DirectMessage>,
    },
    ScopeAnnounce {
        scopes: Vec<String>,
    },
    /// X25519-E2E für genau einen Empfänger (`to_peer` = libp2p Peer-ID String).
    EncryptedDirect {
        to_peer: String,
        /// Hex-kodierter X25519-Public-Key des Senders (64 hex chars).
        sender_enc_pubkey: String,
        /// `[ephemeral_pubkey 32]` + `[nonce 12]` + `[ciphertext]`
        encrypted_payload: Vec<u8>,
        /// libp2p-Identitäts-Signatur des unmittelbaren Senders (Relay-Hop).
        #[serde(default)]
        mesh_signature: Option<Vec<u8>>,
        #[serde(default)]
        hop_count: u8,
        #[serde(default = "default_max_hops")]
        max_hops: u8,
        /// 0 = legacy static ECDH; 1 = Double Ratchet ([`crate::crypto::ratchet`]).
        #[serde(default)]
        e2e_version: u8,
        /// Mesh forwarding priority (not part of mesh signature).
        #[serde(default = "default_wire_priority")]
        priority: Priority,
    },
    /// Symmetrisch verschlüsselte Gruppenlast; `group_id` ist öffentlicher Identifier (kein Key).
    EncryptedGroup {
        group_id: String,
        /// `[nonce 12]` + `[ciphertext]`
        encrypted_payload: Vec<u8>,
    },
    /// Bekanntgabe des X25519-Enc-Public-Keys nach Verbindungsaufbau.
    PeerInfo {
        enc_pubkey_hex: String,
        display_name: Option<String>,
        supported_scopes: Vec<String>,
        /// Stable account id (SD-190); survives transport PeerId rotation.
        #[serde(default)]
        account_id: Option<String>,
        #[serde(default)]
        transport_epoch: u64,
        /// X3DH prekey bundle for Double Ratchet (v1 E2E).
        #[serde(default)]
        ratchet_registration_id: Option<u32>,
        #[serde(default)]
        ratchet_signed_prekey_id: Option<u32>,
        #[serde(default)]
        ratchet_signed_prekey_hex: Option<String>,
        #[serde(default)]
        ratchet_signed_prekey_sig_hex: Option<String>,
        #[serde(default)]
        ratchet_one_time_prekey_id: Option<u32>,
        #[serde(default)]
        ratchet_one_time_prekey_hex: Option<String>,
    },
}

fn default_max_hops() -> u8 {
    8
}

fn default_wire_priority() -> Priority {
    Priority::Normal
}

/// Canonical bytes signed by the libp2p peer that forwards this wire frame.
pub fn encrypted_direct_signing_bytes(
    to_peer: &str,
    sender_enc_pubkey: &str,
    encrypted_payload: &[u8],
    hop_count: u8,
    max_hops: u8,
    e2e_version: u8,
) -> Vec<u8> {
    let mut bytes = Vec::new();
    if e2e_version == 0 {
        bytes.extend_from_slice(b"MYCEL/ENC_DIRECT/v1\0");
    } else {
        bytes.extend_from_slice(b"MYCEL/ENC_DIRECT/v2\0");
    }
    bytes.extend_from_slice(to_peer.as_bytes());
    bytes.push(0);
    bytes.extend_from_slice(sender_enc_pubkey.as_bytes());
    bytes.push(0);
    bytes.extend_from_slice(&(encrypted_payload.len() as u32).to_le_bytes());
    bytes.extend_from_slice(encrypted_payload);
    bytes.push(hop_count);
    bytes.push(max_hops);
    if e2e_version > 0 {
        bytes.push(e2e_version);
    }
    bytes
}

/// Attach a libp2p identity signature to an [`WireMessage::EncryptedDirect`].
pub fn sign_encrypted_direct(
    message: &mut WireMessage,
    keypair: &libp2p::identity::Keypair,
) -> anyhow::Result<()> {
    let WireMessage::EncryptedDirect {
        to_peer,
        sender_enc_pubkey,
        encrypted_payload,
        hop_count,
        max_hops,
        e2e_version,
        mesh_signature,
        ..
    } = message
    else {
        anyhow::bail!("not EncryptedDirect");
    };
    let bytes = encrypted_direct_signing_bytes(
        to_peer,
        sender_enc_pubkey,
        encrypted_payload,
        *hop_count,
        *max_hops,
        *e2e_version,
    );
    let sig = keypair
        .sign(&bytes)
        .map_err(|e| anyhow::anyhow!("failed to sign EncryptedDirect: {e}"))?;
    *mesh_signature = Some(sig);
    Ok(())
}

/// Verify the mesh signature on an incoming [`WireMessage::EncryptedDirect`].
pub fn verify_encrypted_direct(message: &WireMessage, sender_peer_id: &libp2p::PeerId) -> bool {
    let WireMessage::EncryptedDirect {
        to_peer,
        sender_enc_pubkey,
        encrypted_payload,
        mesh_signature,
        hop_count,
        max_hops,
        e2e_version,
        ..
    } = message
    else {
        return false;
    };
    let Some(sig) = mesh_signature else {
        return false;
    };
    for version in [*e2e_version, 0] {
        let bytes = encrypted_direct_signing_bytes(
            to_peer,
            sender_enc_pubkey,
            encrypted_payload,
            *hop_count,
            *max_hops,
            version,
        );
        let multihash = sender_peer_id.as_ref();
        if multihash.code() != 0 {
            return false;
        }
        let Ok(pk) = libp2p::identity::PublicKey::try_decode_protobuf(multihash.digest()) else {
            return false;
        };
        if pk.verify(&bytes, sig) {
            return true;
        }
    }
    false
}

impl DirectMessage {
    pub fn is_expired(&self, now_ms: u64) -> bool {
        let expiry = self
            .envelope
            .created_at_ms
            .saturating_add(self.envelope.ttl_secs as u64 * 1000);
        now_ms >= expiry
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MessageAck {
    pub message_id: String,
    pub accepted: bool,
}

pub type ScopeId = String;

/// Coarse network reachability used for hybrid Internet / mesh routing.
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
pub enum ConnectivityMode {
    /// Reachable bootstrap / Internet path — prefer wide-area transports and refill.
    Internet,
    /// Offline or bootstrap unreachable — stay on local mesh (mDNS, LAN peers).
    MeshOnly,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, Hash)]
pub struct Scope(pub String);

impl Scope {
    pub fn matches(&self, pattern: &str) -> bool {
        if let Some(prefix) = pattern.strip_suffix("/*") {
            self.0.starts_with(prefix)
        } else {
            self.0 == pattern
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StoreStats {
    pub count: usize,
    pub oldest_ms: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WireFrame {
    pub envelope: Envelope,
    pub scope: Option<ScopeId>,
    pub hop_count: u16,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum TransportEvent {
    Listening {
        address: String,
    },
    PeerUp {
        peer_id: String,
    },
    PeerDown {
        peer_id: String,
    },
    DirectReceived {
        from_peer: String,
        message: WireMessage,
    },
    ScopedReceived {
        from_peer: String,
        scope: ScopeId,
        payload: Vec<u8>,
    },
    DirectAck {
        from_peer: String,
        ack: MessageAck,
    },
    SendFailure {
        to_peer: String,
        reason: String,
    },
    ConnectivityChanged {
        mode: ConnectivityMode,
    },
}

#[async_trait]
pub trait MeshTransport: Send {
    fn local_peer_id(&self) -> String;
    fn known_peers(&self) -> Vec<String>;
    fn local_keypair(&self) -> Option<libp2p::identity::Keypair>;
    async fn dial_peer(&mut self, multiaddr: String) -> anyhow::Result<()>;
    /// Store multiaddr for periodic re-dial and connect now (QR / Connect tab).
    async fn remember_and_dial(&mut self, multiaddr: String) -> anyhow::Result<()>;
    /// Re-attempt all remembered bootstrap / invite dial targets.
    fn redial_stored_targets(&mut self);
    /// Dial queued `/p2p-circuit` targets once relay reservation setup is ready.
    fn try_deferred_circuit_dials(&mut self) {}
    async fn send_direct(&mut self, to_peer: String, message: WireMessage) -> anyhow::Result<()>;
    async fn publish_scoped(&mut self, scope: ScopeId, payload: Vec<u8>) -> anyhow::Result<()>;
    /// Join a gossipsub topic so scoped messages for `scope` are received.
    async fn subscribe_scope(&mut self, scope: ScopeId) -> anyhow::Result<()>;
    /// Leave a gossipsub topic.
    async fn unsubscribe_scope(&mut self, scope: ScopeId) -> anyhow::Result<()>;
    fn is_peer_connected(&self, peer_id: &str) -> bool {
        self.known_peers().iter().any(|p| p == peer_id)
    }
    async fn next_event(&mut self) -> anyhow::Result<TransportEvent>;
    fn connected_peer_count(&self) -> usize {
        self.known_peers().len()
    }
    fn max_direct_peers(&self) -> usize {
        usize::MAX
    }
    fn peer_cap_rejections(&self) -> u64 {
        0
    }
    /// Register an active BLE libp2p link (F3); host opens GATT after handoff.
    fn register_ble_link(&mut self, _peer_id: String, _device_id: &str) -> Option<TransportEvent> {
        None
    }
    fn remove_ble_link(&mut self, _peer_id: &str) -> Option<TransportEvent> {
        None
    }
    fn ingest_ble_stream_frame(&mut self, _from_peer: &str, _frame: &[u8]) -> anyhow::Result<()> {
        Ok(())
    }
    fn drain_ble_stream_outbound(&mut self, _to_peer: &str, _max: usize) -> Vec<Vec<u8>> {
        Vec::new()
    }
    fn ble_peer_addrs(&self) -> Vec<String> {
        Vec::new()
    }
    fn drain_pending_ble_events(&mut self) -> Vec<TransportEvent> {
        Vec::new()
    }
    /// Queue F2 side-channel wire message on the unified bearer (best-effort).
    fn queue_bearer_side_wire(
        &mut self,
        _to_peer: String,
        _message: WireMessage,
    ) -> anyhow::Result<()> {
        Ok(())
    }
    /// Drain encoded bearer frames from the F2 side queue.
    fn drain_bearer_side_outbound(
        &mut self,
        _to_peer: Option<&str>,
        _max: usize,
    ) -> Vec<(String, Vec<u8>)> {
        Vec::new()
    }
    /// Drain encoded bearer frames (F2 side + F3 stream) for mesh hosts.
    fn drain_bearer_outbound(
        &mut self,
        _to_peer: Option<&str>,
        _max: usize,
    ) -> Vec<(String, Vec<u8>, crate::bearer_transport::BearerMedium)> {
        Vec::new()
    }
    /// Queue bulk bytes on Wi‑Fi Direct bearer.
    fn queue_bearer_bulk(&mut self, _to_peer: String, _payload: Vec<u8>) -> anyhow::Result<()> {
        Ok(())
    }
    /// Start a Kademlia lookup for `target_peer_id` (no-op on transports without DHT).
    fn kad_find_peer(&mut self, _target_peer_id: &str) {}
    /// Adjust Kademlia activity for connectivity mode changes.
    fn kad_on_connectivity_changed(&mut self, _mode: ConnectivityMode) {}
    /// Tighten peer/mDNS caps when local peer density exceeds threshold (AR-06).
    fn set_density_backoff(&mut self, _active: bool, _max_direct_peers: usize) {}
}

#[async_trait]
pub trait MessageStore: Send + Sync {
    async fn persist(&self, message: &DirectMessage) -> anyhow::Result<()>;
    async fn recent(&self, limit: usize) -> anyhow::Result<Vec<DirectMessage>>;
    async fn contains(&self, message_id: &str) -> anyhow::Result<bool>;
    async fn load_by_id(&self, message_id: &str) -> anyhow::Result<Option<DirectMessage>>;
    async fn list_ids_window(&self, _window: Duration) -> anyhow::Result<Vec<String>>;
    async fn gc_expired(&self) -> anyhow::Result<usize>;
    async fn stats(&self) -> anyhow::Result<StoreStats>;
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::data::Envelope;

    #[test]
    fn direct_message_expiry_respects_ttl() {
        let mut envelope = Envelope::new("a".to_string(), Some("b".to_string()), b"hi".to_vec());
        envelope.created_at_ms = 1_000;
        envelope.ttl_secs = 2;
        let msg = DirectMessage {
            envelope,
            body: "hi".to_string(),
        };
        assert!(!msg.is_expired(2_999));
        assert!(msg.is_expired(3_000));
    }
}
