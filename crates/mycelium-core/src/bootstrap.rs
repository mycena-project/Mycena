// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! Öffentliche Bootstrap-Peers für das Mycelium-Netzwerk.
//!
//! Nach einem Relay-Redeployment:
//! 1. `curl -s https://mycelium-relay.fly.dev/` → `peer_id`
//! 2. [`RELAY_PEER_ID`] und [`BOOTSTRAP_PEERS`] aktualisieren (TCP via dedicated `/ip4/…`)
//! 3. Commit + Build → Clients dialen den Relay korrekt

/// Fly.io dedicated IPv4 (raw libp2p TCP/UDP; shared ingress breaks Noise handshake).
pub const RELAY_IPV4: &str = "149.248.194.198";

pub const RELAY_PEER_ID: &str = "12D3KooWSkVL5ThExB7paWskDKc2pUobKrV9yWVabMpUdw8hE2wj";

/// Public relay / bootstrap infra — not a user device in Connect UI.
pub fn is_relay_peer(peer_id: &str) -> bool {
    peer_id.trim() == RELAY_PEER_ID
}

/// True for a libp2p PeerId string (not a multiaddr or truncated garbage).
pub fn is_mesh_peer_id(peer_id: &str) -> bool {
    let p = peer_id.trim();
    p.starts_with("12D3KooW") && p.len() >= 52 && !p.contains('/')
}

pub const BOOTSTRAP_PEERS: &[&str] = &[
    "/ip4/149.248.194.198/tcp/4001/p2p/12D3KooWSkVL5ThExB7paWskDKc2pUobKrV9yWVabMpUdw8hE2wj",
    "/ip4/149.248.194.198/udp/4001/quic-v1/p2p/12D3KooWSkVL5ThExB7paWskDKc2pUobKrV9yWVabMpUdw8hE2wj",
    // Second regional relay (after deploy, e.g. fly regions add ams):
    // "/ip4/<ams-ip>/tcp/4001/p2p/12D3KooW...",
];

/// Extract the `/p2p/<PeerId>` component from a libp2p multiaddr.
pub fn peer_id_from_multiaddr(addr: &str) -> Option<String> {
    let parsed: libp2p::Multiaddr = addr.parse().ok()?;
    peer_id_from_parsed_multiaddr(&parsed).map(|p| p.to_string())
}

pub fn peer_id_from_parsed_multiaddr(addr: &libp2p::Multiaddr) -> Option<libp2p::PeerId> {
    use libp2p::multiaddr::Protocol;
    addr.iter().find_map(|proto| {
        if let Protocol::P2p(peer_id) = proto {
            Some(peer_id)
        } else {
            None
        }
    })
}

/// libp2p circuit address to reach `remote_peer_id` via the public relay (NAT traversal).
pub fn relay_circuit_multiaddr(remote_peer_id: &str) -> Option<String> {
    let remote = remote_peer_id.trim();
    if remote.is_empty() {
        return None;
    }
    let base = BOOTSTRAP_PEERS.iter().find(|addr| addr.contains("/tcp/"))?;
    Some(format!("{base}/p2p-circuit/p2p/{remote}"))
}

/// Shareable dial info: v2 invite first (QR: peer + E2E key + optional LAN addr), then extras.
pub fn shareable_dial_multiaddrs(
    local_peer_id: &str,
    enc_pubkey_hex: &str,
    listen_addrs: &[String],
) -> Vec<String> {
    let best_direct = listen_addrs.iter().find_map(|a| {
        let trimmed = a.trim();
        if trimmed.is_empty()
            || !trimmed.contains("/ip4/")
            || trimmed.contains("127.0.0.1")
            || trimmed.contains("/p2p-circuit")
        {
            return None;
        }
        Some(if trimmed.contains("/p2p/") {
            trimmed.to_string()
        } else {
            format!("{trimmed}/p2p/{local_peer_id}")
        })
    });
    let mut out = Vec::new();
    out.push(crate::invite::encode_invite_v2(
        local_peer_id,
        enc_pubkey_hex,
        best_direct.as_deref(),
    ));
    if let Some(circuit) = relay_circuit_multiaddr(local_peer_id) {
        out.push(circuit);
    }
    for addr in listen_addrs {
        let trimmed = addr.trim();
        if trimmed.is_empty() {
            continue;
        }
        if trimmed.contains("/p2p/") {
            out.push(trimmed.to_string());
        } else {
            out.push(format!("{trimmed}/p2p/{local_peer_id}"));
        }
    }
    out
}

/// Lädt Bootstrap-Peers mit folgender Priorität:
/// 1. Umgebungsvariable `MYCELIUM_BOOTSTRAP_PEERS` (kommasepariert)
/// 2. Datei `<db_path>/bootstrap.txt` (eine Adresse pro Zeile, # = Kommentar)
/// 3. Eingebettete Konstanten aus [`BOOTSTRAP_PEERS`]
pub fn load_bootstrap_peers(db_path: &str) -> Vec<String> {
    if let Ok(env_peers) = std::env::var("MYCELIUM_BOOTSTRAP_PEERS") {
        let peers: Vec<String> = env_peers
            .split(',')
            .map(str::trim)
            .filter(|s| !s.is_empty())
            .map(String::from)
            .collect();
        if !peers.is_empty() {
            tracing::info!(
                "bootstrap: using {} peer(s) from MYCELIUM_BOOTSTRAP_PEERS",
                peers.len()
            );
            return peers;
        }
    }

    let path = std::path::Path::new(db_path).join("bootstrap.txt");
    if path.exists() {
        if let Ok(content) = std::fs::read_to_string(&path) {
            let peers: Vec<String> = content
                .lines()
                .map(str::trim)
                .filter(|l| !l.is_empty() && !l.starts_with('#'))
                .map(String::from)
                .collect();
            if !peers.is_empty() {
                tracing::info!(
                    "bootstrap: using {} peer(s) from {}",
                    peers.len(),
                    path.display()
                );
                return peers;
            }
        }
    }

    tracing::info!(
        "bootstrap: using {} built-in peer(s)",
        BOOTSTRAP_PEERS.len()
    );
    BOOTSTRAP_PEERS.iter().map(|s| (*s).to_string()).collect()
}

/// Reads custom bootstrap peers from `<db_path>/bootstrap.txt` only (no env / built-in fallback).
pub fn load_custom_bootstrap_peers(db_path: &str) -> Vec<String> {
    let path = std::path::Path::new(db_path).join("bootstrap.txt");
    if !path.exists() {
        return Vec::new();
    }
    std::fs::read_to_string(&path)
        .map(|content| {
            content
                .lines()
                .map(str::trim)
                .filter(|l| !l.is_empty() && !l.starts_with('#'))
                .map(String::from)
                .collect()
        })
        .unwrap_or_default()
}

/// Peers / multiaddrs the user added via QR or Connect (retried until connected).
const DIAL_PEERS_FILE: &str = "dial_peers.txt";

/// Load persisted dial targets (one multiaddr per line).
pub fn load_persisted_dial_peers(db_path: &str) -> Vec<String> {
    let path = std::path::Path::new(db_path).join(DIAL_PEERS_FILE);
    if !path.exists() {
        return Vec::new();
    }
    std::fs::read_to_string(&path)
        .map(|content| {
            content
                .lines()
                .map(str::trim)
                .filter(|l| !l.is_empty() && !l.starts_with('#'))
                .map(String::from)
                .collect()
        })
        .unwrap_or_default()
}

/// Append a dial target if not already stored (QR scan, Connect tab, etc.).
/// Circuit targets replace any previously stored circuit entries (one active QR target).
pub fn persist_dial_peer(db_path: &str, multiaddr: &str) -> std::io::Result<()> {
    let trimmed = multiaddr.trim();
    if trimmed.is_empty() {
        return Ok(());
    }
    let mut peers = load_persisted_dial_peers(db_path);
    if trimmed.contains("/p2p-circuit/") {
        peers.retain(|p| !p.contains("/p2p-circuit/"));
    }
    if peers.iter().any(|p| p == trimmed) {
        return Ok(());
    }
    peers.push(trimmed.to_string());
    write_dial_peers_file(db_path, &peers)
}

/// Keep only the most recent circuit dial target; drop duplicate/stale QR scans.
pub fn sanitize_persisted_dial_peers(db_path: &str) -> std::io::Result<()> {
    let peers = load_persisted_dial_peers(db_path);
    let mut cleaned: Vec<String> = peers
        .iter()
        .filter(|p| !p.contains("/p2p-circuit/"))
        .cloned()
        .collect();
    if let Some(last_circuit) = peers.iter().rfind(|p| p.contains("/p2p-circuit/")) {
        cleaned.push(last_circuit.clone());
    }
    if cleaned == peers {
        return Ok(());
    }
    if cleaned.is_empty() {
        let path = std::path::Path::new(db_path).join(DIAL_PEERS_FILE);
        if path.exists() {
            std::fs::remove_file(path)?;
        }
        return Ok(());
    }
    write_dial_peers_file(db_path, &cleaned)
}

fn write_dial_peers_file(db_path: &str, peers: &[String]) -> std::io::Result<()> {
    std::fs::create_dir_all(db_path)?;
    let path = std::path::Path::new(db_path).join(DIAL_PEERS_FILE);
    std::fs::write(path, peers.join("\n"))
}

/// Persists custom bootstrap peers to `<db_path>/bootstrap.txt`.
pub fn save_custom_bootstrap_peers(db_path: &str, peers: &[String]) -> std::io::Result<()> {
    std::fs::create_dir_all(db_path)?;
    let path = std::path::Path::new(db_path).join("bootstrap.txt");
    if peers.is_empty() {
        if path.exists() {
            std::fs::remove_file(path)?;
        }
        return Ok(());
    }
    std::fs::write(path, peers.join("\n"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn peer_id_from_multiaddr_extracts_embedded_id() {
        let addr = BOOTSTRAP_PEERS[0];
        let peer_id = peer_id_from_multiaddr(addr).expect("peer id");
        assert_eq!(peer_id, RELAY_PEER_ID);
    }

    #[test]
    fn relay_circuit_addr_uses_embedded_relay() {
        let addr =
            relay_circuit_multiaddr("12D3KooWExamplePeerIdForTestOnly").expect("circuit addr");
        assert!(addr.contains(RELAY_IPV4));
        assert!(addr.contains("/p2p-circuit/p2p/12D3KooWExamplePeerIdForTestOnly"));
        assert!(addr.contains(RELAY_PEER_ID));
    }

    #[test]
    fn persist_dial_peer_replaces_old_circuit_targets() {
        let dir = tempfile::tempdir().expect("tempdir");
        let db = dir.path().to_str().expect("path");
        let old = relay_circuit_multiaddr("12D3KooWOldPeerIdForTestOnly").expect("old");
        let new = relay_circuit_multiaddr("12D3KooWNewPeerIdForTestOnly").expect("new");
        persist_dial_peer(db, &old).expect("persist old");
        persist_dial_peer(db, &new).expect("persist new");
        let peers = load_persisted_dial_peers(db);
        assert_eq!(peers.len(), 1);
        assert!(peers[0].contains("12D3KooWNewPeerIdForTestOnly"));
    }

    #[test]
    fn is_mesh_peer_id_rejects_multiaddr_and_short_ids() {
        assert!(is_mesh_peer_id(RELAY_PEER_ID));
        assert!(is_mesh_peer_id(
            "12D3KooWLpQgW1svymPTqprdewzviJjoSYe4fVzUzgjP6P9e7Q3A"
        ));
        assert!(!is_mesh_peer_id("12D3KooW"));
        assert!(!is_mesh_peer_id(
            "/dns4/mycelium-relay.fly.dev/tcp/4001/p2p/12D3KooW"
        ));
    }

    #[test]
    fn sanitize_keeps_latest_circuit_only() {
        let dir = tempfile::tempdir().expect("tempdir");
        let db = dir.path().to_str().expect("path");
        write_dial_peers_file(
            db,
            &[
                relay_circuit_multiaddr("12D3KooWFirstPeerIdForTestOnly").expect("a"),
                relay_circuit_multiaddr("12D3KooWSecondPeerIdForTestOnly").expect("b"),
            ],
        )
        .expect("write");
        sanitize_persisted_dial_peers(db).expect("sanitize");
        let peers = load_persisted_dial_peers(db);
        assert_eq!(peers.len(), 1);
        assert!(peers[0].contains("12D3KooWSecondPeerIdForTestOnly"));
    }
}
