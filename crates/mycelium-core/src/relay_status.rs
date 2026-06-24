// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! HTTP status probe for the public mycelium-relay (used by desktop UI).

use serde::Serialize;

const DEFAULT_RELAY_HOST: &str = "mycelium-relay.fly.dev";

#[derive(Debug, Clone, Serialize)]
pub struct RelayStatus {
    pub online: bool,
    pub status: String,
    /// Raw libp2p socket count (TCP+QUIC per client; not unique devices).
    pub connections: u64,
    /// Unique peer IDs currently connected to the relay (when reported by server).
    pub unique_peers: u64,
    /// Peers opted into rendezvous and reachable via relay circuit (best user-facing count).
    pub reachable_peers: u64,
    pub peer_id: Option<String>,
}

/// Fetch relay status JSON from `https://<host>/` (blocking).
pub fn fetch_relay_status(host: Option<&str>) -> RelayStatus {
    let host = host.unwrap_or(DEFAULT_RELAY_HOST);
    let url = format!("https://{host}/");
    let reachable_peers = fetch_relay_rendezvous_count(host);
    let resp = match ureq::get(&url)
        .timeout(std::time::Duration::from_secs(8))
        .call()
    {
        Ok(r) => r,
        Err(_) => {
            return RelayStatus {
                online: false,
                status: "offline".into(),
                connections: 0,
                unique_peers: 0,
                reachable_peers,
                peer_id: None,
            };
        }
    };
    let body: serde_json::Value = match resp.into_json() {
        Ok(v) => v,
        Err(_) => {
            return RelayStatus {
                online: false,
                status: "offline".into(),
                connections: 0,
                unique_peers: 0,
                reachable_peers,
                peer_id: None,
            };
        }
    };
    let status = body
        .get("status")
        .and_then(|v| v.as_str())
        .unwrap_or("unknown")
        .to_string();
    let online = status == "ok";
    let connections = body
        .get("connections")
        .and_then(|v| v.as_u64())
        .unwrap_or(0);
    let unique_peers = body
        .get("unique_peers")
        .and_then(|v| v.as_u64())
        .unwrap_or_else(|| {
            body.get("connected_peers")
                .and_then(|v| v.as_u64())
                .unwrap_or(0)
        });
    let reachable_from_status = body
        .get("reachable_peers")
        .and_then(|v| v.as_u64())
        .unwrap_or(0);
    let reachable_peers = if reachable_from_status > 0 {
        reachable_from_status
    } else if reachable_peers > 0 {
        reachable_peers
    } else {
        unique_peers
    };
    let peer_id = body
        .get("peer_id")
        .and_then(|v| v.as_str())
        .map(str::to_string);
    RelayStatus {
        online,
        status,
        connections,
        unique_peers,
        reachable_peers,
        peer_id,
    }
}

fn fetch_relay_rendezvous_count(host: &str) -> u64 {
    let url = format!("https://{host}/rendezvous");
    let resp = match ureq::get(&url)
        .timeout(std::time::Duration::from_secs(8))
        .call()
    {
        Ok(r) => r,
        Err(_) => return 0,
    };
    let body: serde_json::Value = match resp.into_json() {
        Ok(v) => v,
        Err(_) => return 0,
    };
    body.get("peers")
        .and_then(|v| v.as_array())
        .map(|a| a.len() as u64)
        .unwrap_or(0)
}
