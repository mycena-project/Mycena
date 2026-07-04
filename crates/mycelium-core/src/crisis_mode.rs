// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! **Crisis Mode** — emergency traffic prioritization when infrastructure fails.
//!
//! Manual activation or auto when internet is fully unavailable and nearby SOS
//! traffic is observed (see [`evaluate_auto_activation`]).

use serde::{Deserialize, Serialize};

/// Tunable crisis runtime (applied by `mycelium-node` when active).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct CrisisRuntimeParams {
    /// Forwarding tick interval (ms); lower = faster relay retries.
    pub forwarding_interval_ms: u64,
    /// Bloom anti-entropy interval (secs); higher = less background sync.
    pub sync_interval_secs: u64,
    /// Extra relay fanout for emergency traffic.
    pub relay_fanout_boost: usize,
    /// `max_hops` for SOS / resource-map envelopes.
    pub emergency_max_hops: u8,
    /// Privacy-shaping batch window (ms) — smaller batches in crisis.
    pub batch_min_ms: u64,
    pub batch_max_ms: u64,
    /// Messages drained per forward tick when node is Active.
    pub forward_budget_active: usize,
}

impl Default for CrisisRuntimeParams {
    fn default() -> Self {
        Self {
            forwarding_interval_ms: 150,
            sync_interval_secs: 300,
            relay_fanout_boost: 2,
            emergency_max_hops: 16,
            batch_min_ms: 200,
            batch_max_ms: 800,
            forward_budget_active: 16,
        }
    }
}

/// Baseline intervals when crisis mode is off (node defaults).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct CrisisBaseline {
    pub forwarding_interval_ms: u64,
    pub sync_interval_secs: u64,
    pub max_relay_fanout: usize,
}

impl CrisisRuntimeParams {
    pub fn from_baseline(baseline: CrisisBaseline) -> Self {
        let mut p = Self::default();
        p.forwarding_interval_ms = p.forwarding_interval_ms.min(baseline.forwarding_interval_ms);
        p
    }

    pub fn effective_relay_fanout(&self, baseline: usize) -> usize {
        (baseline + self.relay_fanout_boost).min(8)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct CrisisAutoConfig {
    /// SOS posts within this window (ms) count as “nearby activity”.
    pub sos_activity_window_ms: u64,
    /// Minimum geohash prefix overlap (chars) for “nearby” SOS.
    pub geohash_prefix_len: usize,
}

impl Default for CrisisAutoConfig {
    fn default() -> Self {
        Self {
            sos_activity_window_ms: 15 * 60 * 1000,
            geohash_prefix_len: 4,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum CrisisManualMode {
    FollowAuto,
    ForceOn,
    ForceOff,
}

impl Default for CrisisManualMode {
    fn default() -> Self {
        Self::FollowAuto
    }
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct CrisisModeState {
    pub manual: CrisisManualMode,
    pub auto_active: bool,
}

impl CrisisModeState {
    pub fn is_active(&self) -> bool {
        match self.manual {
            CrisisManualMode::ForceOn => true,
            CrisisManualMode::ForceOff => false,
            CrisisManualMode::FollowAuto => self.auto_active,
        }
    }
}

/// Auto-activate when internet is down, user listens for SOS, and nearby SOS exists.
pub fn evaluate_auto_activation(
    _config: &CrisisAutoConfig,
    internet_reachable: bool,
    listen_sos: bool,
    nearby_sos_recent: bool,
) -> bool {
    !internet_reachable && listen_sos && nearby_sos_recent
}

/// True when `sos_geohash` is “near” `local_prefix` (shared prefix).
pub fn geohash_nearby(local_prefix: &str, sos_geohash: &str, prefix_len: usize) -> bool {
    let local = local_prefix.trim();
    let sos = sos_geohash.trim();
    if local.is_empty() || sos.is_empty() || prefix_len == 0 {
        return false;
    }
    let n = prefix_len.min(local.len()).min(sos.len());
    local.as_bytes()[..n] == sos.as_bytes()[..n]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn manual_override_wins() {
        let mut s = CrisisModeState {
            manual: CrisisManualMode::ForceOn,
            auto_active: false,
        };
        assert!(s.is_active());
        s.manual = CrisisManualMode::ForceOff;
        assert!(!s.is_active());
    }

    #[test]
    fn auto_requires_all_signals() {
        let cfg = CrisisAutoConfig::default();
        assert!(!evaluate_auto_activation(&cfg, true, true, true));
        assert!(!evaluate_auto_activation(&cfg, false, false, true));
        assert!(evaluate_auto_activation(&cfg, false, true, true));
    }

    #[test]
    fn geohash_prefix_match() {
        assert!(geohash_nearby("u4pr", "u4pruyd", 4));
        assert!(!geohash_nearby("u4pr", "u5xxx", 4));
    }
}
