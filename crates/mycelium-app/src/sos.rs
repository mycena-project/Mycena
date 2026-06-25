// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! Emergency SOS scope helpers (Phase C3).

pub const SOS_SCOPE: &str = "mycelium/sos/v1";
pub const SOS_MAX_MESSAGE_LEN: usize = 280;
pub const SOS_MAX_GEOHASH_LEN: usize = 12;
pub const SOS_MIN_GEOHASH_LEN: usize = 4;

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct SosPayload {
    pub geohash: String,
    pub message: String,
    pub created_at_ms: u64,
}

impl SosPayload {
    pub fn validate(&self) -> anyhow::Result<()> {
        let gh = self.geohash.trim();
        if gh.len() < SOS_MIN_GEOHASH_LEN || gh.len() > SOS_MAX_GEOHASH_LEN {
            anyhow::bail!("geohash length must be {SOS_MIN_GEOHASH_LEN}–{SOS_MAX_GEOHASH_LEN}");
        }
        if !gh
            .chars()
            .all(|c| matches!(c, '0'..='9' | 'b'..='h' | 'j'..='n' | 'p'..='z'))
        {
            anyhow::bail!("invalid geohash characters");
        }
        let msg = self.message.trim();
        if msg.is_empty() || msg.len() > SOS_MAX_MESSAGE_LEN {
            anyhow::bail!("message must be 1–{SOS_MAX_MESSAGE_LEN} chars");
        }
        Ok(())
    }

    pub fn encode_json(&self) -> anyhow::Result<String> {
        Ok(serde_json::to_string(self)?)
    }

    pub fn decode_json(raw: &str) -> anyhow::Result<Self> {
        let payload: Self = serde_json::from_str(raw)?;
        payload.validate()?;
        Ok(payload)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sos_payload_validates() {
        let ok = SosPayload {
            geohash: "u4pru".into(),
            message: "Need help".into(),
            created_at_ms: 1,
        };
        ok.validate().unwrap();
        let bad = SosPayload {
            geohash: "!!!".into(),
            message: "".into(),
            created_at_ms: 1,
        };
        assert!(bad.validate().is_err());
    }
}
