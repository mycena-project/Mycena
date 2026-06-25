// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
use libp2p::kad::store::MemoryStore;
use libp2p::{
    gossipsub::{self, MessageAuthenticity},
    identify, kad, mdns, relay,
    request_response::{self, ProtocolSupport},
    swarm::NetworkBehaviour,
    StreamProtocol,
};
use mycelium_core::data::now_ms;
use mycelium_core::mesh_profile::MeshProfile;
use mycelium_core::transport::WireMessage;
use serde::{Deserialize, Serialize};
use std::time::Duration;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DirectMessageRequest {
    pub message: WireMessage,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DirectMessageResponse {
    pub message_id: String,
    pub accepted: bool,
    pub received_at_ms: u64,
}

#[derive(NetworkBehaviour)]
pub struct MeshBehaviour {
    pub relay: relay::client::Behaviour,
    pub dcutr: libp2p::dcutr::Behaviour,
    pub identify: identify::Behaviour,
    pub mdns: mdns::tokio::Behaviour,
    pub gossip: gossipsub::Behaviour,
    pub direct: request_response::cbor::Behaviour<DirectMessageRequest, DirectMessageResponse>,
    pub kad: kad::Behaviour<MemoryStore>,
}

impl MeshBehaviour {
    pub fn new(
        local_key: &libp2p::identity::Keypair,
        relay: relay::client::Behaviour,
        mesh_profile: MeshProfile,
    ) -> Self {
        let local_peer_id = local_key.public().to_peer_id();
        let params = mesh_profile.params();
        let gossip_config = gossipsub::ConfigBuilder::default()
            .mesh_n(params.mesh_n)
            .mesh_n_low(params.mesh_n_low)
            .mesh_n_high(params.mesh_n_high)
            .mesh_outbound_min(params.mesh_outbound_min)
            .heartbeat_interval(Duration::from_secs(params.heartbeat_interval_secs))
            .history_length(params.history_length)
            .history_gossip(params.history_gossip)
            .build()
            .expect("gossipsub config");
        let gossip = gossipsub::Behaviour::new(
            MessageAuthenticity::Signed(local_key.clone()),
            gossip_config,
        )
        .expect("gossipsub init");

        let mdns =
            mdns::tokio::Behaviour::new(mdns::Config::default(), local_peer_id).expect("mdns init");
        let direct = request_response::cbor::Behaviour::new(
            [(
                StreamProtocol::new("/mycelium/direct/1"),
                ProtocolSupport::Full,
            )],
            request_response::Config::default(),
        );
        let identify = identify::Behaviour::new(identify::Config::new(
            "mycelium/0.1".into(),
            local_key.public(),
        ));

        let mut kad_config = kad::Config::new(StreamProtocol::new("/mycelium/kad/1.0.0"));
        kad_config.set_query_timeout(Duration::from_secs(60));
        let kad =
            kad::Behaviour::with_config(local_peer_id, MemoryStore::new(local_peer_id), kad_config);

        Self {
            relay,
            dcutr: libp2p::dcutr::Behaviour::new(local_peer_id),
            identify,
            mdns,
            gossip,
            direct,
            kad,
        }
    }
}

pub fn make_direct_response(message_id: String) -> DirectMessageResponse {
    DirectMessageResponse {
        message_id,
        accepted: true,
        received_at_ms: now_ms(),
    }
}
