// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! Local encounter history for DTN custody scheduling (privacy: device-local, TTL-pruned).

use mycelium_core::lock_or_recover;
use mycelium_core::data::now_ms;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::Path;
use std::sync::Mutex;

/// Default retention for encounter records (7 days).
pub const ENCOUNTER_LOG_TTL_MS: u64 = 7 * 24 * 60 * 60 * 1000;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct EncounterRecord {
    pub peer_id: String,
    pub first_seen_ms: u64,
    pub last_seen_ms: u64,
    /// Cumulative connection time across encounters.
    pub total_duration_ms: u64,
    pub encounter_count: u32,
    /// Start of the current open encounter, if connected now.
    #[serde(default)]
    pub open_since_ms: Option<u64>,
}

#[derive(Debug)]
pub struct EncounterLogStore {
    db: sled::Db,
    open: Mutex<HashMap<String, u64>>,
}

impl EncounterLogStore {
    pub fn open(base_path: &str) -> anyhow::Result<Self> {
        Self::open_with_flush(base_path, Some(5000))
    }

    /// Open without background flush (deterministic single-threaded use, e.g. sim).
    pub fn open_sync(base_path: &str) -> anyhow::Result<Self> {
        Self::open_with_flush(base_path, None)
    }

    fn open_with_flush(base_path: &str, flush_every_ms: Option<u64>) -> anyhow::Result<Self> {
        let path = Path::new(base_path).join("encounters");
        std::fs::create_dir_all(&path)?;
        let db = sled::Config::new()
            .path(path)
            .flush_every_ms(flush_every_ms)
            .open()?;
        Ok(Self {
            db,
            open: Mutex::new(HashMap::new()),
        })
    }

    pub fn begin_encounter(&self, peer_id: &str, at_ms: u64) -> anyhow::Result<()> {
        // POISON-RECOVER: open encounter map; sled persist follows in save().
        let mut open = lock_or_recover!(self.open, "encounter open map");
        open.insert(peer_id.to_string(), at_ms);
        let mut rec = self.load_or_new(peer_id, at_ms)?;
        rec.last_seen_ms = at_ms;
        rec.open_since_ms = Some(at_ms);
        rec.encounter_count = rec.encounter_count.saturating_add(1);
        self.save(&rec)
    }

    pub fn end_encounter(&self, peer_id: &str, at_ms: u64) -> anyhow::Result<()> {
        let started = {
            // POISON-RECOVER: remove open marker; duration math uses returned start time only.
            let mut open = lock_or_recover!(self.open, "encounter open map");
            open.remove(peer_id)
        };
        let mut rec = self.load_or_new(peer_id, at_ms)?;
        rec.last_seen_ms = at_ms;
        rec.open_since_ms = None;
        if let Some(s) = started {
            rec.total_duration_ms = rec
                .total_duration_ms
                .saturating_add(at_ms.saturating_sub(s));
        }
        self.save(&rec)
    }

    pub fn touch_encounter(&self, peer_id: &str, at_ms: u64) -> anyhow::Result<()> {
        let mut rec = self.load_or_new(peer_id, at_ms)?;
        rec.last_seen_ms = at_ms;
        self.save(&rec)
    }

    pub fn record(&self, peer_id: &str) -> anyhow::Result<Option<EncounterRecord>> {
        let tree = self.tree()?;
        let Some(bytes) = tree.get(peer_id.as_bytes())? else {
            return Ok(None);
        };
        Ok(Some(bincode::deserialize(&bytes)?))
    }

    pub fn recent_peers(
        &self,
        limit: usize,
        since_ms: u64,
    ) -> anyhow::Result<Vec<EncounterRecord>> {
        let mut out = Vec::new();
        for item in self.tree()?.iter() {
            let (_, v) = item?;
            let rec: EncounterRecord = bincode::deserialize(&v)?;
            if rec.last_seen_ms >= since_ms {
                out.push(rec);
            }
        }
        out.sort_by_key(|r| std::cmp::Reverse(r.last_seen_ms));
        out.truncate(limit);
        Ok(out)
    }

    pub fn custody_candidates(
        &self,
        connected: &[String],
        exclude: &str,
        tried: &[String],
        limit: usize,
    ) -> anyhow::Result<Vec<String>> {
        self.custody_candidates_at(connected, exclude, tried, limit, now_ms())
    }

    /// Peers to try for custody spray — recently met, excluding already tried.
    pub fn custody_candidates_at(
        &self,
        connected: &[String],
        exclude: &str,
        tried: &[String],
        limit: usize,
        now: u64,
    ) -> anyhow::Result<Vec<String>> {
        let since = now.saturating_sub(ENCOUNTER_LOG_TTL_MS);
        let recent = self.recent_peers(connected.len().max(limit).saturating_mul(2), since)?;
        let tried_set: std::collections::HashSet<_> = tried.iter().cloned().collect();
        let connected_set: std::collections::HashSet<_> = connected.iter().cloned().collect();
        let mut out = Vec::new();
        for rec in recent {
            if rec.peer_id == exclude || tried_set.contains(&rec.peer_id) {
                continue;
            }
            if connected_set.contains(&rec.peer_id) {
                out.push(rec.peer_id);
                if out.len() >= limit {
                    break;
                }
            }
        }
        if out.len() < limit {
            for p in connected {
                if p == exclude || tried_set.contains(p) || out.contains(p) {
                    continue;
                }
                out.push(p.clone());
                if out.len() >= limit {
                    break;
                }
            }
        }
        Ok(out)
    }

    pub fn prune_expired(&self, now: u64) -> anyhow::Result<usize> {
        let cutoff = now.saturating_sub(ENCOUNTER_LOG_TTL_MS);
        let mut removed = 0usize;
        for item in self.tree()?.iter() {
            let (k, v) = item?;
            let rec: EncounterRecord = bincode::deserialize(&v)?;
            if rec.last_seen_ms < cutoff {
                self.tree()?.remove(k)?;
                removed += 1;
            }
        }
        Ok(removed)
    }

    fn load_or_new(&self, peer_id: &str, at_ms: u64) -> anyhow::Result<EncounterRecord> {
        Ok(match self.record(peer_id)? {
            Some(r) => r,
            None => EncounterRecord {
                peer_id: peer_id.to_string(),
                first_seen_ms: at_ms,
                last_seen_ms: at_ms,
                total_duration_ms: 0,
                encounter_count: 0,
                open_since_ms: None,
            },
        })
    }

    fn save(&self, rec: &EncounterRecord) -> anyhow::Result<()> {
        self.tree()?
            .insert(rec.peer_id.as_bytes(), bincode::serialize(rec)?)?;
        Ok(())
    }

    fn tree(&self) -> anyhow::Result<sled::Tree> {
        Ok(self.db.open_tree("encounters")?)
    }
}

#[cfg(test)]
mod tests {    #![allow(clippy::unwrap_used, clippy::expect_used)]

    use super::*;
    use tempfile::tempdir;

    #[test]
    fn encounter_duration_accumulates() {
        let dir = tempdir().unwrap();
        let log = EncounterLogStore::open(dir.path().to_str().unwrap()).unwrap();
        log.begin_encounter("peer-a", 1000).unwrap();
        log.end_encounter("peer-a", 5000).unwrap();
        let rec = log.record("peer-a").unwrap().unwrap();
        assert_eq!(rec.total_duration_ms, 4000);
        assert_eq!(rec.encounter_count, 1);
        assert!(rec.open_since_ms.is_none());
    }

    #[test]
    fn custody_candidates_prefers_recent_connected() {
        let dir = tempdir().unwrap();
        let log = EncounterLogStore::open(dir.path().to_str().unwrap()).unwrap();
        let base = now_ms();
        log.begin_encounter("b", base + 100).unwrap();
        log.end_encounter("b", base + 200).unwrap();
        log.begin_encounter("c", base + 300).unwrap();
        log.end_encounter("c", base + 400).unwrap();
        let cands = log
            .custody_candidates(&["b".into(), "c".into()], "self", &[], 1)
            .unwrap();
        assert_eq!(cands.len(), 1);
        assert_eq!(cands[0], "c");
    }
}
