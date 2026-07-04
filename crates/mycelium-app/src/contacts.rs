// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
use mycelium_l0_facade::data::now_ms;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum ContactStatus {
    Pending,
    Accepted,
    Blocked,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Contact {
    pub peer_id: String,
    pub display_name: String,
    pub added_at_ms: u64,
    pub status: ContactStatus,
    /// Stable account id from signed invite / identity rotation (SD-190).
    #[serde(default)]
    pub account_id: Option<String>,
    /// Recent transport peer ids during rotation transition (SD-190 dial window).
    #[serde(default)]
    pub previous_peer_ids: Vec<String>,
}

impl Contact {
    pub fn new(peer_id: String, display_name: String, status: ContactStatus) -> Self {
        Self {
            peer_id,
            display_name,
            added_at_ms: now_ms(),
            status,
            account_id: None,
            previous_peer_ids: Vec::new(),
        }
    }
}
