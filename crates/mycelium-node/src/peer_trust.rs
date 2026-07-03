// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! Persistent peer trust for SD-031 relay selection and Sybil resistance.

use anyhow::Context;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::Path;
use std::sync::Mutex;

const RELIABILITY_ALPHA: f32 = 0.15;
const SPAM_BETA: f32 = 0.20;
const RELAY_EMA_ALPHA: f32 = 0.12;

/// Trust signals for a mesh peer (persisted across restarts).
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct PeerTrust {
    /// When this peer was first observed (epoch ms).
    pub first_seen_ms: u64,
    /// EMA of successful vs. failed message handling [0, 1].
    pub reliability: f32,
    /// EMA of invalid / duplicate / rate-limited ingest events [0, 1].
    pub spam_score: f32,
    /// EMA of relay delivery ack success [0, 1].
    pub relay_success_rate: f32,
    pub relay_success_count: u32,
    pub relay_failure_count: u32,
    /// Confirmed relationship depth (1 = we stored their enc key / contact hello).
    pub mutual_contacts: u16,
    pub messages_accepted: u32,
    pub messages_rejected: u32,
    pub strike_count: u8,
    pub last_strike_ms: u64,
    pub throttled_until_ms: u64,
    pub last_updated_ms: u64,
}

impl Default for PeerTrust {
    fn default() -> Self {
        Self::new_peer(0)
    }
}

impl PeerTrust {
    pub fn new_peer(now_ms: u64) -> Self {
        Self {
            first_seen_ms: now_ms,
            reliability: 0.5,
            spam_score: 0.0,
            relay_success_rate: 0.5,
            relay_success_count: 0,
            relay_failure_count: 0,
            mutual_contacts: 0,
            messages_accepted: 0,
            messages_rejected: 0,
            strike_count: 0,
            last_strike_ms: 0,
            throttled_until_ms: 0,
            last_updated_ms: now_ms,
        }
    }

    /// Pessimistic prior for unseen peers at relay selection time (Sybil-resistant).
    pub fn cold_unknown(now_ms: u64) -> Self {
        Self {
            first_seen_ms: now_ms,
            reliability: 0.25,
            spam_score: 0.0,
            relay_success_rate: 0.25,
            relay_success_count: 0,
            relay_failure_count: 0,
            mutual_contacts: 0,
            messages_accepted: 0,
            messages_rejected: 0,
            strike_count: 0,
            last_strike_ms: 0,
            throttled_until_ms: 0,
            last_updated_ms: now_ms,
        }
    }

    pub fn age_ms(&self, now_ms: u64) -> u64 {
        now_ms.saturating_sub(self.first_seen_ms)
    }

    fn ema(prev: f32, sample: f32, alpha: f32) -> f32 {
        prev + alpha * (sample - prev)
    }

    pub fn touch(&mut self, now_ms: u64) {
        if self.first_seen_ms == 0 {
            self.first_seen_ms = now_ms;
        }
        self.last_updated_ms = now_ms;
    }

    pub fn record_message_accepted(&mut self, now_ms: u64) {
        self.touch(now_ms);
        self.messages_accepted = self.messages_accepted.saturating_add(1);
        self.reliability = Self::ema(self.reliability, 1.0, RELIABILITY_ALPHA);
        if self.strike_count > 0 {
            self.strike_count = self.strike_count.saturating_sub(1);
        }
    }

    pub fn record_message_rejected(&mut self, now_ms: u64) {
        self.touch(now_ms);
        self.messages_rejected = self.messages_rejected.saturating_add(1);
        self.reliability = Self::ema(self.reliability, 0.0, RELIABILITY_ALPHA);
        self.spam_score = Self::ema(self.spam_score, 1.0, SPAM_BETA);
        self.register_strike(now_ms);
    }

    pub fn register_strike(&mut self, now_ms: u64) {
        self.touch(now_ms);
        if now_ms.saturating_sub(self.last_strike_ms) > 60_000 {
            self.strike_count = 0;
        }
        self.strike_count = self.strike_count.saturating_add(1);
        self.last_strike_ms = now_ms;
        if self.strike_count >= 3 {
            self.throttled_until_ms = now_ms + 5 * 60 * 1000;
        }
    }

    pub fn decay_strikes_if_idle(&mut self, now_ms: u64) {
        if now_ms.saturating_sub(self.last_strike_ms) > 10 * 60 * 1000 {
            self.strike_count = 0;
        }
    }

    pub fn is_throttled(&mut self, now_ms: u64) -> bool {
        self.decay_strikes_if_idle(now_ms);
        now_ms < self.throttled_until_ms
    }

    pub fn record_relay_outcome(&mut self, now_ms: u64, success: bool) {
        self.touch(now_ms);
        if success {
            self.relay_success_count = self.relay_success_count.saturating_add(1);
            self.relay_success_rate = Self::ema(self.relay_success_rate, 1.0, RELAY_EMA_ALPHA);
        } else {
            self.relay_failure_count = self.relay_failure_count.saturating_add(1);
            self.relay_success_rate = Self::ema(self.relay_success_rate, 0.0, RELAY_EMA_ALPHA);
        }
    }

    /// Local-only signal: we persisted this peer's enc key (confirmed contact).
    pub fn note_confirmed_contact(&mut self, now_ms: u64) {
        self.touch(now_ms);
        if self.mutual_contacts == 0 {
            self.mutual_contacts = 1;
        }
    }

    /// Weighted score for SD-031 relay selection (higher = preferred).
    pub fn relay_selection_score(&self, now_ms: u64) -> f64 {
        let age_ms = self.age_ms(now_ms);
        let age_factor = (age_ms as f64 / (7.0 * 86_400_000.0)).min(1.0);

        let mut score = 0.0;
        score += 0.35 * f64::from(self.reliability);
        score += 0.30 * f64::from(self.relay_success_rate);
        score += 0.15 * age_factor;
        score += 0.10 * (f64::from(self.mutual_contacts.min(5)) / 5.0);
        score -= 0.25 * f64::from(self.spam_score);
        score -= 0.20 * f64::from(self.strike_count) / 10.0;

        if age_ms < 3_600_000 {
            let youth = 1.0 - age_ms as f64 / 3_600_000.0;
            score -= 0.20 * youth;
        }
        score
    }
}

/// Snapshot for UI / FFI (legacy strike fields preserved).
#[derive(Debug, Default, Clone, Serialize, Deserialize)]
pub struct PeerReputationSnapshot {
    pub strikes: u8,
    pub last_strike_ms: u64,
    pub throttled_until_ms: u64,
    pub reliability: f32,
    pub spam_score: f32,
    pub relay_success_rate: f32,
    pub age_ms: u64,
    pub mutual_contacts: u16,
}

impl PeerReputationSnapshot {
    pub fn from_trust(trust: &PeerTrust, now_ms: u64) -> Self {
        Self {
            strikes: trust.strike_count,
            last_strike_ms: trust.last_strike_ms,
            throttled_until_ms: trust.throttled_until_ms,
            reliability: trust.reliability,
            spam_score: trust.spam_score,
            relay_success_rate: trust.relay_success_rate,
            age_ms: trust.age_ms(now_ms),
            mutual_contacts: trust.mutual_contacts,
        }
    }
}

pub struct PeerTrustStore {
    _db: sled::Db,
    tree: sled::Tree,
    cache: Mutex<HashMap<String, PeerTrust>>,
}

impl PeerTrustStore {
    pub fn open(db_path: &str) -> anyhow::Result<Self> {
        let path = Path::new(db_path).join("peer_trust");
        let db = sled::Config::new()
            .path(path)
            .flush_every_ms(Some(5000))
            .open()
            .context("opening peer_trust db")?;
        let tree = db.open_tree("trust")?;
        let mut cache = HashMap::new();
        for item in tree.iter() {
            let (k, v) = item?;
            let peer_id = String::from_utf8(k.to_vec()).context("peer id utf8")?;
            let trust: PeerTrust = bincode::deserialize(&v).context("decode peer trust")?;
            cache.insert(peer_id, trust);
        }
        Ok(Self {
            _db: db,
            tree,
            cache: Mutex::new(cache),
        })
    }

    pub(crate) fn load_all(&self) -> HashMap<String, PeerTrust> {
        self.cache
            .lock()
            .expect("peer trust cache lock") // INVARIANT: mutex not poisoned
            .clone()
    }

    fn persist(&self, peer_id: &str, trust: &PeerTrust) -> anyhow::Result<()> {
        let bytes = bincode::serialize(trust)?;
        self.tree.insert(peer_id.as_bytes(), bytes)?;
        Ok(())
    }

    pub(crate) fn with_peer<F>(&self, peer_id: &str, now_ms: u64, f: F) -> anyhow::Result<()>
    where
        F: FnOnce(&mut PeerTrust),
    {
        let mut cache = self.cache.lock().expect("peer trust cache lock"); // INVARIANT: mutex not poisoned
        let entry = cache
            .entry(peer_id.to_string())
            .or_insert_with(|| PeerTrust::new_peer(now_ms));
        entry.touch(now_ms);
        f(entry);
        let trust = entry.clone();
        drop(cache);
        self.persist(peer_id, &trust)
    }

    pub(crate) fn get(&self, peer_id: &str) -> Option<PeerTrust> {
        self.cache
            .lock()
            .expect("peer trust cache lock") // INVARIANT: mutex not poisoned
            .get(peer_id)
            .cloned()
    }

    pub(crate) fn is_throttled(&self, peer_id: &str, now_ms: u64) -> bool {
        let mut cache = self.cache.lock().expect("peer trust cache lock"); // INVARIANT: mutex not poisoned
        if let Some(trust) = cache.get_mut(peer_id) {
            return trust.is_throttled(now_ms);
        }
        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn trust_persists_across_reopen() {
        let dir = tempfile::tempdir().expect("tempdir");
        let base = dir.path().to_str().expect("path");
        {
            let store = PeerTrustStore::open(base).expect("open");
            store
                .with_peer("12D3KooWTrustPeerIdForPersistTest01", 1000, |t| {
                    t.reliability = 0.88;
                    t.relay_success_rate = 0.91;
                    t.first_seen_ms = 500;
                })
                .expect("save");
        }
        let store2 = PeerTrustStore::open(base).expect("reopen");
        let t = store2
            .get("12D3KooWTrustPeerIdForPersistTest01")
            .expect("loaded");
        assert!((t.reliability - 0.88).abs() < f32::EPSILON);
        assert!((t.relay_success_rate - 0.91).abs() < f32::EPSILON);
    }
}
