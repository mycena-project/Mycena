// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! Gossip payload when a device rotates its libp2p transport PeerId (SD-190 spike).

use serde::{Deserialize, Serialize};

use crate::account_identity::{identity_rotation_signable_bytes, verify_account_signature};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct IdentityRotationGossip {
    pub account_id: String,
    pub new_peer_id: String,
    pub epoch: u64,
    pub signature: Vec<u8>,
}

impl IdentityRotationGossip {
    pub fn sign(
        account_id: &str,
        new_peer_id: &str,
        epoch: u64,
        account_keypair: &libp2p::identity::Keypair,
    ) -> anyhow::Result<Self> {
        let bytes = identity_rotation_signable_bytes(account_id, new_peer_id, epoch);
        let signature = crate::account_identity::sign_account_message(account_keypair, &bytes)?;
        Ok(Self {
            account_id: account_id.to_string(),
            new_peer_id: new_peer_id.to_string(),
            epoch,
            signature,
        })
    }

    pub fn verify(&self) -> bool {
        if self.account_id.is_empty() || self.new_peer_id.is_empty() {
            return false;
        }
        let bytes =
            identity_rotation_signable_bytes(&self.account_id, &self.new_peer_id, self.epoch);
        verify_account_signature(&self.account_id, &bytes, &self.signature)
    }

    pub fn encode(&self) -> anyhow::Result<Vec<u8>> {
        Ok(serde_json::to_vec(self)?)
    }

    pub fn decode(bytes: &[u8]) -> anyhow::Result<Self> {
        Ok(serde_json::from_slice(bytes)?)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use libp2p::identity::Keypair;

    #[test]
    fn rotation_gossip_roundtrip() {
        let kp = Keypair::generate_ed25519();
        let account_id = crate::account_identity::account_id_from_keypair(&kp);
        let gossip = IdentityRotationGossip::sign(
            &account_id,
            "12D3KooWNewTransportPeerIdForRotationTestOnly",
            2,
            &kp,
        )
        .unwrap();
        assert!(gossip.verify());
        let decoded = IdentityRotationGossip::decode(&gossip.encode().unwrap()).unwrap();
        assert_eq!(decoded, gossip);
    }
}
