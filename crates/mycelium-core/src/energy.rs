// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, Serialize, Deserialize, Eq, PartialEq)]
pub enum NodeState {
    Active,
    Intermittent,
    Passive,
}

impl NodeState {
    pub fn sync_interval_secs(self) -> u64 {
        match self {
            Self::Active => 2,
            Self::Intermittent => 10,
            Self::Passive => 45,
        }
    }

    /// Whether optional Shade cover-traffic may be emitted (SD-070).
    pub fn allows_cover_traffic(self) -> bool {
        !matches!(self, Self::Passive)
    }

    /// Multiplier on base cover interval (Passive → disabled via 0).
    pub fn cover_traffic_interval_multiplier(self) -> u64 {
        match self {
            Self::Active => 1,
            Self::Intermittent => 2,
            Self::Passive => 0,
        }
    }
}

#[cfg(test)]
mod tests {    #![allow(clippy::unwrap_used, clippy::expect_used)]

    use super::NodeState;

    #[test]
    fn cover_traffic_gated_by_energy() {
        assert!(NodeState::Active.allows_cover_traffic());
        assert!(NodeState::Intermittent.allows_cover_traffic());
        assert!(!NodeState::Passive.allows_cover_traffic());
        assert_eq!(NodeState::Passive.cover_traffic_interval_multiplier(), 0);
    }
}
