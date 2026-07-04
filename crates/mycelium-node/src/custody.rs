// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! Store-and-forward custody queue — spray-and-wait with bounded network copies.

use crate::encounter_log::EncounterLogStore;
use mycelium_core::data::now_ms;
use mycelium_core::transport::DirectMessage;
use serde::{Deserialize, Serialize};
use std::collections::HashSet;
use std::path::Path;

/// Default max active custody copies in the mesh (spray-and-wait).
pub const DEFAULT_MAX_CUSTODY_SPRAY: u8 = 3;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct CustodyConfig {
    pub max_spray_copies: u8,
    pub max_queue_entries: usize,
    /// Background flush interval; `None` for deterministic single-threaded tests.
    #[serde(default = "default_flush_every_ms")]
    pub flush_every_ms: Option<u64>,
}

fn default_flush_every_ms() -> Option<u64> {
    Some(5000)
}

impl Default for CustodyConfig {
    fn default() -> Self {
        Self {
            max_spray_copies: DEFAULT_MAX_CUSTODY_SPRAY,
            max_queue_entries: 512,
            flush_every_ms: default_flush_every_ms(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct StoredCustody {
    message: DirectMessage,
    /// Peers already used for spray (origin) or relay handoff (carrier).
    targets_tried: HashSet<String>,
    enqueued_at_ms: u64,
    /// Origin-only: custody copies released into the mesh.
    #[serde(default)]
    sprays_released: u8,
}

#[derive(Debug, Clone)]
pub struct CustodyForwardPlan {
    pub message_id: String,
    pub to_peer: String,
    pub message: DirectMessage,
}

#[derive(Debug)]
pub struct CustodyQueue {
    db: sled::Db,
    config: CustodyConfig,
}

impl CustodyQueue {
    pub fn open(base_path: &str, config: CustodyConfig) -> anyhow::Result<Self> {
        let path = Path::new(base_path).join("custody");
        std::fs::create_dir_all(&path)?;
        let db = sled::Config::new()
            .path(path)
            .flush_every_ms(config.flush_every_ms)
            .open()?;
        Ok(Self { db, config })
    }

    pub fn config(&self) -> &CustodyConfig {
        &self.config
    }

    pub fn len(&self) -> anyhow::Result<usize> {
        Ok(self.tree()?.len())
    }

    pub fn is_empty(&self) -> anyhow::Result<bool> {
        Ok(self.tree()?.is_empty())
    }

    pub fn enqueue(&self, message: &DirectMessage) -> anyhow::Result<bool> {
        if !Self::eligible_for_custody(message) {
            return Ok(false);
        }
        let tree = self.tree()?;
        let id = message.envelope.id.0.clone();
        if tree.contains_key(id.as_bytes())? {
            return Ok(false);
        }
        if tree.len() >= self.config.max_queue_entries {
            anyhow::bail!("custody queue full");
        }
        let stored = StoredCustody {
            message: message.clone(),
            targets_tried: HashSet::new(),
            enqueued_at_ms: now_ms(),
            sprays_released: 0,
        };
        tree.insert(id.as_bytes(), bincode::serialize(&stored)?)?;
        Ok(true)
    }

    pub fn remove(&self, message_id: &str) -> anyhow::Result<()> {
        let _ = self.tree()?.remove(message_id.as_bytes())?;
        Ok(())
    }

    pub fn prune_expired(&self, now: u64) -> anyhow::Result<usize> {
        let mut removed = 0usize;
        for item in self.tree()?.iter() {
            let (k, v) = item?;
            let stored: StoredCustody = bincode::deserialize(&v)?;
            if !Self::relay_allowed(&stored, now) {
                self.tree()?.remove(k)?;
                removed += 1;
            }
        }
        Ok(removed)
    }

    pub fn next_forward_plan(
        &self,
        local_peer: &str,
        connected_peers: &[String],
        encounter_log: &EncounterLogStore,
    ) -> anyhow::Result<Option<CustodyForwardPlan>> {
        self.next_forward_plan_at(local_peer, connected_peers, encounter_log, now_ms())
    }

    /// Plan at most one custody forward per tick (spray-and-wait pacing).
    pub fn next_forward_plan_at(
        &self,
        local_peer: &str,
        connected_peers: &[String],
        encounter_log: &EncounterLogStore,
        now: u64,
    ) -> anyhow::Result<Option<CustodyForwardPlan>> {
        let mut entries: Vec<(Vec<u8>, StoredCustody)> = Vec::new();
        for item in self.tree()?.iter() {
            let (k, v) = item?;
            entries.push((k.to_vec(), bincode::deserialize(&v)?));
        }
        entries.sort_by(|a, b| a.0.cmp(&b.0));
        for (k, mut stored) in entries {
            if !Self::relay_allowed(&stored, now) {
                continue;
            }
            let mode = Self::forward_mode(&stored, local_peer);
            if matches!(mode, ForwardMode::OriginSpray) && !Self::origin_spray_budget(&stored) {
                continue;
            }
            let tried: Vec<String> = stored.targets_tried.iter().cloned().collect();
            let mut candidates =
                encounter_log.custody_candidates_at(connected_peers, local_peer, &tried, 8, now)?;
            candidates.sort();
            let Some(peer) = candidates
                .into_iter()
                .find(|p| Self::peer_allowed(&stored, p, local_peer))
            else {
                continue;
            };
            stored.targets_tried.insert(peer.clone());
            let outbound = Self::prepare_outbound(&mut stored, local_peer);
            if matches!(mode, ForwardMode::OriginSpray) {
                self.tree()?.insert(k, bincode::serialize(&stored)?)?;
            } else {
                self.tree()?.remove(k)?;
            }
            return Ok(Some(CustodyForwardPlan {
                message_id: stored.message.envelope.id.0.clone(),
                to_peer: peer,
                message: outbound,
            }));
        }
        Ok(None)
    }

    pub fn plans_for_encounter(
        &self,
        local_peer: &str,
        encounter_peer: &str,
        max: usize,
    ) -> anyhow::Result<Vec<CustodyForwardPlan>> {
        self.plans_for_encounter_at(local_peer, encounter_peer, max, now_ms())
    }

    pub fn plans_for_encounter_at(
        &self,
        local_peer: &str,
        encounter_peer: &str,
        max: usize,
        now: u64,
    ) -> anyhow::Result<Vec<CustodyForwardPlan>> {
        let mut out = Vec::new();
        let mut entries: Vec<(Vec<u8>, StoredCustody)> = Vec::new();
        for item in self.tree()?.iter() {
            let (k, v) = item?;
            entries.push((k.to_vec(), bincode::deserialize(&v)?));
        }
        entries.sort_by(|a, b| a.0.cmp(&b.0));
        for (k, mut stored) in entries {
            if out.len() >= max {
                break;
            }
            if !Self::relay_allowed(&stored, now) {
                continue;
            }
            let mode = Self::forward_mode(&stored, local_peer);
            if matches!(mode, ForwardMode::OriginSpray) && !Self::origin_spray_budget(&stored) {
                continue;
            }
            if stored.targets_tried.contains(encounter_peer) {
                continue;
            }
            if !Self::peer_allowed(&stored, encounter_peer, local_peer) {
                continue;
            }
            stored.targets_tried.insert(encounter_peer.to_string());
            let outbound = Self::prepare_outbound(&mut stored, local_peer);
            if matches!(mode, ForwardMode::OriginSpray) {
                self.tree()?.insert(k, bincode::serialize(&stored)?)?;
            } else {
                self.tree()?.remove(k)?;
            }
            out.push(CustodyForwardPlan {
                message_id: stored.message.envelope.id.0.clone(),
                to_peer: encounter_peer.to_string(),
                message: outbound,
            });
        }
        Ok(out)
    }

    pub fn eligible_for_custody(message: &DirectMessage) -> bool {
        message.envelope.custody || message.envelope.to_peer.is_some()
    }

    /// Network copy budget for origin spray phase (not per-hop relay).
    pub fn spray_budget_remaining(message: &DirectMessage) -> bool {
        message.envelope.custody_spray_sent < message.envelope.max_custody_spray
    }

    pub fn mark_delivered_to_local(&self, message_id: &str) -> anyhow::Result<()> {
        self.remove(message_id)
    }

    fn forward_mode(stored: &StoredCustody, local_peer: &str) -> ForwardMode {
        if Self::is_origin(stored, local_peer) && Self::origin_spray_budget(stored) {
            ForwardMode::OriginSpray
        } else {
            ForwardMode::Transfer
        }
    }

    fn is_origin(stored: &StoredCustody, local_peer: &str) -> bool {
        stored.message.envelope.from_peer == local_peer
    }

    fn origin_spray_budget(stored: &StoredCustody) -> bool {
        stored.sprays_released < stored.message.envelope.max_custody_spray
    }

    fn relay_allowed(stored: &StoredCustody, now: u64) -> bool {
        !stored.message.is_expired(now)
            && stored.message.envelope.hop_count < stored.message.envelope.max_hops
    }

    fn peer_allowed(stored: &StoredCustody, peer: &str, local_peer: &str) -> bool {
        if peer == local_peer {
            return false;
        }
        if peer == stored.message.envelope.from_peer {
            return false;
        }
        if stored
            .message
            .envelope
            .last_custody_from
            .as_deref()
            .is_some_and(|prev| prev == peer)
        {
            return false;
        }
        true
    }

    /// Origin spray: duplicate copy. Otherwise: hand off local custody (transfer).
    fn prepare_outbound(stored: &mut StoredCustody, local_peer: &str) -> DirectMessage {
        let mut outbound = stored.message.clone();
        outbound.envelope.custody = true;
        outbound.envelope.hop_count = outbound.envelope.hop_count.saturating_add(1);
        outbound.envelope.last_custody_from = Some(local_peer.to_string());
        if Self::is_origin(stored, local_peer) && Self::origin_spray_budget(stored) {
            stored.sprays_released = stored.sprays_released.saturating_add(1);
            outbound.envelope.custody_spray_sent = outbound.envelope.max_custody_spray;
        }
        outbound
    }

    fn tree(&self) -> anyhow::Result<sled::Tree> {
        Ok(self.db.open_tree("custody_queue")?)
    }
}

enum ForwardMode {
    OriginSpray,
    Transfer,
}

#[cfg(test)]
mod tests {    #![allow(clippy::unwrap_used, clippy::expect_used)]

    use super::*;
    use mycelium_core::data::Envelope;
    use tempfile::tempdir;

    fn custody_msg(from: &str, to: &str) -> DirectMessage {
        let mut env = Envelope::new(from.to_string(), Some(to.to_string()), b"dtm".to_vec());
        env.custody = true;
        env.max_custody_spray = 3;
        DirectMessage {
            envelope: env,
            body: "dtm".into(),
        }
    }

    #[test]
    fn enqueue_respects_spray_budget() {
        let dir = tempdir().unwrap();
        let q = CustodyQueue::open(dir.path().to_str().unwrap(), CustodyConfig::default()).unwrap();
        let msg = custody_msg("a", "z");
        assert!(q.enqueue(&msg).unwrap());
        assert!(!q.enqueue(&msg).unwrap());
    }

    #[test]
    fn forward_plan_respects_tried_peers() {
        let dir = tempdir().unwrap();
        let log = EncounterLogStore::open(dir.path().to_str().unwrap()).unwrap();
        let q = CustodyQueue::open(dir.path().to_str().unwrap(), CustodyConfig::default()).unwrap();
        log.begin_encounter("b", 1).unwrap();
        log.end_encounter("b", 2).unwrap();
        let msg = custody_msg("a", "z");
        q.enqueue(&msg).unwrap();
        let plan = q
            .next_forward_plan("a", &["b".into()], &log)
            .unwrap()
            .expect("plan");
        assert_eq!(plan.to_peer, "b");
        assert_eq!(plan.message.envelope.custody_spray_sent, 3);
        assert_eq!(plan.message.envelope.hop_count, 1);
        assert!(q
            .next_forward_plan("a", &["b".into()], &log)
            .unwrap()
            .is_none());
    }

    #[test]
    fn carrier_relay_uses_hop_budget_not_spray_budget() {
        let dir = tempdir().unwrap();
        let q = CustodyQueue::open(dir.path().to_str().unwrap(), CustodyConfig::default()).unwrap();
        let mut msg = custody_msg("a", "z");
        msg.envelope.custody_spray_sent = 3;
        q.enqueue(&msg).unwrap();

        let plans = q.plans_for_encounter("b", "c", 1).unwrap();
        assert_eq!(plans.len(), 1);
        assert_eq!(plans[0].message.envelope.hop_count, 1);
        assert_eq!(plans[0].message.envelope.custody_spray_sent, 3);
        assert_eq!(
            q.len().unwrap(),
            0,
            "carrier relinquishes custody on handoff"
        );
    }

    #[test]
    fn origin_relays_after_spray_budget_exhausted() {
        let dir = tempdir().unwrap();
        let q = CustodyQueue::open(dir.path().to_str().unwrap(), CustodyConfig::default()).unwrap();
        let mut msg = custody_msg("a", "z");
        msg.envelope.max_custody_spray = 1;
        q.enqueue(&msg).unwrap();
        let spray = q.plans_for_encounter("a", "b", 1).unwrap();
        assert_eq!(spray.len(), 1);
        assert_eq!(q.len().unwrap(), 1, "origin keeps copy after spray");
        let relay = q.plans_for_encounter("a", "c", 1).unwrap();
        assert_eq!(relay.len(), 1);
        assert_eq!(relay[0].to_peer, "c");
        assert_eq!(q.len().unwrap(), 0, "origin transfers after spray phase");
    }

    #[test]
    fn skips_immediate_custody_ping_pong() {
        let dir = tempdir().unwrap();
        let q = CustodyQueue::open(dir.path().to_str().unwrap(), CustodyConfig::default()).unwrap();
        let mut msg = custody_msg("a", "z");
        msg.envelope.last_custody_from = Some("c".into());
        q.enqueue(&msg).unwrap();
        assert!(q.plans_for_encounter("b", "c", 1).unwrap().is_empty());
        let plan = q.plans_for_encounter("b", "d", 1).unwrap();
        assert_eq!(plan.len(), 1);
        assert_eq!(plan[0].to_peer, "d");
    }
}
