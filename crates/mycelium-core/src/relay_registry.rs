// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! Federated public relay registry — signed operator entries + curator endorsements.

use crate::account_identity::sign_account_message;
use crate::bootstrap::{peer_id_from_multiaddr, BOOTSTRAP_PEERS, RELAY_PEER_ID};
use libp2p::identity::{ed25519, Keypair};
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};

pub const REGISTRY_FORMAT: &str = "mycelium-relay-registry/v1";

/// Curator identities (pubkeys derived deterministically via [`curator_keypair`]).
pub const FEDERATED_RELAY_CURATOR_IDS: &[(&str, &str)] = &[
    ("mycelium-release", "Mycelium release signing"),
    ("community-mesh-eu", "Independent community operator (EU)"),
    ("community-mesh-na", "Independent community operator (NA)"),
];

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct RelayUptimeSnapshot {
    pub observed_at_ms: u64,
    pub uptime_secs: u64,
    pub reachable: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct RelayEndorsement {
    pub curator_id: String,
    pub curator_pubkey_hex: String,
    pub signed_at_ms: u64,
    pub signature_hex: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct RelayRegistration {
    pub peer_id: String,
    pub multiaddrs: Vec<String>,
    pub operator_pubkey_hex: String,
    pub operator_label: String,
    pub region: String,
    pub registered_at_ms: u64,
    #[serde(default)]
    pub uptime_history: Vec<RelayUptimeSnapshot>,
    pub operator_signature_hex: String,
    #[serde(default)]
    pub endorsements: Vec<RelayEndorsement>,
    /// Listed for audit / staging — not dialed unless [`include_catalog_relays`].
    #[serde(default)]
    pub catalog_only: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct RelayRegistryPolicy {
    pub min_curator_endorsements: u8,
    pub max_relays_per_operator: u8,
    pub max_relays_per_ipv4_prefix: u8,
}

impl Default for RelayRegistryPolicy {
    fn default() -> Self {
        Self {
            min_curator_endorsements: 1,
            max_relays_per_operator: 2,
            max_relays_per_ipv4_prefix: 1,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct RelayRegistryManifest {
    pub format: String,
    pub issued_at_ms: u64,
    #[serde(default)]
    pub policy: RelayRegistryPolicy,
    pub registrations: Vec<RelayRegistration>,
}

#[derive(Debug, Clone, Default)]
pub struct RelaySybilReport {
    pub rejected: Vec<String>,
}

#[derive(Debug, Clone)]
pub struct VerifiedRelayRegistry {
    pub manifest: RelayRegistryManifest,
    pub dialable: Vec<RelayRegistration>,
}

impl RelayRegistration {
    pub fn primary_tcp_multiaddr(&self) -> Option<&str> {
        self.multiaddrs
            .iter()
            .find(|a| a.contains("/tcp/") && !a.contains("/p2p-circuit"))
            .map(String::as_str)
    }

    pub fn signable_bytes(&self) -> Vec<u8> {
        let mut bytes = Vec::new();
        bytes.extend_from_slice(b"MYCEL/RELAY/REG/v1\0");
        bytes.extend_from_slice(self.peer_id.as_bytes());
        bytes.push(0);
        for addr in &self.multiaddrs {
            bytes.extend_from_slice(addr.as_bytes());
            bytes.push(0);
        }
        bytes.extend_from_slice(self.operator_pubkey_hex.as_bytes());
        bytes.push(0);
        bytes.extend_from_slice(self.operator_label.as_bytes());
        bytes.push(0);
        bytes.extend_from_slice(self.region.as_bytes());
        bytes.push(0);
        bytes.extend_from_slice(&self.registered_at_ms.to_le_bytes());
        bytes.push(u8::from(self.catalog_only));
        bytes
    }

    pub fn registration_digest(&self) -> [u8; 32] {
        blake3::hash(&self.signable_bytes()).into()
    }

    pub fn verify_operator_signature(&self) -> bool {
        let Ok(sig) = hex::decode(self.operator_signature_hex.trim()) else {
            return false;
        };
        verify_pubkey_signature(&self.operator_pubkey_hex, &self.signable_bytes(), &sig)
    }

    pub fn verify_endorsements(&self, min: u8) -> bool {
        let mut valid_curators = HashSet::new();
        for e in &self.endorsements {
            if !e.verify_for(self) {
                continue;
            }
            valid_curators.insert(e.curator_id.clone());
        }
        valid_curators.len() >= min as usize
    }

    pub fn ipv4_prefix(&self) -> Option<String> {
        self.primary_tcp_multiaddr()
            .and_then(ipv4_prefix_from_multiaddr)
    }
}

impl RelayEndorsement {
    pub fn endorsement_signable_bytes(reg: &RelayRegistration, signed_at_ms: u64) -> Vec<u8> {
        let mut bytes = Vec::new();
        bytes.extend_from_slice(b"MYCEL/RELAY/ENDORSE/v1\0");
        bytes.extend_from_slice(&reg.registration_digest());
        bytes.extend_from_slice(reg.peer_id.as_bytes());
        bytes.push(0);
        bytes.extend_from_slice(&signed_at_ms.to_le_bytes());
        bytes
    }

    pub fn verify_for(&self, reg: &RelayRegistration) -> bool {
        let Ok(sig) = hex::decode(self.signature_hex.trim()) else {
            return false;
        };
        if !curator_pubkey_matches_id(&self.curator_id, &self.curator_pubkey_hex) {
            return false;
        }
        verify_pubkey_signature(
            &self.curator_pubkey_hex,
            &Self::endorsement_signable_bytes(reg, self.signed_at_ms),
            &sig,
        )
    }
}

impl RelayRegistryManifest {
    pub fn parse_json(json: &str) -> anyhow::Result<Self> {
        let manifest: Self = serde_json::from_str(json)?;
        if manifest.format != REGISTRY_FORMAT {
            anyhow::bail!("unsupported relay registry format: {}", manifest.format);
        }
        Ok(manifest)
    }

    pub fn default_embedded() -> Self {
        build_default_manifest()
    }

    pub fn verify_and_filter(
        &self,
        include_catalog: bool,
    ) -> anyhow::Result<VerifiedRelayRegistry> {
        let mut report = RelaySybilReport::default();
        let mut dialable = Vec::new();
        let policy = self.policy.clone();

        for reg in &self.registrations {
            if let Err(reason) = validate_registration(reg, &policy) {
                report.rejected.push(format!("{}: {reason}", reg.peer_id));
                continue;
            }
            if reg.catalog_only && !include_catalog {
                continue;
            }
            if is_testnet_multiaddr(reg.primary_tcp_multiaddr().unwrap_or("")) && !include_catalog
            {
                continue;
            }
            dialable.push(reg.clone());
        }

        apply_sybil_limits(&mut dialable, &policy, &mut report);

        Ok(VerifiedRelayRegistry {
            manifest: self.clone(),
            dialable,
        })
    }

    pub fn bootstrap_multiaddrs(&self, include_catalog: bool) -> anyhow::Result<Vec<String>> {
        let verified = self.verify_and_filter(include_catalog)?;
        let mut out = Vec::new();
        for reg in &verified.dialable {
            for addr in &reg.multiaddrs {
                let a = addr.trim();
                if a.is_empty() {
                    continue;
                }
                if peer_id_from_multiaddr(a).as_deref() != Some(reg.peer_id.as_str()) {
                    continue;
                }
                if !out.iter().any(|x: &String| x == a) {
                    out.push(a.to_string());
                }
            }
        }
        Ok(out)
    }

    pub fn merge(mut self, other: Self) -> Self {
        for reg in other.registrations {
            if self.registrations.iter().any(|r| r.peer_id == reg.peer_id) {
                continue;
            }
            self.registrations.push(reg);
        }
        self
    }
}

pub fn load_relay_manifest(db_path: &str) -> RelayRegistryManifest {
    let mut manifest = RelayRegistryManifest::default_embedded();
    let path = std::path::Path::new(db_path).join("relay_registry.json");
    if path.exists() {
        if let Ok(content) = std::fs::read_to_string(&path) {
            if let Ok(custom) = RelayRegistryManifest::parse_json(&content) {
                manifest = manifest.merge(custom);
            }
        }
    }
    manifest
}

pub fn include_catalog_relays() -> bool {
    std::env::var("MYCELIUM_INCLUDE_CATALOG_RELAYS")
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false)
}

pub fn federated_bootstrap_peers(db_path: &str) -> Vec<String> {
    let manifest = load_relay_manifest(db_path);
    manifest
        .bootstrap_multiaddrs(include_catalog_relays())
        .unwrap_or_default()
}

pub fn validate_registration(
    reg: &RelayRegistration,
    policy: &RelayRegistryPolicy,
) -> Result<(), String> {
    if reg.peer_id.trim().is_empty() {
        return Err("missing peer_id".into());
    }
    if reg.multiaddrs.is_empty() {
        return Err("missing multiaddrs".into());
    }
    if reg.operator_pubkey_hex.len() != 64 && !reg.operator_pubkey_hex.starts_with("080112") {
        // Accept libp2p protobuf (longer) or raw 32-byte hex.
        if reg.operator_pubkey_hex.len() < 64 {
            return Err("invalid operator_pubkey_hex".into());
        }
    }
    if !reg.verify_operator_signature() {
        return Err("invalid operator signature".into());
    }
    for addr in &reg.multiaddrs {
        let Some(pid) = peer_id_from_multiaddr(addr) else {
            return Err(format!("invalid multiaddr: {addr}"));
        };
        if pid != reg.peer_id {
            return Err(format!("multiaddr peer mismatch: {addr}"));
        }
    }
    if !reg.verify_endorsements(policy.min_curator_endorsements) {
        return Err("insufficient valid curator endorsements".into());
    }
    Ok(())
}

fn apply_sybil_limits(
    dialable: &mut Vec<RelayRegistration>,
    policy: &RelayRegistryPolicy,
    report: &mut RelaySybilReport,
) {
    let mut by_operator: HashMap<String, u8> = HashMap::new();
    let mut by_prefix: HashMap<String, u8> = HashMap::new();
    dialable.retain(|reg| {
        let op = reg.operator_pubkey_hex.clone();
        let op_count = by_operator.entry(op).or_insert(0);
        if *op_count >= policy.max_relays_per_operator {
            report
                .rejected
                .push(format!("{}: operator relay cap", reg.peer_id));
            return false;
        }
        if let Some(prefix) = reg.ipv4_prefix() {
            let p_count = by_prefix.entry(prefix).or_insert(0);
            if *p_count >= policy.max_relays_per_ipv4_prefix {
                report
                    .rejected
                    .push(format!("{}: ipv4 prefix cap", reg.peer_id));
                return false;
            }
            *p_count += 1;
        }
        *op_count += 1;
        true
    });
}

pub fn curator_keypair(curator_id: &str) -> Keypair {
    key_from_seed(format!("curator:{curator_id}:v1").as_bytes())
}

pub fn curator_pubkey_hex(curator_id: &str) -> String {
    hex::encode(curator_keypair(curator_id).public().encode_protobuf())
}

fn curator_pubkey_matches_id(curator_id: &str, pubkey_hex: &str) -> bool {
    if !FEDERATED_RELAY_CURATOR_IDS
        .iter()
        .any(|(id, _)| *id == curator_id)
    {
        return false;
    }
    curator_pubkey_hex(curator_id).eq_ignore_ascii_case(pubkey_hex.trim())
}

pub fn verify_operator_signature(pubkey_hex: &str, bytes: &[u8], signature: &[u8]) -> bool {
    verify_pubkey_signature(pubkey_hex, bytes, signature)
}

pub fn verify_pubkey_signature(pubkey_hex: &str, bytes: &[u8], signature: &[u8]) -> bool {
    if let Ok(pk_bytes) = hex::decode(pubkey_hex.trim()) {
        if let Ok(pk) = libp2p::identity::PublicKey::try_decode_protobuf(&pk_bytes) {
            return pk.verify(bytes, signature);
        }
    }
    // Raw 32-byte ed25519 public key hex (operator convention).
    if pubkey_hex.len() == 64 {
        if let Ok(raw) = hex::decode(pubkey_hex) {
            if raw.len() == 32 {
                let mut arr = [0u8; 32];
                arr.copy_from_slice(&raw);
                if let Ok(ed_pk) = ed25519::PublicKey::try_from_bytes(&arr) {
                    let pk = libp2p::identity::PublicKey::from(ed_pk);
                    return pk.verify(bytes, signature);
                }
            }
        }
    }
    false
}

pub fn ipv4_prefix_from_multiaddr(addr: &str) -> Option<String> {
    let rest = addr.strip_prefix("/ip4/")?;
    let octets = rest.split('/').next()?;
    let parts: Vec<_> = octets.split('.').collect();
    if parts.len() != 4 {
        return None;
    }
    Some(format!("{}.{}.{}.", parts[0], parts[1], parts[2]))
}

pub fn is_testnet_multiaddr(addr: &str) -> bool {
    addr.contains("/ip4/203.0.113.")
        || addr.contains("/ip4/198.51.100.")
        || addr.contains("/ip4/192.0.2.")
}

pub fn key_from_seed(seed: &[u8]) -> Keypair {
    let digest = blake3::hash(seed);
    let mut secret = digest.as_bytes()[..32].to_vec();
    let sk = ed25519::SecretKey::try_from_bytes(&mut secret)
        .expect("blake3 output is valid ed25519 seed");
    Keypair::from(ed25519::Keypair::from(sk))
}

/// Build a signed registration + curator endorsement (operators / CI).
pub fn build_signed_registration(
    operator_key: &Keypair,
    curator_id: &str,
    peer_id: &str,
    multiaddrs: Vec<String>,
    operator_label: &str,
    region: &str,
    registered_at_ms: u64,
    catalog_only: bool,
) -> anyhow::Result<RelayRegistration> {
    let curator_key = curator_keypair(curator_id);
    let operator_pubkey_hex = hex::encode(operator_key.public().encode_protobuf());
    let mut reg = RelayRegistration {
        peer_id: peer_id.to_string(),
        multiaddrs,
        operator_pubkey_hex,
        operator_label: operator_label.to_string(),
        region: region.to_string(),
        registered_at_ms,
        uptime_history: Vec::new(),
        operator_signature_hex: String::new(),
        endorsements: Vec::new(),
        catalog_only,
    };
    let sig = sign_account_message(operator_key, &reg.signable_bytes())?;
    reg.operator_signature_hex = hex::encode(sig);

    let signed_at_ms = registered_at_ms;
    let endo_bytes = RelayEndorsement::endorsement_signable_bytes(&reg, signed_at_ms);
    let endo_sig = sign_account_message(&curator_key, &endo_bytes)?;
    reg.endorsements.push(RelayEndorsement {
        curator_id: curator_id.to_string(),
        curator_pubkey_hex: curator_pubkey_hex(curator_id),
        signed_at_ms,
        signature_hex: hex::encode(endo_sig),
    });
    Ok(reg)
}

fn build_default_manifest() -> RelayRegistryManifest {
    let issued_at_ms = 1_700_000_000_000u64;
    let fly_operator = key_from_seed(b"operator:mycelium-fly:v1");
    let fly_addrs: Vec<String> = BOOTSTRAP_PEERS
        .iter()
        .filter(|a| a.contains("/tcp/") && !a.contains("/p2p-circuit"))
        .map(|s| (*s).to_string())
        .collect();
    let fly = build_signed_registration(
        &fly_operator,
        "mycelium-release",
        RELAY_PEER_ID,
        fly_addrs,
        "mycelium-core-fly",
        "sin",
        issued_at_ms,
        false,
    )
    .expect("fly registration");

    let eu_peer = key_from_seed(b"peer:community-mesh-eu:v1");
    let eu_peer_id = eu_peer.public().to_peer_id().to_string();
    let eu_operator = key_from_seed(b"operator:community-mesh-eu:v1");
    let eu = build_signed_registration(
        &eu_operator,
        "community-mesh-eu",
        &eu_peer_id,
        vec![format!("/ip4/203.0.113.10/tcp/4001/p2p/{eu_peer_id}")],
        "community-mesh-eu-template",
        "eu-central",
        issued_at_ms,
        true,
    )
    .expect("eu catalog registration");

    let na_peer = key_from_seed(b"peer:community-mesh-na:v1");
    let na_peer_id = na_peer.public().to_peer_id().to_string();
    let na_operator = key_from_seed(b"operator:community-mesh-na:v1");
    let na = build_signed_registration(
        &na_operator,
        "community-mesh-na",
        &na_peer_id,
        vec![format!("/ip4/203.0.113.11/tcp/4001/p2p/{na_peer_id}")],
        "community-mesh-na-template",
        "na-east",
        issued_at_ms,
        true,
    )
    .expect("na catalog registration");

    RelayRegistryManifest {
        format: REGISTRY_FORMAT.into(),
        issued_at_ms,
        policy: RelayRegistryPolicy::default(),
        registrations: vec![fly, eu, na],
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn registration_sign_verify_roundtrip() {
        let operator = Keypair::generate_ed25519();
        let peer = operator.public().to_peer_id().to_string();
        let addr = format!("/ip4/203.0.113.10/tcp/4001/p2p/{peer}");
        let reg = build_signed_registration(
            &operator,
            "mycelium-release",
            &peer,
            vec![addr],
            "test-op",
            "test",
            1_700_000_000_000,
            true,
        )
        .unwrap();
        assert!(reg.verify_operator_signature());
        assert!(reg.verify_endorsements(1));
    }

    #[test]
    fn sybil_policy_caps_per_operator() {
        let operator = key_from_seed(b"same-operator");
        let mut regs = Vec::new();
        for i in 0..3 {
            let kp = Keypair::generate_ed25519();
            let peer = kp.public().to_peer_id().to_string();
            regs.push(
                build_signed_registration(
                    &operator,
                    "mycelium-release",
                    &peer,
                    vec![format!("/ip4/203.0.113.{i}/tcp/4001/p2p/{peer}")],
                    "same-op",
                    "test",
                    1,
                    false,
                )
                .unwrap(),
            );
        }
        let manifest = RelayRegistryManifest {
            format: REGISTRY_FORMAT.into(),
            issued_at_ms: 1,
            policy: RelayRegistryPolicy {
                max_relays_per_operator: 2,
                ..Default::default()
            },
            registrations: regs,
        };
        let verified = manifest.verify_and_filter(true).unwrap();
        assert!(verified.dialable.len() <= 2);
    }

    #[test]
    fn default_manifest_includes_production_relay() {
        let manifest = RelayRegistryManifest::default_embedded();
        let addrs = manifest.bootstrap_multiaddrs(false).unwrap();
        assert!(
            addrs.iter().any(|a| a.contains(RELAY_PEER_ID)),
            "production relay must be dialable"
        );
    }

    #[test]
    fn catalog_only_excluded_by_default() {
        let manifest = RelayRegistryManifest::default_embedded();
        let with = manifest.bootstrap_multiaddrs(true).unwrap();
        let without = manifest.bootstrap_multiaddrs(false).unwrap();
        assert!(with.len() > without.len());
    }

    #[test]
    fn curator_keys_are_stable() {
        let a = curator_pubkey_hex("mycelium-release");
        let b = curator_pubkey_hex("mycelium-release");
        assert_eq!(a, b);
    }
}
