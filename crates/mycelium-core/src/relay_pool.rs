// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! Multi-relay bootstrap pool with hash-based client rotation (Phase D2, SD-071).

use crate::bootstrap::{peer_id_from_multiaddr, RELAY_PEER_ID};

/// One public relay reachable via raw TCP (and optional QUIC in bootstrap list).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RelayEntry {
    pub peer_id: String,
    /// TCP multiaddr including `/p2p/<relay_id>` (no circuit suffix).
    pub tcp_multiaddr: String,
}

#[derive(Debug, Clone, Default)]
pub struct RelayPool {
    entries: Vec<RelayEntry>,
}

impl RelayPool {
    /// Build a deduplicated pool from bootstrap multiaddrs (TCP entries only).
    pub fn from_bootstrap_peers(peers: &[impl AsRef<str>]) -> Self {
        let mut entries: Vec<RelayEntry> = Vec::new();
        for addr in peers {
            let s = addr.as_ref().trim();
            if s.is_empty() || !s.contains("/tcp/") || s.contains("/p2p-circuit") {
                continue;
            }
            let Some(relay_id) = peer_id_from_multiaddr(s) else {
                continue;
            };
            if entries.iter().any(|e| e.peer_id == relay_id) {
                continue;
            }
            entries.push(RelayEntry {
                peer_id: relay_id,
                tcp_multiaddr: s.to_string(),
            });
        }
        if entries.is_empty() {
            entries.push(RelayEntry {
                peer_id: RELAY_PEER_ID.to_string(),
                tcp_multiaddr: crate::bootstrap::BOOTSTRAP_PEERS[0].to_string(),
            });
        }
        Self { entries }
    }

    pub fn len(&self) -> usize {
        self.entries.len()
    }

    pub fn is_empty(&self) -> bool {
        self.entries.is_empty()
    }

    pub fn entries(&self) -> &[RelayEntry] {
        &self.entries
    }

    pub fn is_relay_peer(&self, peer_id: &str) -> bool {
        let p = peer_id.trim();
        self.entries.iter().any(|e| e.peer_id == p)
    }

    /// Stable relay pick for a remote peer (spreads load across the pool).
    pub fn select_index_for(&self, seed: &str) -> usize {
        if self.entries.is_empty() {
            return 0;
        }
        let h = blake3::hash(seed.as_bytes());
        usize::from_le_bytes(h.as_bytes()[0..8].try_into().expect("8 bytes")) % self.entries.len()
    }

    pub fn entry(&self, index: usize) -> Option<&RelayEntry> {
        self.entries.get(index % self.entries.len().max(1))
    }

    pub fn rotate_index(&self, current: usize) -> usize {
        if self.entries.len() <= 1 {
            return current;
        }
        (current + 1) % self.entries.len()
    }

    /// Circuit multiaddr to reach `remote_peer_id` via relay at `relay_index`.
    pub fn circuit_multiaddr(&self, remote_peer_id: &str, relay_index: usize) -> Option<String> {
        let remote = remote_peer_id.trim();
        if remote.is_empty() {
            return None;
        }
        let entry = self.entry(relay_index)?;
        Some(format!("{}/p2p-circuit/p2p/{remote}", entry.tcp_multiaddr))
    }

    /// Load distribution when `node_count` clients each hash-pick a relay.
    pub fn load_distribution(node_count: usize, pool: &RelayPool) -> Vec<usize> {
        let n = pool.len().max(1);
        let mut counts = vec![0usize; n];
        for i in 0..node_count {
            let seed = format!("node-{i}");
            counts[pool.select_index_for(&seed)] += 1;
        }
        counts
    }

    /// Max share of nodes on any single relay (0.0–1.0).
    pub fn max_relay_share(node_count: usize, pool: &RelayPool) -> f64 {
        if node_count == 0 || pool.is_empty() {
            return 0.0;
        }
        let counts = Self::load_distribution(node_count, pool);
        let max = *counts.iter().max().unwrap_or(&0);
        max as f64 / node_count as f64
    }
}

/// True when `peer_id` matches any relay in the built-in bootstrap pool.
pub fn is_relay_peer_in_pool(peer_id: &str, bootstrap_peers: &[impl AsRef<str>]) -> bool {
    RelayPool::from_bootstrap_peers(bootstrap_peers).is_relay_peer(peer_id)
}

#[cfg(test)]
mod tests {
    use super::*;
    use libp2p::identity;

    fn relay_addr(ip: &str) -> String {
        let kp = identity::Keypair::generate_ed25519();
        format!("/ip4/{}/tcp/4001/p2p/{}", ip, kp.public().to_peer_id())
    }

    fn sample_pool() -> RelayPool {
        RelayPool::from_bootstrap_peers(&[
            relay_addr("10.0.0.1"),
            relay_addr("10.0.0.2"),
            relay_addr("10.0.0.3"),
        ])
    }

    #[test]
    fn pool_deduplicates_relays() {
        let pool = sample_pool();
        assert_eq!(pool.len(), 3);
    }

    #[test]
    fn circuit_multiaddr_uses_selected_relay() {
        let pool = sample_pool();
        let remote = "12D3KooWRemotePeerIdForTestOnly012345678901234";
        let addr = pool.circuit_multiaddr(remote, 1).expect("circuit");
        assert!(addr.contains("10.0.0.2"));
        assert!(addr.contains(remote));
    }

    #[test]
    fn festival_soak_500_nodes_no_relay_collapse() {
        let pool = sample_pool();
        let share = RelayPool::max_relay_share(500, &pool);
        // With 3 relays, uniform hashing should stay well below 50% on one host.
        assert!(
            share < 0.45,
            "single relay share {share} too high for 500-node soak"
        );
        let counts = RelayPool::load_distribution(500, &pool);
        assert!(
            counts.iter().all(|&c| c > 0),
            "every relay should get traffic"
        );
    }

    #[test]
    fn relay_failover_drill_rotates_index() {
        let pool = sample_pool();
        let start = pool.select_index_for("client-a");
        let after_fail = pool.rotate_index(start);
        assert_ne!(start, after_fail);
        let after_second = pool.rotate_index(after_fail);
        assert_ne!(after_fail, after_second);
    }

    #[test]
    fn hash_selection_is_stable() {
        let pool = sample_pool();
        assert_eq!(
            pool.select_index_for("peer-xyz"),
            pool.select_index_for("peer-xyz")
        );
    }
}
