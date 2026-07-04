// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! Geohash-prefix gossip topic sharding (AR-06) — avoids one global mesh topic.

use crate::sos::{geohash_from_payload_bytes, SOS_SCOPE};

const GEO_SEGMENT: &str = "g";

/// Default geohash prefix length for gossip shards (`MYCELIUM_GEOHASH_SHARD_LEN`).
pub const DEFAULT_SHARD_PREFIX_LEN: usize = 4;

/// Read shard prefix length from env (4–8).
pub fn shard_prefix_len() -> usize {
    std::env::var("MYCELIUM_GEOHASH_SHARD_LEN")
        .ok()
        .and_then(|v| v.parse().ok())
        .unwrap_or(DEFAULT_SHARD_PREFIX_LEN)
        .clamp(4, 8)
}

/// Scopes that shard by geohash prefix instead of a single global topic.
pub fn supports_geohash_sharding(base_scope: &str) -> bool {
    base_scope == SOS_SCOPE || base_scope.starts_with("mycelium/bulletin/")
}

/// Normalize a geohash to the gossip shard prefix.
pub fn shard_prefix(geohash: &str) -> Option<String> {
    let gh = geohash.trim();
    if gh.len() < 4 {
        return None;
    }
    if !gh.chars().all(|c| {
        matches!(c, '0'..='9' | 'b'..='h' | 'j'..='n' | 'p'..='z')
    }) {
        return None;
    }
    let len = shard_prefix_len().min(gh.len());
    Some(gh[..len].to_string())
}

/// Gossip topic string: `{base}/g/{prefix}`.
pub fn gossip_topic(base_scope: &str, geohash: &str) -> Option<String> {
    let prefix = shard_prefix(geohash)?;
    Some(format!("{base_scope}/{GEO_SEGMENT}/{prefix}"))
}

/// Pattern stored in subscribed_scopes for wildcard ingest matching.
pub fn subscribe_pattern(base_scope: &str) -> String {
    format!("{base_scope}/{GEO_SEGMENT}/*")
}

/// Resolve gossip publish topics for a scoped payload.
pub fn resolve_publish_topics(base_scope: &str, payload: &[u8]) -> Vec<String> {
    if supports_geohash_sharding(base_scope) {
        if let Some(gh) = geohash_from_payload_bytes(payload)
            .or_else(|| extract_geohash_field(payload))
        {
            if let Some(topic) = gossip_topic(base_scope, &gh) {
                return vec![topic];
            }
        }
    }
    vec![base_scope.to_string()]
}

/// Gossip topics to join when subscribing to a location-aware scope.
pub fn resolve_subscribe_topics(base_scope: &str, local_geohash: Option<&str>) -> Vec<String> {
    if !supports_geohash_sharding(base_scope) {
        return vec![base_scope.to_string()];
    }
    let Some(gh) = local_geohash.map(str::trim).filter(|s| !s.is_empty()) else {
        return Vec::new();
    };
    gossip_topic(base_scope, gh).into_iter().collect()
}

fn extract_geohash_field(payload: &[u8]) -> Option<String> {
    let v: serde_json::Value = serde_json::from_slice(payload).ok()?;
    v.get("geohash")?.as_str().map(str::to_string)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sos_shards_by_geohash_prefix() {
        let topic = gossip_topic(SOS_SCOPE, "u4pruyd").expect("topic");
        assert_eq!(topic, "mycelium/sos/v1/g/u4pr");
    }

    #[test]
    fn publish_resolves_shard_from_json() {
        let payload = br#"{"geohash":"u4pru","message":"help","created_at_ms":1}"#;
        let topics = resolve_publish_topics(SOS_SCOPE, payload);
        assert_eq!(topics, vec!["mycelium/sos/v1/g/u4pr".to_string()]);
    }

    #[test]
    fn subscribe_requires_local_geohash() {
        assert!(resolve_subscribe_topics(SOS_SCOPE, None).is_empty());
        assert_eq!(
            resolve_subscribe_topics(SOS_SCOPE, Some("u4pru")),
            vec!["mycelium/sos/v1/g/u4pr".to_string()]
        );
    }

    #[test]
    fn non_sharded_scope_stays_global() {
        let topics = resolve_publish_topics("mycelium/chat", b"hello");
        assert_eq!(topics, vec!["mycelium/chat".to_string()]);
    }
}
