// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! ADR 006 phase 2 — classify [`NodeCommand`] mesh writes and typed send helpers.

use mycelium_core::consistency::{
    Consistency, EventuallyConsistentModel, LocalModel, StateOp, OP_DIRECT_MESSAGE_SEND,
    OP_GOSSIP_BULLETIN_PUBLISH, OP_INVITE_PARSE, OP_SOS_BROADCAST,
};
use mycelium_core::transport::WireMessage;

use crate::ingest_guard;
use crate::NodeCommand;

impl NodeCommand {
    /// Consistency level for mesh-visible state changes (see ADR 006).
    pub fn mesh_consistency(&self) -> Option<Consistency> {
        match self {
            NodeCommand::SendDirect { .. }
            | NodeCommand::SendDirectPayload { .. }
            | NodeCommand::SendWire { .. }
            | NodeCommand::SendWireOrQueueBle { .. } => Some(Consistency::EventuallyConsistent),
            NodeCommand::Broadcast { scope, .. } | NodeCommand::BroadcastPayload { scope, .. } => {
                if ingest_guard::is_sos_scope(scope) {
                    Some(Consistency::Local)
                } else {
                    Some(Consistency::EventuallyConsistent)
                }
            }
            NodeCommand::SeedPeerEncKey { .. } | NodeCommand::SeedPeerRatchetBundle { .. } => {
                Some(Consistency::EventuallyConsistent)
            }
            _ => None,
        }
    }
}

impl crate::NodeHandle {
    /// Plaintext direct message ([`OP_DIRECT_MESSAGE_SEND`]).
    pub async fn mesh_send_direct_text(
        &self,
        to_peer: String,
        body: String,
        _op: StateOp<EventuallyConsistentModel>,
    ) -> anyhow::Result<()> {
        debug_assert_eq!(_op.name, OP_DIRECT_MESSAGE_SEND.name);
        self.send(NodeCommand::SendDirect { to_peer, body }).await
    }

    /// Direct mesh payload ([`OP_DIRECT_MESSAGE_SEND`]).
    pub async fn mesh_send_direct(
        &self,
        to_peer: String,
        body: String,
        payload: Vec<u8>,
        _op: StateOp<EventuallyConsistentModel>,
    ) -> anyhow::Result<()> {
        debug_assert_eq!(_op.name, OP_DIRECT_MESSAGE_SEND.name);
        self.send(NodeCommand::SendDirectPayload {
            to_peer,
            body,
            payload,
        })
        .await
    }

    /// Scoped gossip bulletin ([`OP_GOSSIP_BULLETIN_PUBLISH`]).
    pub async fn mesh_broadcast(
        &self,
        scope: String,
        body: String,
        payload: Vec<u8>,
        _op: StateOp<EventuallyConsistentModel>,
    ) -> anyhow::Result<()> {
        debug_assert_eq!(_op.name, OP_GOSSIP_BULLETIN_PUBLISH.name);
        self.send(NodeCommand::BroadcastPayload {
            scope,
            body,
            payload,
        })
        .await
    }

    /// SOS bulletin with local compose + propagation cap ([`OP_SOS_BROADCAST`]).
    pub async fn mesh_broadcast_sos(
        &self,
        scope: String,
        body: String,
        payload: Vec<u8>,
        _op: StateOp<LocalModel>,
    ) -> anyhow::Result<()> {
        debug_assert_eq!(_op.name, OP_SOS_BROADCAST.name);
        self.send(NodeCommand::BroadcastPayload {
            scope,
            body,
            payload,
        })
        .await
    }

    /// Direct wire frame (libp2p or BLE queue) ([`OP_DIRECT_MESSAGE_SEND`]).
    pub async fn mesh_send_wire(
        &self,
        to_peer: String,
        message: WireMessage,
        _op: StateOp<EventuallyConsistentModel>,
    ) -> anyhow::Result<()> {
        debug_assert_eq!(_op.name, OP_DIRECT_MESSAGE_SEND.name);
        self.send(NodeCommand::SendWire { to_peer, message }).await
    }

    /// Wire over libp2p if connected, else BLE queue ([`OP_DIRECT_MESSAGE_SEND`]).
    pub async fn mesh_send_wire_or_ble(
        &self,
        to_peer: String,
        message: WireMessage,
        _op: StateOp<EventuallyConsistentModel>,
    ) -> anyhow::Result<()> {
        debug_assert_eq!(_op.name, OP_DIRECT_MESSAGE_SEND.name);
        self.send(NodeCommand::SendWireOrQueueBle { to_peer, message })
            .await
    }

    /// Invite / QR enc-key seed ([`OP_INVITE_PARSE`]).
    pub async fn mesh_seed_invite_enc_key(
        &self,
        peer_id: String,
        enc_pubkey_hex: String,
        _op: StateOp<EventuallyConsistentModel>,
    ) -> anyhow::Result<()> {
        debug_assert_eq!(_op.name, OP_INVITE_PARSE.name);
        self.send(NodeCommand::SeedPeerEncKey {
            peer_id,
            enc_pubkey_hex,
        })
        .await
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use mycelium_core::consistency::OP_COIN_TRANSFER;

    #[test]
    fn command_consistency_matches_adr() {
        let dm = NodeCommand::SendDirect {
            to_peer: "p".into(),
            body: "hi".into(),
        };
        assert_eq!(
            dm.mesh_consistency(),
            Some(Consistency::EventuallyConsistent)
        );

        let sos = NodeCommand::BroadcastPayload {
            scope: "mycelium/sos/v1".into(),
            body: "sos".into(),
            payload: vec![],
        };
        assert_eq!(sos.mesh_consistency(), Some(Consistency::Local));

        let chat = NodeCommand::BroadcastPayload {
            scope: "mycelium/chat".into(),
            body: "chat".into(),
            payload: vec![],
        };
        assert_eq!(
            chat.mesh_consistency(),
            Some(Consistency::EventuallyConsistent)
        );

        let seed = NodeCommand::SeedPeerEncKey {
            peer_id: "p".into(),
            enc_pubkey_hex: "aa".into(),
        };
        assert_eq!(
            seed.mesh_consistency(),
            Some(Consistency::EventuallyConsistent)
        );

        assert_eq!(
            NodeCommand::SetEnergyState(mycelium_core::energy::NodeState::Active)
                .mesh_consistency(),
            None
        );
    }

    #[test]
    fn mesh_ops_reference_adr_constants() {
        assert_eq!(
            OP_DIRECT_MESSAGE_SEND.consistency(),
            Consistency::EventuallyConsistent
        );
        assert_eq!(
            OP_GOSSIP_BULLETIN_PUBLISH.consistency(),
            Consistency::EventuallyConsistent
        );
        assert_eq!(OP_SOS_BROADCAST.consistency(), Consistency::Local);
        assert_eq!(
            OP_COIN_TRANSFER.consistency(),
            Consistency::SettlementRequired
        );
    }
}
