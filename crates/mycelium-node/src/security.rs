// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! Network-layer security helpers (SD-030, SD-031).

use libp2p::PeerId;
use mycelium_core::transport::DirectMessage;
use std::collections::HashMap;

pub use crate::peer_trust::{PeerReputationSnapshot, PeerTrust, PeerTrustStore};

/// Unsigned `WireMessage::Data` grace deadline (ms). `0` = disabled — unsigned messages are always rejected (SD-030).
pub const UNSIGNED_GRACE_PERIOD_UNTIL_MS: u64 = 0;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Sd030DropReason {
    NoSignature,
    InvalidSignature,
    UnparseableAuthor,
}

/// Gossip relay bodies are unsigned by design: inner payload carries its own auth
/// (E2E group, signed store listing, enc1 bulletin). Only these exact tags bypass SD-030.
pub fn is_gossip_relay_body(body: &str) -> bool {
    body == "[mycelium:group]" || body == "[appstore]" || body.starts_with("[bulletin:")
}

/// Returns `Ok(())` when the message may be processed; `Err` when it must be dropped.
pub fn validate_data_message_signature(
    message: &DirectMessage,
    _now_ms: u64,
) -> Result<(), Sd030DropReason> {
    if is_gossip_relay_body(&message.body) {
        return Ok(());
    }
    match &message.envelope.signature {
        None => Err(Sd030DropReason::NoSignature),
        Some(_) => {
            let author = message.envelope.signature_author_peer();
            let Ok(author_id) = author.parse::<PeerId>() else {
                return Err(Sd030DropReason::UnparseableAuthor);
            };
            if message.envelope.verify(&author_id) {
                Ok(())
            } else {
                Err(Sd030DropReason::InvalidSignature)
            }
        }
    }
}

/// SD-031: pick up to `max_fanout` relay peers by weighted trust score (Sybil-resistant).
///
/// `endorsement_boost`: local social trust from [`UserTrustStore`](../../mycelium-coin/src/user_trust.rs)
/// (0..1 per peer). Combined with [`PeerTrust::relay_selection_score`] — separate attack surfaces.
pub fn select_relay_candidates(
    peers: &[String],
    to_peer: &str,
    peer_trust: &HashMap<String, PeerTrust>,
    endorsement_boost: Option<&HashMap<String, f32>>,
    now_ms: u64,
    max_fanout: usize,
) -> Vec<String> {
    const ENDORSEMENT_WEIGHT: f64 = 0.15;
    let mut scored: Vec<(String, f64)> = peers
        .iter()
        .filter(|p| *p != to_peer)
        .map(|p| {
            let trust = peer_trust
                .get(p)
                .cloned()
                .unwrap_or_else(|| PeerTrust::cold_unknown(now_ms));
            let mut score = trust.relay_selection_score(now_ms);
            if let Some(boosts) = endorsement_boost {
                if let Some(b) = boosts.get(p) {
                    score += ENDORSEMENT_WEIGHT * f64::from(*b);
                }
            }
            (p.clone(), score)
        })
        .collect();
    scored.sort_by(|a, b| {
        b.1.partial_cmp(&a.1)
            .unwrap_or(std::cmp::Ordering::Equal)
            .then_with(|| a.0.cmp(&b.0))
    });
    scored
        .into_iter()
        .take(max_fanout)
        .map(|(peer, _)| peer)
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use libp2p::identity;
    use mycelium_core::data::Envelope;

    fn sample_message(signed: bool, body: &str) -> DirectMessage {
        let kp = identity::Keypair::generate_ed25519();
        let author = kp.public().to_peer_id().to_string();
        let mut envelope = Envelope::new(author, Some("dest".into()), b"payload".to_vec());
        if signed {
            envelope.sign(&kp).unwrap();
        } else {
            envelope.signature = None;
        }
        DirectMessage {
            envelope,
            body: body.into(),
        }
    }

    fn established_trust(
        now_ms: u64,
        reliability: f32,
        relay_rate: f32,
        age_days: u64,
    ) -> PeerTrust {
        PeerTrust {
            first_seen_ms: now_ms.saturating_sub(age_days * 86_400_000),
            reliability,
            spam_score: 0.02,
            relay_success_rate: relay_rate,
            relay_success_count: 100,
            relay_failure_count: 5,
            mutual_contacts: 2,
            messages_accepted: 500,
            messages_rejected: 2,
            strike_count: 0,
            last_strike_ms: 0,
            throttled_until_ms: 0,
            last_updated_ms: now_ms,
        }
    }

    #[test]
    fn unsigned_dropped_after_grace_period() {
        let msg = sample_message(false, "hello");
        let after_grace = UNSIGNED_GRACE_PERIOD_UNTIL_MS + 1;
        assert_eq!(
            validate_data_message_signature(&msg, after_grace),
            Err(Sd030DropReason::NoSignature)
        );
    }

    #[test]
    fn unsigned_rejected_immediately() {
        let msg = sample_message(false, "hello");
        assert_eq!(
            validate_data_message_signature(&msg, 0),
            Err(Sd030DropReason::NoSignature)
        );
    }

    #[test]
    fn gossip_relay_body_skips_sig_check() {
        let msg = sample_message(false, "[mycelium:group]");
        let after_grace = UNSIGNED_GRACE_PERIOD_UNTIL_MS + 1;
        assert!(validate_data_message_signature(&msg, after_grace).is_ok());
    }

    #[test]
    fn invalid_sig_is_dropped() {
        let kp_a = identity::Keypair::generate_ed25519();
        let kp_b = identity::Keypair::generate_ed25519();
        let author_b = kp_b.public().to_peer_id().to_string();
        let mut envelope = Envelope::new(author_b, Some("dest".into()), b"payload".to_vec());
        envelope.sign(&kp_a).unwrap();
        let msg = DirectMessage {
            envelope,
            body: "hello".into(),
        };
        assert_eq!(
            validate_data_message_signature(&msg, 0),
            Err(Sd030DropReason::InvalidSignature)
        );
    }

    #[test]
    fn fanout_is_bounded() {
        let peers: Vec<String> = (0..10).map(|i| format!("peer{i}")).collect();
        let now = 1_700_000_000_000u64;
        let relay = select_relay_candidates(&peers, "unknown", &HashMap::new(), None, now, 3);
        assert_eq!(relay.len(), 3);
        assert!(!relay.iter().any(|p| p == "unknown"));
    }

    #[test]
    fn high_trust_peers_preferred_over_spammers() {
        let now = 1_700_000_000_000u64;
        let peers = vec![
            "good_a".into(),
            "good_b".into(),
            "bad_a".into(),
            "bad_b".into(),
        ];
        let mut trust = HashMap::new();
        trust.insert("good_a".into(), established_trust(now, 0.95, 0.92, 30));
        trust.insert("good_b".into(), established_trust(now, 0.90, 0.88, 14));
        let mut bad = established_trust(now, 0.2, 0.15, 30);
        bad.spam_score = 0.85;
        bad.strike_count = 6;
        trust.insert("bad_a".into(), bad.clone());
        trust.insert("bad_b".into(), bad);
        let selected = select_relay_candidates(&peers, "dest", &trust, None, now, 2);
        assert_eq!(selected.len(), 2);
        assert!(selected.contains(&"good_a".to_string()));
        assert!(selected.contains(&"good_b".to_string()));
    }

    /// Property-style: a Sybil swarm of fresh peers must not outrank established reliable peers.
    #[test]
    fn sybil_swarm_not_preferred_over_established() {
        let now = 1_700_000_000_000u64;
        let mut trust = HashMap::new();
        let mut peers = Vec::new();

        for i in 0..3 {
            let id = format!("12D3KooWEstablished{i:02}");
            peers.push(id.clone());
            trust.insert(id, established_trust(now, 0.93, 0.90, 21));
        }
        for i in 0..25 {
            let id = format!("12D3KooWSybil{i:02}");
            peers.push(id.clone());
            trust.insert(id, PeerTrust::cold_unknown(now));
        }

        for fanout in 1..=3 {
            let selected = select_relay_candidates(&peers, "12D3KooWDest", &trust, None, now, fanout);
            assert_eq!(selected.len(), fanout);
            assert!(
                selected
                    .iter()
                    .all(|p| p.starts_with("12D3KooWEstablished")),
                "fanout {fanout} picked sybil: {selected:?}"
            );
        }
    }

    /// Randomized property: adding more Sybil peers must not displace top established picks.
    #[test]
    fn sybil_property_more_sybls_still_lose() {
        let now = 1_700_000_000_000u64;
        let mut trust = HashMap::new();
        let mut peers = Vec::new();
        for i in 0..5 {
            let id = format!("est_{i}");
            peers.push(id.clone());
            trust.insert(id, established_trust(now, 0.91, 0.89, 10 + i as u64));
        }

        for sybil_count in [5, 15, 40, 100] {
            let mut trial_peers = peers.clone();
            let mut trial_trust = trust.clone();
            for i in 0..sybil_count {
                let id = format!("sybil_{sybil_count}_{i}");
                trial_peers.push(id.clone());
                trial_trust.insert(id, PeerTrust::cold_unknown(now));
            }
            let selected = select_relay_candidates(&trial_peers, "target", &trial_trust, None, now, 3);
            assert_eq!(selected.len(), 3);
            let established_in_top: usize =
                selected.iter().filter(|p| p.starts_with("est_")).count();
            assert_eq!(
                established_in_top, 3,
                "sybil_count={sybil_count} selected={selected:?}"
            );
        }
    }

    #[test]
    fn local_endorsement_boosts_relay_score() {
        let now = 1_700_000_000_000u64;
        let peers = vec!["endorsed".into(), "plain".into()];
        let mut trust = HashMap::new();
        trust.insert("endorsed".into(), PeerTrust::cold_unknown(now));
        trust.insert("plain".into(), PeerTrust::cold_unknown(now));
        let mut boosts = HashMap::new();
        boosts.insert("endorsed".to_string(), 1.0f32);
        let selected = select_relay_candidates(&peers, "dest", &trust, Some(&boosts), now, 1);
        assert_eq!(selected, vec!["endorsed".to_string()]);
    }
}
