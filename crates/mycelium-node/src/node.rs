// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
use crate::error::decode_peer_ble_wire_frame;
use crate::forwarding::{
    EnergyPolicy, ForwardDecision, ForwardingPolicy, IngestPipeline, ProbabilisticForwardingPolicy,
    SeenCache, SimpleEnergyPolicy,
};
use crate::ingest_guard::{IngestGuard, IngestReject};
use crate::security::{self, Sd030DropReason};
use crate::storage::SledMessageStore;
use crate::transport::Libp2pTransport;
use libp2p::{identity, Multiaddr, PeerId};
use mycelium_core::bootstrap::load_bootstrap_peers;
use mycelium_core::crypto::{self};
use mycelium_core::data::{now_ms, Priority};
use mycelium_core::energy::NodeState;
use mycelium_core::sync::BloomFilter;
use mycelium_core::transport::{
    ConnectivityMode, DirectMessage, MeshTransport, MessageStore, Scope, StoreStats,
    TransportEvent, WireMessage,
};
use mycelium_l0_facade::EncKey;
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet, VecDeque};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};
use tokio::sync::{broadcast, mpsc, RwLock};
use tokio::time::{interval, MissedTickBehavior};
use tracing::{debug, info, warn};

type BleWireOutboundReply = tokio::sync::oneshot::Sender<Vec<(String, Vec<u8>)>>;
type BleStreamOutboundReply = tokio::sync::oneshot::Sender<Vec<Vec<u8>>>;
type E2eEncryptReply = tokio::sync::oneshot::Sender<anyhow::Result<(Vec<u8>, u8)>>;

/// Scopes subscribed on the libp2p gossip layer at node startup.
pub const SYSTEM_SCOPES: &[&str] = &[
    "mycelium/chat",
    "mycelium/coin/v1",
    "mycelium/appstore/v1",
    "mycelium/identity/v1",
];

const RELAY_KEY_ROTATION_SECS: u64 = 86_400;

fn default_max_peers() -> usize {
    50
}

#[derive(Debug, Clone)]
pub struct NodeConfig {
    pub listen_addr: Multiaddr,
    pub db_path: String,
    pub keypair_path: Option<String>,
    pub forwarding_interval_ms: u64,
    pub sync_interval_secs: u64,
    /// Multiaddrs dialed when [`ConnectivityMode::Internet`]. Empty means
    /// [`NodeRunner::new`] fills this from [`mycelium_core::bootstrap::load_bootstrap_peers`].
    pub bootstrap_peers: Vec<String>,
    /// When set, the libp2p transport emits [`TransportEvent::ConnectivityChanged`] on mode flips.
    pub connectivity_rx: Option<tokio::sync::watch::Receiver<ConnectivityMode>>,
    /// Optional display name included in [`WireMessage::PeerInfo`].
    pub display_name: Option<String>,
    /// Optional 32-byte master key for at-rest encryption (Android Keystore-backed prefs).
    pub storage_key: Option<[u8; 32]>,
    /// Max relay candidates when the destination peer is not directly connected.
    pub max_relay_fanout: usize,
    /// When true, register on relay rendezvous and dial other opted-in peers.
    pub rendezvous_enabled: bool,
    /// When false, stay in local-mesh mode even if the public relay is reachable.
    pub internet_enabled: Arc<AtomicBool>,
    /// Sender paired with [`Self::connectivity_rx`] for runtime mode updates (opt-out, probe).
    pub connectivity_mode_tx: Option<tokio::sync::watch::Sender<ConnectivityMode>>,
    /// Bulletin-Board-Scopes die der Nutzer explizit abonniert hat.
    /// Leer = kein Bulletin Board (default).
    pub bulletin_subscriptions: Vec<String>,
    /// Max direkte Peer-Verbindungen. Default 50.
    pub max_peers: usize,
    /// Festival / dense LAN: cap mDNS, dense gossip, lower peer cap (Phase D3).
    pub crowd_mode: bool,
}

fn default_max_relay_fanout() -> usize {
    3
}

fn default_internet_enabled() -> Arc<AtomicBool> {
    Arc::new(AtomicBool::new(true))
}

impl Default for NodeConfig {
    fn default() -> Self {
        Self {
            listen_addr: "/ip4/0.0.0.0/tcp/0"
                .parse()
                // INVARIANT: compile-time constant multiaddr; parse cannot fail.
                .expect("default listen addr must parse"),
            db_path: ".mycelium-node".to_string(),
            keypair_path: Some(".mycelium-node/identity".to_string()),
            forwarding_interval_ms: 500,
            sync_interval_secs: 30,
            bootstrap_peers: Vec::new(),
            connectivity_rx: None,
            display_name: None,
            storage_key: None,
            max_relay_fanout: default_max_relay_fanout(),
            rendezvous_enabled: true,
            internet_enabled: default_internet_enabled(),
            connectivity_mode_tx: None,
            bulletin_subscriptions: Vec::new(),
            max_peers: default_max_peers(),
            crowd_mode: false,
        }
    }
}

impl NodeConfig {
    /// Sensible defaults; `bootstrap_peers` stays empty so [`NodeRunner::new`] loads
    /// peers via [`mycelium_core::bootstrap::load_bootstrap_peers`].
    pub fn with_defaults(db_path: &str) -> Self {
        Self {
            listen_addr: "/ip4/0.0.0.0/tcp/0"
                .parse()
                // INVARIANT: compile-time constant multiaddr; parse cannot fail.
                .expect("default listen addr must parse"),
            db_path: db_path.to_string(),
            keypair_path: Some(format!("{db_path}/identity")),
            forwarding_interval_ms: 500,
            sync_interval_secs: 30,
            bootstrap_peers: Vec::new(),
            connectivity_rx: None,
            display_name: None,
            storage_key: None,
            max_relay_fanout: default_max_relay_fanout(),
            rendezvous_enabled: true,
            internet_enabled: default_internet_enabled(),
            connectivity_mode_tx: None,
            bulletin_subscriptions: Vec::new(),
            max_peers: default_max_peers(),
            crowd_mode: false,
        }
    }
}

#[derive(Clone)]
struct RelayIdentity {
    keypair: Arc<Mutex<identity::Keypair>>,
    created_at: Arc<Mutex<Instant>>,
}

impl RelayIdentity {
    fn new() -> Self {
        Self {
            keypair: Arc::new(Mutex::new(identity::Keypair::generate_ed25519())),
            created_at: Arc::new(Mutex::new(Instant::now())),
        }
    }

    fn peer_id(&self) -> String {
        self.keypair
            .lock()
            .expect("relay keypair lock") // INVARIANT: mutex not poisoned; we never panic while holding it
            .public()
            .to_peer_id()
            .to_string()
    }

    fn age_secs(&self) -> u64 {
        self.created_at
            .lock()
            .expect("relay created_at lock") // INVARIANT: mutex not poisoned; we never panic while holding it
            .elapsed()
            .as_secs()
    }

    fn maybe_rotate(&self) {
        if self.age_secs() > RELAY_KEY_ROTATION_SECS {
            *self.keypair.lock().expect("relay keypair lock") = // INVARIANT: mutex not poisoned
                identity::Keypair::generate_ed25519();
            *self.created_at.lock().expect("relay created_at lock") = Instant::now(); // INVARIANT: mutex not poisoned
            info!("relay keypair rotated");
        }
    }

    fn signing_keypair(&self) -> identity::Keypair {
        self.keypair.lock().expect("relay keypair lock").clone() // INVARIANT: mutex not poisoned
    }
}

#[derive(Debug)]
pub enum NodeCommand {
    SendDirect {
        to_peer: String,
        body: String,
    },
    SendDirectPayload {
        to_peer: String,
        body: String,
        payload: Vec<u8>,
    },
    Broadcast {
        scope: String,
        body: String,
    },
    BroadcastPayload {
        scope: String,
        body: String,
        payload: Vec<u8>,
    },
    SendWire {
        to_peer: String,
        message: WireMessage,
    },
    AddBootstrapPeer {
        multiaddr: String,
    },
    /// Trust-on-first-use: seed X25519 key from invite v2 QR (no libp2p PeerInfo required).
    SeedPeerEncKey {
        peer_id: String,
        enc_pubkey_hex: String,
    },
    /// Seed ratchet prekey bundle from invite v3 tail (first-message v1 E2E).
    SeedPeerRatchetBundle {
        peer_id: String,
        bundle: mycelium_core::crypto::ratchet::PrekeyBundle,
    },
    /// Build signed v3 invite including live ratchet prekeys from the runner.
    BuildSignedInviteV3 {
        peer_id: String,
        direct_multiaddr: Option<String>,
        reply: tokio::sync::oneshot::Sender<anyhow::Result<String>>,
    },
    SubscribeScope(String),
    UnsubscribeScope(String),
    SubscribeBulletinScope(String),
    UnsubscribeBulletinScope(String),
    GcNow {
        reply: Option<tokio::sync::oneshot::Sender<usize>>,
    },
    StoreStats {
        reply: Option<tokio::sync::oneshot::Sender<StoreStats>>,
    },
    ListPeers,
    ConnectedPeers {
        reply: Option<tokio::sync::oneshot::Sender<Vec<String>>>,
    },
    SetEnergyState(NodeState),
    SetRendezvousEnabled(bool),
    /// Voluntary opt-out of relay/internet dials (local mesh only).
    SetInternetEnabled(bool),
    /// Ingest a BLE side-channel frame (`mycelium_core::ble_mesh`).
    IngestBleFrame {
        from_peer: String,
        frame: Vec<u8>,
    },
    /// Take queued BLE outbound frames for a destination peer (F2).
    DrainBleOutbound {
        to_peer: Option<String>,
        max: usize,
        reply: Option<BleWireOutboundReply>,
    },
    /// Send wire message over libp2p if connected, else queue for BLE (F2).
    SendWireOrQueueBle {
        to_peer: String,
        message: WireMessage,
    },
    /// Encrypt a wrapped E2E direct payload (ratchet when possible, else legacy v0).
    EncryptE2ePayload {
        to_peer: String,
        wrapped: Vec<u8>,
        reply: Option<E2eEncryptReply>,
    },
    /// Register BLE libp2p overlay link (F3).
    RegisterBleLink {
        peer_id: String,
        device_id: String,
    },
    /// Ingest reassembled MYLP stream frame from host GATT (F3).
    BleStreamInbound {
        from_peer: String,
        frame: Vec<u8>,
    },
    /// Take queued F3 stream frames for a peer.
    DrainBleStreamOutbound {
        to_peer: String,
        max: usize,
        reply: Option<BleStreamOutboundReply>,
    },
    SetCrowdMode(bool),
}

#[derive(Debug, Default, Clone, Serialize, Deserialize)]
pub struct NodeMetrics {
    pub messages_forwarded: u64,
    pub messages_dropped_ttl: u64,
    pub messages_dropped_hops: u64,
    pub messages_dropped_queue: u64,
    pub messages_dropped_invalid_sig: u64,
    pub messages_dropped_no_sig: u64,
    pub messages_dropped_rate_limit: u64,
    pub messages_dropped_replay: u64,
    pub messages_dropped_oversize: u64,
    pub messages_delivered_local: u64,
    pub pending_queue_size: usize,
    pub seen_cache_size: usize,
    pub connected_peers: usize,
    pub max_peers: usize,
    pub peer_cap_rejections: u64,
}

#[derive(Debug, Default, Clone, Serialize, Deserialize)]
pub struct PeerReputationSnapshot {
    pub strikes: u8,
    pub last_strike_ms: u64,
    pub throttled_until_ms: u64,
}

#[derive(Clone)]
pub struct NodeHandle {
    tx: mpsc::Sender<NodeCommand>,
    metrics: Arc<RwLock<NodeMetrics>>,
    incoming_tx: broadcast::Sender<DirectMessage>,
    peers: Arc<RwLock<Vec<String>>>,
    reputations: Arc<Mutex<HashMap<String, PeerReputationSnapshot>>>,
    scopes: Arc<RwLock<HashSet<String>>>,
    bulletin_subscriptions: Arc<RwLock<Vec<String>>>,
    relay_identity: RelayIdentity,
    enc_pubkey_hex: String,
    peer_x25519: Arc<RwLock<HashMap<String, [u8; 32]>>>,
    listen_addrs: Arc<RwLock<Vec<String>>>,
    account_id: String,
    transport_epoch: u64,
    account_signing_key: Arc<identity::Keypair>,
    invite_direct_cache: Arc<RwLock<Option<String>>>,
}

impl NodeHandle {
    pub async fn send(&self, cmd: NodeCommand) -> anyhow::Result<()> {
        self.tx.send(cmd).await?;
        Ok(())
    }

    pub async fn metrics(&self) -> NodeMetrics {
        self.metrics.read().await.clone()
    }

    pub fn subscribe_incoming(&self) -> broadcast::Receiver<DirectMessage> {
        self.incoming_tx.subscribe()
    }

    pub async fn known_peers(&self) -> Vec<String> {
        self.peers
            .read()
            .await
            .iter()
            .filter(|p| !mycelium_core::bootstrap::is_relay_peer(p))
            .cloned()
            .collect()
    }

    /// Peers with an active libp2p or BLE transport path (not just cached in peer cap).
    pub async fn connected_peers(&self) -> Vec<String> {
        let (tx, rx) = tokio::sync::oneshot::channel();
        if self
            .tx
            .send(NodeCommand::ConnectedPeers { reply: Some(tx) })
            .await
            .is_err()
        {
            return Vec::new();
        }
        rx.await.unwrap_or_default()
    }

    pub async fn is_peer_connected(&self, peer: &str) -> bool {
        if mycelium_core::bootstrap::is_relay_peer(peer) {
            return false;
        }
        self.connected_peers().await.iter().any(|p| p == peer)
    }

    pub async fn listen_addrs(&self) -> Vec<String> {
        self.listen_addrs.read().await.clone()
    }

    pub fn account_id(&self) -> &str {
        &self.account_id
    }

    pub fn transport_epoch(&self) -> u64 {
        self.transport_epoch
    }

    pub async fn peer_reputation(&self, peer_id: &str) -> Option<PeerReputationSnapshot> {
        self.reputations
            .lock()
            .expect("reputation lock") // INVARIANT: mutex not poisoned
            .get(peer_id)
            .cloned()
    }

    pub async fn subscribed_scopes(&self) -> Vec<String> {
        self.scopes.read().await.iter().cloned().collect()
    }

    pub fn relay_peer_id(&self) -> String {
        self.relay_identity.peer_id()
    }

    pub fn relay_keypair_age_secs(&self) -> u64 {
        self.relay_identity.age_secs()
    }

    pub async fn bulletin_subscriptions(&self) -> Vec<String> {
        self.bulletin_subscriptions.read().await.clone()
    }

    pub async fn subscribe_bulletin_scope(&self, scope: String) -> anyhow::Result<()> {
        self.send(NodeCommand::SubscribeBulletinScope(scope)).await
    }

    pub async fn unsubscribe_bulletin_scope(&self, scope: String) -> anyhow::Result<()> {
        self.send(NodeCommand::UnsubscribeBulletinScope(scope))
            .await
    }

    pub async fn is_bulletin_enabled(&self) -> bool {
        !self.bulletin_subscriptions.read().await.is_empty()
    }

    pub fn local_enc_pubkey_hex(&self) -> String {
        self.enc_pubkey_hex.clone()
    }

    /// Stable signed v3 invite using the account key and live ratchet prekeys from the runner.
    pub async fn encode_signed_invite_v3(
        &self,
        peer_id: &str,
        direct_multiaddr: Option<&str>,
    ) -> anyhow::Result<String> {
        let (tx, rx) = tokio::sync::oneshot::channel();
        self.tx
            .send(NodeCommand::BuildSignedInviteV3 {
                peer_id: peer_id.to_string(),
                direct_multiaddr: direct_multiaddr.map(String::from),
                reply: tx,
            })
            .await?;
        rx.await
            .map_err(|_| anyhow::anyhow!("invite build response channel closed"))?
    }

    pub fn build_contact_hello(
        &self,
        peer_id: &str,
        display_name: &str,
    ) -> anyhow::Result<mycelium_core::contact_hello::ContactHello> {
        mycelium_core::contact_hello::ContactHello::sign(
            peer_id,
            &self.account_id,
            display_name,
            &self.enc_pubkey_hex,
            &self.account_signing_key,
        )
    }

    /// First stable LAN multiaddr seen; cached so the signed v3 invite QR does not flip.
    pub async fn stable_direct_multiaddr(&self, local_peer_id: &str) -> Option<String> {
        if let Some(cached) = self.invite_direct_cache.read().await.clone() {
            return Some(cached);
        }
        let listen = self.listen_addrs().await;
        let picked = mycelium_core::bootstrap::pick_stable_lan_multiaddr(&listen, local_peer_id);
        if let Some(ref addr) = picked {
            *self.invite_direct_cache.write().await = Some(addr.clone());
        }
        picked
    }

    pub async fn has_enc_key_for(&self, peer: &str) -> bool {
        self.peer_x25519.read().await.contains_key(peer)
    }

    pub async fn encrypt_e2e_payload(
        &self,
        to_peer: &str,
        wrapped: Vec<u8>,
    ) -> anyhow::Result<(Vec<u8>, u8)> {
        let (tx, rx) = tokio::sync::oneshot::channel();
        self.tx
            .send(NodeCommand::EncryptE2ePayload {
                to_peer: to_peer.to_string(),
                wrapped,
                reply: Some(tx),
            })
            .await?;
        rx.await?
    }

    pub async fn seed_peer_enc_key(&self, peer: &str, enc_pubkey_hex: &str) -> anyhow::Result<()> {
        self.send(NodeCommand::SeedPeerEncKey {
            peer_id: peer.to_string(),
            enc_pubkey_hex: enc_pubkey_hex.to_string(),
        })
        .await
    }

    pub async fn seed_peer_ratchet_bundle(
        &self,
        peer: &str,
        bundle: mycelium_core::crypto::ratchet::PrekeyBundle,
    ) -> anyhow::Result<()> {
        self.send(NodeCommand::SeedPeerRatchetBundle {
            peer_id: peer.to_string(),
            bundle,
        })
        .await
    }

    pub async fn peer_x25519_public(&self, peer: &str) -> Option<crypto::X25519PublicKey> {
        self.peer_x25519
            .read()
            .await
            .get(peer)
            .map(|b| crypto::X25519PublicKey::from(*b))
    }

    pub async fn store_stats(&self) -> anyhow::Result<StoreStats> {
        let (tx, rx) = tokio::sync::oneshot::channel();
        self.tx
            .send(NodeCommand::StoreStats { reply: Some(tx) })
            .await?;
        rx.await
            .map_err(|_| anyhow::anyhow!("store stats response channel closed"))
    }

    pub async fn gc_now(&self) -> anyhow::Result<usize> {
        let (tx, rx) = tokio::sync::oneshot::channel();
        self.tx.send(NodeCommand::GcNow { reply: Some(tx) }).await?;
        rx.await
            .map_err(|_| anyhow::anyhow!("gc response channel closed"))
    }
}

pub struct NodeRunner {
    transport: Box<dyn MeshTransport>,
    keypair: Option<identity::Keypair>,
    local_peer_id: String,
    store: Arc<dyn MessageStore>,
    ingest: IngestPipeline,
    seen_cache: BasicSeenCache,
    node_state: NodeState,
    forwarding_policy: ProbabilisticForwardingPolicy,
    energy_policy: SimpleEnergyPolicy,
    pending: VecDeque<PendingMessage>,
    pending_cap: usize,
    forwarding_interval_ms: u64,
    sync_interval_secs: u64,
    metrics: Arc<RwLock<NodeMetrics>>,
    incoming_tx: broadcast::Sender<DirectMessage>,
    peers: Arc<RwLock<Vec<String>>>,
    subscribed_scopes: Arc<RwLock<HashSet<String>>>,
    peer_scopes: HashMap<String, HashSet<String>>,
    peer_reputations: Arc<Mutex<HashMap<String, PeerReputationSnapshot>>>,
    cmd_rx: mpsc::Receiver<NodeCommand>,
    enc_keypair: EncKey,
    display_name: Option<String>,
    peer_x25519: Arc<RwLock<HashMap<String, [u8; 32]>>>,
    max_relay_fanout: usize,
    listen_addrs: Arc<RwLock<Vec<String>>>,
    rendezvous_enabled: Arc<AtomicBool>,
    internet_enabled: Arc<AtomicBool>,
    connectivity_mode_tx: Option<tokio::sync::watch::Sender<ConnectivityMode>>,
    db_path: String,
    relay_identity: RelayIdentity,
    bulletin_subscriptions: Arc<RwLock<Vec<String>>>,
    ingest_guard: IngestGuard,
    peer_enc_store: Arc<crate::peer_enc_keys::PeerEncKeyStore>,
    account_id: String,
    transport_epoch: u64,
    account_signing_key: Arc<identity::Keypair>,
    pending_identity_rotation: Option<mycelium_core::identity_rotation::IdentityRotationGossip>,
    crowd_mode: Arc<AtomicBool>,
    rendezvous_dial_bucket: u32,
    ble_outbound: VecDeque<(String, Vec<u8>)>,
    ble_outbound_cap: usize,
    ratchet_store: Arc<crate::ratchet_store::RatchetSessionStore>,
    local_prekeys: Arc<Mutex<mycelium_core::crypto::ratchet::LocalPrekeyStore>>,
}

async fn resolve_logical_sender(
    peer_x25519: &Arc<RwLock<HashMap<String, [u8; 32]>>>,
    sender_enc_pubkey: &str,
    from_peer: &str,
) -> String {
    if let Ok(pk) = crypto::parse_x25519_public_hex(sender_enc_pubkey) {
        let target = pk.as_bytes();
        let map = peer_x25519.read().await;
        if let Some(id) = map
            .iter()
            .find(|(_, v)| v.as_slice() == target)
            .map(|(k, _)| k.clone())
        {
            return id;
        }
    }
    from_peer.to_string()
}

async fn persist_peer_enc_key(
    peer_x25519: Arc<RwLock<HashMap<String, [u8; 32]>>>,
    peer_enc_store: Arc<crate::peer_enc_keys::PeerEncKeyStore>,
    peer_id: String,
    key: [u8; 32],
) {
    peer_x25519.write().await.insert(peer_id.clone(), key);
    if let Err(e) = peer_enc_store.save(&peer_id, &key) {
        warn!("failed to persist enc key for {peer_id}: {e}");
    }
}

impl NodeRunner {
    pub fn new(config: NodeConfig) -> anyhow::Result<(Self, NodeHandle)> {
        if let Err(e) = mycelium_core::bootstrap::sanitize_persisted_dial_peers(&config.db_path) {
            tracing::warn!("could not sanitize dial peers: {e}");
        }
        let mut bootstrap_peers = if config.bootstrap_peers.is_empty() {
            load_bootstrap_peers(&config.db_path)
        } else {
            config.bootstrap_peers.clone()
        };
        for extra in mycelium_core::bootstrap::load_persisted_dial_peers(&config.db_path) {
            if !bootstrap_peers.iter().any(|p| p == &extra) {
                bootstrap_peers.push(extra);
            }
        }
        // At most one stored circuit target (latest QR scan).
        let mut last_circuit: Option<String> = None;
        bootstrap_peers.retain(|p| {
            if p.contains("/p2p-circuit/") {
                last_circuit = Some(p.clone());
                false
            } else {
                true
            }
        });
        if let Some(circuit) = last_circuit {
            bootstrap_peers.push(circuit);
        }
        let keypair_path = config
            .keypair_path
            .clone()
            .or_else(|| Some(format!("{}/identity", config.db_path)));
        let identity = crate::secrets::bootstrap_identities(
            &config.db_path,
            keypair_path.as_deref().unwrap_or("identity"),
            config.storage_key,
        )?;
        let transport = Libp2pTransport::new(
            config.listen_addr.clone(),
            keypair_path,
            bootstrap_peers,
            config.connectivity_rx.clone(),
            config.storage_key,
            config.max_peers,
            config.crowd_mode,
            config.internet_enabled.clone(),
        )?;
        Self::new_with_transport(config, Box::new(transport), identity)
    }

    pub fn new_with_transport(
        config: NodeConfig,
        transport: Box<dyn MeshTransport>,
        identity: crate::secrets::IdentityBootstrap,
    ) -> anyhow::Result<(Self, NodeHandle)> {
        let keypair = transport.local_keypair();
        let local_peer_id = transport.local_peer_id();
        let enc_keypair =
            crate::secrets::load_or_create_enc_keypair(&config.db_path, config.storage_key)?;
        let enc_pubkey_hex = enc_keypair.public_hex();
        let peer_enc_store = Arc::new(crate::peer_enc_keys::PeerEncKeyStore::open(
            &config.db_path,
        )?);
        let initial_enc = peer_enc_store.load_all().unwrap_or_else(|e| {
            warn!("could not load persisted peer enc keys: {e}");
            HashMap::new()
        });
        if !initial_enc.is_empty() {
            info!("loaded {} persisted peer enc key(s)", initial_enc.len());
        }
        let peer_x25519 = Arc::new(RwLock::new(initial_enc));
        let ratchet_store = Arc::new(crate::ratchet_store::RatchetSessionStore::open(
            &config.db_path,
        )?);
        let local_prekeys = match ratchet_store.load_local_prekeys()? {
            Some(pk) => pk,
            None => {
                let pk = mycelium_core::crypto::ratchet::LocalPrekeyStore::generate(
                    enc_keypair.as_core_keypair(),
                    32,
                );
                ratchet_store.save_local_prekeys(&pk)?;
                pk
            }
        };
        let local_prekeys = Arc::new(Mutex::new(local_prekeys));
        let listen_addrs = Arc::new(RwLock::new(Vec::new()));
        let display_name = config.display_name.clone();
        let store = Arc::new(SledMessageStore::open(&config.db_path)?);
        let ingest = IngestPipeline::new(store.clone());
        let metrics = Arc::new(RwLock::new(NodeMetrics::default()));
        let peers = Arc::new(RwLock::new(Vec::new()));
        let reputations = Arc::new(Mutex::new(HashMap::new()));
        let mut initial_scopes: HashSet<String> =
            SYSTEM_SCOPES.iter().map(|s| (*s).to_string()).collect();
        initial_scopes.insert("mycelium/group/*".to_string());
        let scopes = Arc::new(RwLock::new(initial_scopes));
        let bulletin_subscriptions = Arc::new(RwLock::new(config.bulletin_subscriptions.clone()));
        let relay_identity = RelayIdentity::new();
        let (incoming_tx, _) = broadcast::channel(512);
        let (tx, cmd_rx) = mpsc::channel(64);
        let rendezvous_enabled = Arc::new(AtomicBool::new(config.rendezvous_enabled));
        let crowd_mode = Arc::new(AtomicBool::new(config.crowd_mode));
        let account_id = identity.account_id.clone();
        let transport_epoch = identity.transport_meta.epoch.max(1);
        let pending_identity_rotation = identity.pending_rotation;
        let account_signing_key = Arc::new(identity.account_keypair);
        let invite_direct_cache = Arc::new(RwLock::new(None));
        Ok((
            Self {
                local_peer_id,
                transport,
                keypair,
                store,
                ingest,
                seen_cache: BasicSeenCache::new(16_384),
                node_state: NodeState::Active,
                forwarding_policy: ProbabilisticForwardingPolicy,
                energy_policy: SimpleEnergyPolicy,
                pending: VecDeque::new(),
                pending_cap: 1024,
                forwarding_interval_ms: config.forwarding_interval_ms,
                sync_interval_secs: config.sync_interval_secs,
                metrics: metrics.clone(),
                incoming_tx: incoming_tx.clone(),
                peers: peers.clone(),
                subscribed_scopes: scopes.clone(),
                peer_scopes: HashMap::new(),
                peer_reputations: reputations.clone(),
                cmd_rx,
                enc_keypair,
                display_name,
                peer_x25519: peer_x25519.clone(),
                max_relay_fanout: config.max_relay_fanout,
                listen_addrs: listen_addrs.clone(),
                rendezvous_enabled: rendezvous_enabled.clone(),
                internet_enabled: config.internet_enabled.clone(),
                connectivity_mode_tx: config.connectivity_mode_tx.clone(),
                db_path: config.db_path.clone(),
                relay_identity: relay_identity.clone(),
                bulletin_subscriptions: bulletin_subscriptions.clone(),
                ingest_guard: IngestGuard::new(),
                peer_enc_store,
                account_id: account_id.clone(),
                transport_epoch,
                account_signing_key: account_signing_key.clone(),
                pending_identity_rotation,
                crowd_mode: crowd_mode.clone(),
                rendezvous_dial_bucket: 0,
                ble_outbound: VecDeque::new(),
                ble_outbound_cap: 128,
                ratchet_store: ratchet_store.clone(),
                local_prekeys: local_prekeys.clone(),
            },
            NodeHandle {
                tx,
                metrics,
                incoming_tx,
                peers,
                reputations,
                scopes,
                bulletin_subscriptions,
                relay_identity,
                enc_pubkey_hex,
                peer_x25519,
                listen_addrs,
                account_id,
                transport_epoch,
                account_signing_key,
                invite_direct_cache,
            },
        ))
    }

    pub fn local_peer_id(&self) -> &str {
        &self.local_peer_id
    }

    pub fn relay_peer_id(&self) -> String {
        self.relay_identity.peer_id()
    }

    fn maybe_rotate_relay_keypair(&self) {
        self.relay_identity.maybe_rotate();
    }

    /// Test helper: backdate relay key creation.
    #[doc(hidden)]
    pub fn set_relay_keypair_age_for_test(&self, age_secs: u64) {
        let created = Instant::now() - Duration::from_secs(age_secs);
        *self
            .relay_identity
            .created_at
            .lock()
            .expect("relay created_at lock") // INVARIANT: mutex not poisoned
            = created;
    }

    /// Rotate the relay keypair when it is older than 24 hours.
    #[doc(hidden)]
    pub fn rotate_relay_keypair_if_due(&self) {
        self.relay_identity.maybe_rotate();
    }

    fn apply_relay_forward_mask(&self, message: &mut DirectMessage) {
        let relay_id = self.relay_identity.peer_id();
        if message.envelope.author_peer.is_none() {
            message.envelope.author_peer = Some(message.envelope.from_peer.clone());
        }
        message.envelope.from_peer = relay_id;
    }

    fn update_transport_metrics(&self, metrics: &mut NodeMetrics) {
        metrics.connected_peers = self.transport.connected_peer_count();
        metrics.max_peers = self.transport.max_direct_peers();
        metrics.peer_cap_rejections = self.transport.peer_cap_rejections();
    }

    async fn find_peer_via_kad(&mut self, target_peer_id: &str) {
        self.transport.kad_find_peer(target_peer_id);
    }

    pub async fn run(mut self) -> anyhow::Result<()> {
        info!("node started with peer_id={}", self.local_peer_id);
        let crowd = self.crowd_mode.load(Ordering::Relaxed);
        if crowd {
            for scope in self.bulletin_subscriptions.read().await.clone() {
                if let Err(e) = self.transport.subscribe_scope(scope.clone()).await {
                    warn!("failed to subscribe to bulletin scope {scope}: {e}");
                } else {
                    info!("bulletin subscribed (crowd priority): {scope}");
                }
                self.subscribed_scopes.write().await.insert(scope);
            }
        }
        for scope in SYSTEM_SCOPES {
            if let Err(e) = self.transport.subscribe_scope(scope.to_string()).await {
                warn!("failed to subscribe to {scope}: {e}");
            }
        }
        if !crowd {
            for scope in self.bulletin_subscriptions.read().await.clone() {
                if let Err(e) = self.transport.subscribe_scope(scope.clone()).await {
                    warn!("failed to subscribe to bulletin scope {scope}: {e}");
                } else {
                    info!("bulletin subscribed: {scope}");
                }
                self.subscribed_scopes.write().await.insert(scope);
            }
        }
        let mut forward_tick =
            interval(Duration::from_millis(self.config_forwarding_interval_ms()));
        forward_tick.set_missed_tick_behavior(MissedTickBehavior::Skip);
        let mut sync_tick = interval(Duration::from_secs(self.config_sync_interval_secs()));
        sync_tick.set_missed_tick_behavior(MissedTickBehavior::Skip);
        let mut gc_tick = interval(Duration::from_secs(6 * 60 * 60));
        gc_tick.set_missed_tick_behavior(MissedTickBehavior::Skip);
        let (rendezvous_tx, mut rendezvous_rx) = mpsc::channel::<Vec<String>>(8);
        let rendezvous_local = self.local_peer_id.clone();
        let rendezvous_flag = self.rendezvous_enabled.clone();
        let internet_flag = self.internet_enabled.clone();
        tokio::spawn(async move {
            let mut tick = interval(Duration::from_secs(45));
            tick.set_missed_tick_behavior(MissedTickBehavior::Skip);
            loop {
                tick.tick().await;
                if !rendezvous_flag.load(Ordering::Relaxed)
                    || !internet_flag.load(Ordering::Relaxed)
                {
                    continue;
                }
                let local = rendezvous_local.clone();
                let remote_ids = tokio::task::spawn_blocking(move || {
                    mycelium_core::rendezvous::set_relay_rendezvous_registration(
                        &local, true, None,
                    );
                    mycelium_core::rendezvous::fetch_relay_rendezvous(None)
                })
                .await
                .unwrap_or_default();
                if rendezvous_tx.send(remote_ids).await.is_err() {
                    break;
                }
            }
        });

        self.transport.redial_stored_targets();
        if let Some(rot) = self.pending_identity_rotation.take() {
            if let Err(e) = self.publish_identity_rotation(rot).await {
                warn!("failed to publish identity rotation: {e}");
            }
        }
        let mut dial_tick = interval(Duration::from_secs(15));
        dial_tick.set_missed_tick_behavior(MissedTickBehavior::Skip);
        let mut circuit_dial_tick = interval(Duration::from_secs(5));
        circuit_dial_tick.set_missed_tick_behavior(MissedTickBehavior::Skip);
        loop {
            tokio::select! {
                transport_event = self.transport.next_event() => {
                    match transport_event {
                        Ok(event) => {
                            if let Err(e) = self.handle_transport_event(event).await {
                                warn!("transport event handler error: {e:#}");
                            }
                        }
                        Err(e) => warn!("transport event stream error: {e:#}"),
                    }
                }
                Some(cmd) = self.cmd_rx.recv() => {
                    if let Err(e) = self.handle_command(cmd).await {
                        warn!("command handler error: {e:#}");
                    }
                }
                Some(remote_ids) = rendezvous_rx.recv() => {
                    self.dial_rendezvous_list(remote_ids).await;
                }
                _ = forward_tick.tick() => {
                    if let Err(e) = self.forward_once().await {
                        warn!("forward tick error: {e:#}");
                    }
                }
                _ = sync_tick.tick() => {
                    if let Err(e) = self.sync_known_peers().await {
                        warn!("sync tick error: {e:#}");
                    }
                }
                _ = gc_tick.tick() => {
                    match self.store.gc_expired().await {
                        Ok(deleted) if deleted > 0 => {
                            info!("GC: deleted {} expired messages", deleted);
                        }
                        Ok(_) => {}
                        Err(e) => warn!("GC tick error: {e:#}"),
                    }
                }
                _ = dial_tick.tick() => {
                    self.transport.redial_stored_targets();
                }
                _ = circuit_dial_tick.tick() => {
                    self.transport.try_deferred_circuit_dials();
                }
            }
        }
    }

    /// Dial peers returned by relay rendezvous (HTTP runs off the transport loop).
    /// Bucketed selective dial — contacts/QR first, then hash bucket (Phase D4).
    async fn dial_rendezvous_list(&mut self, remote_ids: Vec<String>) {
        if remote_ids.is_empty() {
            return;
        }
        let priority: Vec<String> =
            mycelium_core::bootstrap::load_persisted_dial_peers(&self.db_path)
                .iter()
                .filter_map(|addr| mycelium_core::bootstrap::peer_id_from_multiaddr(addr))
                .collect();
        let connected = self.transport.known_peers();
        let plan = mycelium_core::rendezvous_dial::default_rendezvous_plan(
            &self.local_peer_id,
            &remote_ids,
            &priority,
            &connected,
            self.rendezvous_dial_bucket,
        );
        self.rendezvous_dial_bucket = self.rendezvous_dial_bucket.wrapping_add(1);
        if plan.to_dial.is_empty() {
            tracing::debug!(
                "rendezvous bucket {}/{}: {} listed, no dials this tick",
                plan.bucket,
                plan.num_buckets,
                remote_ids.len()
            );
            return;
        }
        tracing::info!(
            "rendezvous bucket {}/{}: dialing {} of {} listed peer(s)",
            plan.bucket,
            plan.num_buckets,
            plan.to_dial.len(),
            remote_ids.len()
        );
        for peer in plan.to_dial {
            let Some(circuit) = mycelium_core::bootstrap::relay_circuit_multiaddr(&peer) else {
                continue;
            };
            if let Err(e) = self.transport.remember_and_dial(circuit).await {
                tracing::debug!("rendezvous dial {peer}: {e}");
            }
        }
    }

    async fn handle_command(&mut self, cmd: NodeCommand) -> anyhow::Result<()> {
        match cmd {
            NodeCommand::SendDirect { to_peer, body } => {
                self.send_direct_internal(to_peer, body.clone(), body.into_bytes())
                    .await?;
            }
            NodeCommand::SendDirectPayload {
                to_peer,
                body,
                payload,
            } => {
                self.send_direct_internal(to_peer, body, payload).await?;
            }
            NodeCommand::Broadcast { scope, body } => {
                self.broadcast_scoped(scope, body.clone(), body.into_bytes())
                    .await?;
            }
            NodeCommand::BroadcastPayload {
                scope,
                body,
                payload,
            } => {
                self.broadcast_scoped(scope, body, payload).await?;
            }
            NodeCommand::SendWire {
                to_peer,
                mut message,
            } => {
                if !self.transport.is_peer_connected(&to_peer) {
                    anyhow::bail!("not connected to peer {to_peer}");
                }
                if let WireMessage::EncryptedDirect { .. } = &mut message {
                    match &self.keypair {
                        Some(keypair) => {
                            mycelium_core::transport::sign_encrypted_direct(&mut message, keypair)?;
                        }
                        None => anyhow::bail!("cannot send EncryptedDirect without local keypair"),
                    }
                }
                self.transport.send_direct(to_peer, message).await?;
            }
            NodeCommand::SendWireOrQueueBle {
                to_peer,
                mut message,
            } => {
                if let WireMessage::EncryptedDirect { .. } = &mut message {
                    match &self.keypair {
                        Some(keypair) => {
                            mycelium_core::transport::sign_encrypted_direct(&mut message, keypair)?;
                        }
                        None => anyhow::bail!("cannot send EncryptedDirect without local keypair"),
                    }
                }
                if self.transport.is_peer_connected(&to_peer) {
                    self.transport.send_direct(to_peer, message).await?;
                } else {
                    anyhow::bail!(
                        "peer_not_connected: no delivery path to {to_peer} (connect via Wi‑Fi, relay, or BLE first)"
                    );
                }
            }
            NodeCommand::EncryptE2ePayload {
                to_peer,
                wrapped,
                reply,
            } => {
                let legacy_pk = self.peer_x25519.read().await.get(&to_peer).copied();
                let result = self.encrypt_e2e_for_peer_sync(&to_peer, &wrapped, legacy_pk);
                if let Some(tx) = reply {
                    let _ = tx.send(result);
                }
            }
            NodeCommand::IngestBleFrame { from_peer, frame } => {
                if self.try_ingest_ble_assembled(&from_peer, &frame).await? {
                    return Ok(());
                }
                let wire = decode_peer_ble_wire_frame(&frame)?;
                self.handle_incoming_direct(from_peer, wire).await?;
            }
            NodeCommand::DrainBleOutbound {
                to_peer,
                max,
                reply,
            } => {
                let mut drained = Vec::new();
                let mut rest = VecDeque::new();
                while let Some((peer, frame)) = self.ble_outbound.pop_front() {
                    let matches = to_peer.as_ref().is_none_or(|p| p == &peer);
                    if matches && drained.len() < max {
                        drained.push((peer, frame));
                    } else {
                        rest.push_back((peer, frame));
                    }
                }
                self.ble_outbound = rest;
                if let Some(tx) = reply {
                    let _ = tx.send(drained);
                }
            }
            NodeCommand::RegisterBleLink { peer_id, device_id } => {
                if self
                    .transport
                    .register_ble_link(peer_id.clone(), &device_id)
                    .is_some()
                {
                    for addr in self.transport.ble_peer_addrs() {
                        if addr.contains(&peer_id) {
                            let mut addrs = self.listen_addrs.write().await;
                            if !addrs.iter().any(|a| a == &addr) {
                                addrs.push(addr);
                            }
                        }
                    }
                    self.handle_transport_event(TransportEvent::PeerUp { peer_id })
                        .await?;
                }
            }
            NodeCommand::BleStreamInbound { from_peer, frame } => {
                self.transport.ingest_ble_stream_frame(&from_peer, &frame)?;
                for ev in self.transport.drain_pending_ble_events() {
                    self.handle_transport_event(ev).await?;
                }
            }
            NodeCommand::DrainBleStreamOutbound {
                to_peer,
                max,
                reply,
            } => {
                let drained = self.transport.drain_ble_stream_outbound(&to_peer, max);
                if let Some(tx) = reply {
                    let _ = tx.send(drained);
                }
            }
            NodeCommand::ListPeers => {
                let peers = self.transport.known_peers();
                *self.peers.write().await = peers.clone();
                info!("known peers: {}", peers.len());
                for peer in peers {
                    info!("peer {}", peer);
                }
            }
            NodeCommand::ConnectedPeers { reply } => {
                let peers: Vec<String> = self
                    .transport
                    .known_peers()
                    .into_iter()
                    .filter(|p| self.transport.is_peer_connected(p))
                    .collect();
                if let Some(tx) = reply {
                    let _ = tx.send(peers);
                }
            }
            NodeCommand::AddBootstrapPeer { multiaddr } => {
                if let Err(e) =
                    mycelium_core::bootstrap::persist_dial_peer(&self.db_path, &multiaddr)
                {
                    warn!("could not persist dial peer: {e}");
                }
                self.transport.remember_and_dial(multiaddr).await?;
            }
            NodeCommand::SeedPeerEncKey {
                peer_id,
                enc_pubkey_hex,
            } => {
                let existing = self.peer_x25519.read().await.get(&peer_id).copied();
                match crypto::verify_enc_pubkey_update(existing.as_ref(), &enc_pubkey_hex) {
                    Ok(bytes) => {
                        persist_peer_enc_key(
                            self.peer_x25519.clone(),
                            self.peer_enc_store.clone(),
                            peer_id.clone(),
                            bytes,
                        )
                        .await;
                        info!("seeded enc key for {peer_id} from invite");
                        if self.transport.is_peer_connected(&peer_id) {
                            if let Err(e) = self.send_peer_info(peer_id.clone()).await {
                                warn!("could not send PeerInfo after invite seed for {peer_id}: {e}");
                            }
                        }
                    }
                    Err(e) => {
                        warn!("invalid enc_pubkey_hex in invite for {peer_id}: {e}");
                    }
                }
            }
            NodeCommand::SeedPeerRatchetBundle { peer_id, bundle } => {
                use mycelium_core::crypto::ratchet;
                if !ratchet::verify_prekey_bundle(&bundle) {
                    warn!("rejecting invalid ratchet bundle seed for {peer_id}");
                    return Ok(());
                }
                if let Some(enc) = self.peer_x25519.read().await.get(&peer_id).copied() {
                    if bundle.identity_key != enc {
                        warn!(
                            "rejecting ratchet bundle for {peer_id}: identity_key != seeded enc key"
                        );
                        return Ok(());
                    }
                }
                if let Err(e) = self.ratchet_store.save_peer_bundle(&peer_id, &bundle) {
                    warn!("failed to persist ratchet bundle for {peer_id}: {e}");
                } else {
                    info!("seeded ratchet bundle for {peer_id} from invite");
                }
            }
            NodeCommand::BuildSignedInviteV3 {
                peer_id,
                direct_multiaddr,
                reply,
            } => {
                let bundle = self
                    .local_prekeys
                    .lock()
                    .expect("local prekeys lock") // INVARIANT: mutex not poisoned
                    .public_bundle(self.enc_keypair.as_core_keypair());
                let result = mycelium_core::invite::encode_signed_invite_v3(
                    &self.account_id,
                    &peer_id,
                    &self.enc_keypair.public_hex(),
                    self.transport_epoch,
                    self.account_signing_key.as_ref(),
                    direct_multiaddr.as_deref(),
                    Some(&bundle),
                );
                let _ = reply.send(result);
            }
            NodeCommand::SubscribeScope(scope) => {
                self.subscribed_scopes.write().await.insert(scope.clone());
                if !scope.contains('*') {
                    if let Err(e) = self.transport.subscribe_scope(scope).await {
                        warn!("failed to subscribe to gossip topic: {e}");
                    }
                }
            }
            NodeCommand::UnsubscribeScope(scope) => {
                self.subscribed_scopes.write().await.remove(&scope);
                if !scope.contains('*') {
                    if let Err(e) = self.transport.unsubscribe_scope(scope).await {
                        warn!("failed to unsubscribe from gossip topic: {e}");
                    }
                }
            }
            NodeCommand::SubscribeBulletinScope(scope) => {
                let scope = scope.trim().to_string();
                if scope.is_empty() {
                    return Ok(());
                }
                {
                    let mut subs = self.bulletin_subscriptions.write().await;
                    if !subs.iter().any(|s| s == &scope) {
                        subs.push(scope.clone());
                    }
                }
                self.subscribed_scopes.write().await.insert(scope.clone());
                if !scope.contains('*') {
                    if let Err(e) = self.transport.subscribe_scope(scope.clone()).await {
                        warn!("failed to subscribe to bulletin scope: {e}");
                    } else {
                        info!("bulletin subscribed: {scope}");
                    }
                }
                self.announce_subscribed_scopes_to_peers().await?;
            }
            NodeCommand::UnsubscribeBulletinScope(scope) => {
                self.bulletin_subscriptions
                    .write()
                    .await
                    .retain(|s| s != &scope);
                self.subscribed_scopes.write().await.remove(&scope);
                if !scope.contains('*') {
                    if let Err(e) = self.transport.unsubscribe_scope(scope).await {
                        warn!("failed to unsubscribe from bulletin scope: {e}");
                    }
                }
            }
            NodeCommand::GcNow { reply } => {
                let deleted = self.store.gc_expired().await?;
                info!("manual GC deleted {} messages", deleted);
                if let Some(tx) = reply {
                    let _ = tx.send(deleted);
                }
            }
            NodeCommand::StoreStats { reply } => {
                let stats = self.store.stats().await?;
                info!(
                    "store stats: count={} oldest_ms={}",
                    stats.count, stats.oldest_ms
                );
                if let Some(tx) = reply {
                    let _ = tx.send(stats);
                }
            }
            NodeCommand::SetEnergyState(state) => {
                self.node_state = state;
                info!("energy state updated to {:?}", self.node_state);
            }
            NodeCommand::SetRendezvousEnabled(enabled) => {
                self.rendezvous_enabled.store(enabled, Ordering::Relaxed);
                let local = self.local_peer_id.clone();
                tokio::task::spawn_blocking(move || {
                    mycelium_core::rendezvous::set_relay_rendezvous_registration(
                        &local, enabled, None,
                    );
                });
                info!(
                    "rendezvous discovery {}",
                    if enabled { "enabled" } else { "disabled" }
                );
            }
            NodeCommand::SetInternetEnabled(enabled) => {
                self.internet_enabled.store(enabled, Ordering::Relaxed);
                if !enabled {
                    self.rendezvous_enabled.store(false, Ordering::Relaxed);
                    let local = self.local_peer_id.clone();
                    tokio::task::spawn_blocking(move || {
                        mycelium_core::rendezvous::set_relay_rendezvous_registration(
                            &local, false, None,
                        );
                    });
                }
                if let Some(tx) = &self.connectivity_mode_tx {
                    let mode = if enabled {
                        ConnectivityMode::Internet
                    } else {
                        ConnectivityMode::MeshOnly
                    };
                    let _ = tx.send(mode);
                }
                info!(
                    "internet connectivity {}",
                    if enabled {
                        "enabled (relay + mesh)"
                    } else {
                        "disabled — local mesh only"
                    }
                );
            }
            NodeCommand::SetCrowdMode(enabled) => {
                self.crowd_mode.store(enabled, Ordering::Relaxed);
                info!(
                    "crowd mode {}",
                    if enabled { "enabled" } else { "disabled" }
                );
            }
        }
        Ok(())
    }

    async fn try_ingest_ble_assembled(
        &mut self,
        from_peer: &str,
        frame: &[u8],
    ) -> anyhow::Result<bool> {
        if frame.len() >= 4 && frame[..4] == mycelium_core::ble_stream::STREAM_MAGIC {
            self.transport.ingest_ble_stream_frame(from_peer, frame)?;
            for ev in self.transport.drain_pending_ble_events() {
                self.handle_transport_event(ev).await?;
            }
            return Ok(true);
        }
        Ok(false)
    }

    fn queue_ble_wire(&mut self, to_peer: String, wire: WireMessage) {
        match mycelium_core::ble_mesh::encode_wire(&wire) {
            Ok(frame) => {
                self.ble_outbound.push_back((to_peer.clone(), frame));
                while self.ble_outbound.len() > self.ble_outbound_cap {
                    self.ble_outbound.pop_front();
                }
                info!(
                    "queued BLE frame for {to_peer} ({} bytes)",
                    self.ble_outbound.back().map(|(_, f)| f.len()).unwrap_or(0)
                );
            }
            Err(e) => warn!("BLE encode for {to_peer}: {e:#}"),
        }
    }

    async fn send_direct_internal(
        &mut self,
        to_peer: String,
        body: String,
        payload: Vec<u8>,
    ) -> anyhow::Result<()> {
        let mut envelope = mycelium_core::data::Envelope::new(
            self.local_peer_id.clone(),
            Some(to_peer.clone()),
            payload,
        );
        if let Some(keypair) = &self.keypair {
            let _ = envelope.sign(keypair);
        }
        let msg = DirectMessage {
            envelope,
            body: body.clone(),
        };
        let wire = WireMessage::Data(msg.clone());
        let _ = self
            .ingest
            .ingest(&mut self.seen_cache, &msg, false)
            .await?;
        let peers = self.transport.known_peers();
        if peers.iter().any(|p| p == &to_peer) {
            self.enqueue(to_peer.clone(), msg).await?;
            info!("direct message queued for {}", to_peer);
        } else if peers.is_empty() {
            warn!("no relay candidates available for {}", to_peer);
            self.queue_ble_wire(to_peer.clone(), wire);
        } else {
            let peer_strikes: HashMap<String, u8> = self
                .peer_reputations
                .lock()
                .expect("reputation lock") // INVARIANT: mutex not poisoned; we never panic while holding it
                .iter()
                .map(|(peer, rep)| (peer.clone(), rep.strikes))
                .collect();
            let relay_candidates = security::select_relay_candidates(
                &peers,
                &to_peer,
                &peer_strikes,
                self.max_relay_fanout,
            );
            if relay_candidates.is_empty() {
                warn!("SD-031: no suitable relay candidates for {}", to_peer);
            } else {
                info!(
                    "SD-031: relaying to {} candidate(s) toward {}",
                    relay_candidates.len(),
                    to_peer
                );
                for peer in relay_candidates {
                    self.enqueue(peer, msg.clone()).await?;
                }
            }
            info!("message queued for relay toward {}", to_peer);
            if !self.transport.is_peer_connected(&to_peer) {
                self.queue_ble_wire(to_peer.clone(), wire);
            }
        }
        Ok(())
    }

    async fn publish_identity_rotation(
        &mut self,
        rot: mycelium_core::identity_rotation::IdentityRotationGossip,
    ) -> anyhow::Result<()> {
        let payload = rot.encode()?;
        self.broadcast_scoped(
            "mycelium/identity/v1".into(),
            format!("[identity:{}]", rot.account_id),
            payload,
        )
        .await
    }

    async fn broadcast_scoped(
        &mut self,
        scope: String,
        body: String,
        payload: Vec<u8>,
    ) -> anyhow::Result<()> {
        let mut targets: HashSet<String> = self.targets_for_scope(&scope).into_iter().collect();
        // Bulletin posts must reach every connected peer — ScopeAnnounce can be
        // missed when peer_cap has not recorded the sender yet (asymmetric LAN sync).
        if body.starts_with("[bulletin:") {
            if let Ok(peers) = self.peers.try_read() {
                for peer in peers.iter() {
                    if !mycelium_core::bootstrap::is_relay_peer(peer) {
                        targets.insert(peer.clone());
                    }
                }
            }
        }
        for to_peer in targets {
            let mut envelope = mycelium_core::data::Envelope::new(
                self.local_peer_id.clone(),
                None,
                payload.clone(),
            );
            if let Some(keypair) = &self.keypair {
                let _ = envelope.sign(keypair);
            }
            let msg = DirectMessage {
                envelope,
                body: body.clone(),
            };
            match self.ingest.ingest(&mut self.seen_cache, &msg, false).await {
                Ok(true) => {
                    if let Err(e) = self.enqueue(to_peer.clone(), msg).await {
                        warn!("broadcast enqueue to {to_peer}: {e:#}");
                    }
                }
                Ok(false) => {}
                Err(e) => warn!("broadcast ingest for {scope}: {e:#}"),
            }
        }
        // Always gossip so remote peers receive posts even when ScopeAnnounce was missed.
        if let Err(e) = self.transport.publish_scoped(scope, payload).await {
            warn!("gossip publish for bulletin scope (non-fatal): {e:#}");
        }
        Ok(())
    }

    async fn announce_subscribed_scopes_to_peers(&mut self) -> anyhow::Result<()> {
        let scopes: Vec<String> = self
            .subscribed_scopes
            .read()
            .await
            .iter()
            .cloned()
            .collect();
        if scopes.is_empty() {
            return Ok(());
        }
        let peers: Vec<String> = self
            .transport
            .known_peers()
            .into_iter()
            .filter(|p| !mycelium_core::bootstrap::is_relay_peer(p))
            .collect();
        for peer in peers {
            if let Err(err) = self
                .transport
                .send_direct(
                    peer.clone(),
                    WireMessage::ScopeAnnounce {
                        scopes: scopes.clone(),
                    },
                )
                .await
            {
                warn!("scope announce to {peer} failed: {err}");
            }
        }
        Ok(())
    }

    async fn handle_transport_event(&mut self, event: TransportEvent) -> anyhow::Result<()> {
        match event {
            TransportEvent::Listening { address } => {
                info!("listening on {address}");
                {
                    let mut addrs = self.listen_addrs.write().await;
                    if !addrs.iter().any(|a| a == &address) {
                        addrs.push(address);
                    }
                }
            }
            TransportEvent::PeerUp { peer_id } => {
                if mycelium_core::bootstrap::is_relay_peer(&peer_id) {
                    return Ok(());
                }
                info!("discovered peer={peer_id}");
                {
                    let mut peers = self.peers.write().await;
                    if !peers.iter().any(|p| p == &peer_id) {
                        peers.push(peer_id.clone());
                    }
                }
                self.send_scope_announce(peer_id.clone()).await?;
                self.send_peer_info(peer_id.clone()).await?;
                self.send_sync_bloom(peer_id).await?;
            }
            TransportEvent::PeerDown { peer_id } => {
                info!("expired peer={peer_id}");
                {
                    let mut peers = self.peers.write().await;
                    peers.retain(|p| p != &peer_id);
                }
                self.peer_scopes.remove(&peer_id);
            }
            TransportEvent::DirectReceived { from_peer, message } => {
                self.handle_incoming_direct(from_peer, message).await?;
            }
            TransportEvent::DirectAck { from_peer, ack } => {
                info!(
                    "delivery ack from {} message_id={} accepted={}",
                    from_peer, ack.message_id, ack.accepted
                );
            }
            TransportEvent::SendFailure { to_peer, reason } => {
                warn!("direct send to {} failed: {}", to_peer, reason);
            }
            TransportEvent::ConnectivityChanged { mode } => {
                info!("connectivity mode from transport: {:?}", mode);
            }
            TransportEvent::ScopedReceived {
                from_peer,
                scope,
                payload,
            } => {
                let scope_obj = Scope(scope.clone());
                let scopes = self.subscribed_scopes.read().await;
                if !scopes.iter().any(|pattern| scope_obj.matches(pattern)) {
                    return Ok(());
                }
                let now = now_ms();
                if crate::ingest_guard::is_sos_scope(&scope) {
                    let geohash = mycelium_core::sos::geohash_from_payload_bytes(&payload);
                    if let Err(reason) =
                        self.ingest_guard
                            .check_sos_gossip(&from_peer, geohash.as_deref(), now)
                    {
                        self.record_ingest_reject(reason);
                        return Ok(());
                    }
                }
                if let Err(reason) =
                    self.ingest_guard
                        .check_gossip_payload(&from_peer, payload.len(), now)
                {
                    self.record_ingest_reject(reason);
                    return Ok(());
                }
                if scope.starts_with("mycelium/group/")
                    && bincode::deserialize::<WireMessage>(&payload)
                        .map(|wm| matches!(wm, WireMessage::EncryptedGroup { .. }))
                        .unwrap_or(false)
                {
                    let envelope = mycelium_core::data::Envelope::new_unsigned(
                        from_peer.clone(),
                        None,
                        payload,
                    );
                    if let Err(reason) = self.ingest_guard.check_gossip_message(
                        &from_peer,
                        &envelope.id.0,
                        envelope.created_at_ms,
                        now,
                    ) {
                        self.record_ingest_reject(reason);
                        return Ok(());
                    }
                    let stored = DirectMessage {
                        envelope,
                        body: "[mycelium:group]".into(),
                    };
                    let should_process = self
                        .ingest
                        .ingest(&mut self.seen_cache, &stored, false)
                        .await?;
                    if should_process {
                        let _ = self.incoming_tx.send(stored);
                    }
                    return Ok(());
                }
                // App store listings are bincode payloads; they may decode as UTF-8 by chance.
                // Always route this scope through the app pipeline with a stable body tag.
                if scope == "mycelium/appstore/v1" {
                    let envelope = mycelium_core::data::Envelope::new_unsigned(
                        from_peer.clone(),
                        None,
                        payload,
                    );
                    if let Err(reason) = self.ingest_guard.check_gossip_message(
                        &from_peer,
                        &envelope.id.0,
                        envelope.created_at_ms,
                        now,
                    ) {
                        self.record_ingest_reject(reason);
                        return Ok(());
                    }
                    let stored = DirectMessage {
                        envelope,
                        body: "[appstore]".into(),
                    };
                    let should_process = self
                        .ingest
                        .ingest(&mut self.seen_cache, &stored, false)
                        .await?;
                    if should_process {
                        let _ = self.incoming_tx.send(stored);
                    }
                    return Ok(());
                }
                if scope == "mycelium/proximity/v1" {
                    let envelope = mycelium_core::data::Envelope::new_unsigned(
                        from_peer.clone(),
                        None,
                        payload,
                    );
                    if let Err(reason) = self.ingest_guard.check_gossip_message(
                        &from_peer,
                        &envelope.id.0,
                        envelope.created_at_ms,
                        now,
                    ) {
                        self.record_ingest_reject(reason);
                        return Ok(());
                    }
                    let stored = DirectMessage {
                        envelope,
                        body: "[proximity]".into(),
                    };
                    let should_process = self
                        .ingest
                        .ingest(&mut self.seen_cache, &stored, false)
                        .await?;
                    if should_process {
                        let _ = self.incoming_tx.send(stored);
                    }
                    return Ok(());
                }
                let scope_subscribed = scopes.iter().any(|pattern| scope_obj.matches(pattern));
                if scope_subscribed
                    && (payload.starts_with(b"enc1:") || std::str::from_utf8(&payload).is_err())
                {
                    let envelope = mycelium_core::data::Envelope::new_unsigned(
                        from_peer.clone(),
                        None,
                        payload,
                    );
                    if let Err(reason) = self.ingest_guard.check_gossip_message(
                        &from_peer,
                        &envelope.id.0,
                        envelope.created_at_ms,
                        now,
                    ) {
                        self.record_ingest_reject(reason);
                        return Ok(());
                    }
                    let stored = DirectMessage {
                        envelope,
                        body: format!("[bulletin:{scope}]"),
                    };
                    let should_process = self
                        .ingest
                        .ingest(&mut self.seen_cache, &stored, false)
                        .await?;
                    if should_process {
                        let _ = self.incoming_tx.send(stored);
                    }
                    return Ok(());
                }
                if let Ok(body) = String::from_utf8(payload.clone()) {
                    let envelope = mycelium_core::data::Envelope::new_unsigned(
                        from_peer.clone(),
                        None,
                        payload,
                    );
                    if let Err(reason) = self.ingest_guard.check_gossip_message(
                        &from_peer,
                        &envelope.id.0,
                        envelope.created_at_ms,
                        now,
                    ) {
                        self.record_ingest_reject(reason);
                        return Ok(());
                    }
                    let stored = DirectMessage {
                        envelope,
                        body: body.clone(),
                    };
                    let should_process = self
                        .ingest
                        .ingest(&mut self.seen_cache, &stored, false)
                        .await?;
                    if should_process {
                        info!("gossip recv from {} [{}]: {}", from_peer, scope, body);
                        let _ = self.incoming_tx.send(stored);
                    }
                }
            }
        }
        Ok(())
    }

    async fn handle_incoming_direct(
        &mut self,
        from_peer: String,
        message: WireMessage,
    ) -> anyhow::Result<()> {
        if self.is_throttled(&from_peer) {
            return Ok(());
        }
        if let WireMessage::EncryptedDirect { mesh_signature, .. } = &message {
            if mesh_signature.is_none() {
                warn!("DROPPING EncryptedDirect from {from_peer}: no signature");
                let mut metrics = self.metrics.write().await;
                metrics.messages_dropped_no_sig += 1;
                return Ok(());
            }
            let Ok(sender_id) = from_peer.parse::<PeerId>() else {
                warn!("DROPPING EncryptedDirect: unparseable peer_id {from_peer}");
                return Ok(());
            };
            if !mycelium_core::transport::verify_encrypted_direct(&message, &sender_id) {
                warn!("DROPPING EncryptedDirect from {from_peer}: invalid signature");
                let mut metrics = self.metrics.write().await;
                metrics.messages_dropped_invalid_sig += 1;
                return Ok(());
            }
        }
        match message {
            WireMessage::Data(message) => {
                match security::validate_data_message_signature(&message, now_ms()) {
                    Err(Sd030DropReason::NoSignature) => {
                        warn!(
                            "SD-030: dropping unsigned message from {} (payload {} bytes)",
                            from_peer,
                            message.envelope.payload.len()
                        );
                        let mut metrics = self.metrics.write().await;
                        metrics.messages_dropped_no_sig += 1;
                        return Ok(());
                    }
                    Err(Sd030DropReason::InvalidSignature) => {
                        warn!("SD-030: dropping invalid-sig message from {}", from_peer);
                        let mut metrics = self.metrics.write().await;
                        metrics.messages_dropped_invalid_sig += 1;
                        self.register_strike(&from_peer);
                        return Ok(());
                    }
                    Err(Sd030DropReason::UnparseableAuthor) => {
                        warn!(
                            "SD-030: dropping message — unparseable author {}",
                            message.envelope.from_peer
                        );
                        let mut metrics = self.metrics.write().await;
                        metrics.messages_dropped_no_sig += 1;
                        return Ok(());
                    }
                    Ok(()) => {
                        if message.envelope.signature.is_none()
                            && !security::is_gossip_relay_body(&message.body)
                        {
                            warn!(
                                "SD-030: unsigned message from {} — will drop after grace period",
                                from_peer
                            );
                        }
                    }
                }
                if message.envelope.hop_count >= message.envelope.max_hops {
                    let mut metrics = self.metrics.write().await;
                    metrics.messages_dropped_hops += 1;
                    return Ok(());
                }
                let author = message.envelope.signature_author_peer();
                let now = now_ms();
                if let Err(reason) = self.ingest_guard.allow_direct(author, &message, now) {
                    self.record_ingest_reject(reason);
                    if matches!(reason, IngestReject::RateLimit | IngestReject::Replay) {
                        self.register_strike(&from_peer);
                    }
                    return Ok(());
                }
                let should_process = self
                    .ingest
                    .ingest(&mut self.seen_cache, &message, false)
                    .await?;
                if !should_process {
                    self.register_strike(&from_peer);
                    return Ok(());
                }
                self.reward_peer(&from_peer);
                let _ = self.incoming_tx.send(message.clone());
                info!("direct recv from {}: {}", from_peer, message.body);

                if message
                    .envelope
                    .to_peer
                    .as_deref()
                    .is_some_and(|to| to == self.local_peer_id)
                {
                    let mut metrics = self.metrics.write().await;
                    metrics.messages_delivered_local += 1;
                    return Ok(());
                }
                if message.is_expired(now_ms()) {
                    let mut metrics = self.metrics.write().await;
                    metrics.messages_dropped_ttl += 1;
                    return Ok(());
                }
                if !self
                    .energy_policy
                    .can_forward(self.node_state, message.envelope.priority)
                {
                    return Ok(());
                }

                let mut peers = self.transport.known_peers();
                peers.retain(|peer| peer != &from_peer);
                if let Some(target) = message.envelope.to_peer.clone() {
                    if peers.iter().any(|p| p == &target) {
                        let mut forwarded = message.clone();
                        forwarded.envelope.hop_count =
                            forwarded.envelope.hop_count.saturating_add(1);
                        self.apply_relay_forward_mask(&mut forwarded);
                        self.enqueue(target, forwarded).await?;
                        return Ok(());
                    }
                    let crowd = self.crowd_mode.load(Ordering::Relaxed);
                    if !crowd || message.body.starts_with("[bulletin:") {
                        self.find_peer_via_kad(&target).await;
                    }
                }
                let decision = self.forwarding_policy.decide(
                    &message,
                    &self.local_peer_id,
                    &peers,
                    self.node_state,
                );
                debug!(
                    "forward decision for message_id={} is {:?}",
                    message.envelope.id.0, decision
                );
                match decision {
                    ForwardDecision::ForwardTo(targets) => {
                        for target in targets {
                            let mut forwarded = message.clone();
                            forwarded.envelope.hop_count =
                                forwarded.envelope.hop_count.saturating_add(1);
                            self.apply_relay_forward_mask(&mut forwarded);
                            self.enqueue(target, forwarded).await?;
                        }
                    }
                    ForwardDecision::Drop(reason) => {
                        warn!(
                            "dropping message_id={} reason={}",
                            message.envelope.id.0, reason
                        );
                    }
                    _ => {}
                }
            }
            WireMessage::SyncBloom { bloom, count } => {
                let Some(remote_bloom) = BloomFilter::from_bytes(&bloom) else {
                    return Ok(());
                };

                let own_ids = self.store.list_ids_window(Duration::from_secs(600)).await?;

                let mut send_to_remote: Vec<String> = Vec::new();
                for id in &own_ids {
                    if !remote_bloom.contains(id) {
                        send_to_remote.push(id.clone());
                    }
                }

                if own_ids.len() as f64 > (count as f64 * 0.8) && !send_to_remote.is_empty() {
                    self.transport
                        .send_direct(
                            from_peer.clone(),
                            WireMessage::SyncIds {
                                ids: send_to_remote.into_iter().take(256).collect(),
                            },
                        )
                        .await?;
                } else if (own_ids.len() as f64) < (count as f64 * 0.8) {
                    let mut own_bloom = BloomFilter::new();
                    for id in &own_ids {
                        own_bloom.insert(id);
                    }
                    self.transport
                        .send_direct(
                            from_peer.clone(),
                            WireMessage::SyncBloom {
                                bloom: own_bloom.to_bytes(),
                                count: own_ids.len() as u64,
                            },
                        )
                        .await?;
                }
            }
            WireMessage::SyncIds { ids } => {
                let mut missing = Vec::new();
                for id in ids.iter().take(256) {
                    if !self.store.contains(id).await? {
                        missing.push(id.clone());
                    }
                }
                if !missing.is_empty() {
                    self.transport
                        .send_direct(from_peer, WireMessage::SyncRequest { ids: missing })
                        .await?;
                }
            }
            WireMessage::SyncRequest { ids } => {
                let mut messages = Vec::new();
                for id in ids.iter().take(128) {
                    if let Some(missing) = self.store.load_by_id(id).await? {
                        messages.push(missing);
                    }
                }
                if !messages.is_empty() {
                    self.transport
                        .send_direct(from_peer, WireMessage::SyncData { messages })
                        .await?;
                }
            }
            WireMessage::SyncData { messages } => {
                for message in messages {
                    let _ = self
                        .ingest
                        .ingest(&mut self.seen_cache, &message, false)
                        .await?;
                }
            }
            WireMessage::PeerInfo {
                enc_pubkey_hex,
                supported_scopes,
                ratchet_registration_id,
                ratchet_signed_prekey_id,
                ratchet_signed_prekey_hex,
                ratchet_signed_prekey_sig_hex,
                ratchet_one_time_prekey_id,
                ratchet_one_time_prekey_hex,
                ..
            } => {
                let existing = self.peer_x25519.read().await.get(&from_peer).copied();
                match crypto::verify_enc_pubkey_update(existing.as_ref(), &enc_pubkey_hex) {
                    Ok(bytes) => {
                        persist_peer_enc_key(
                            self.peer_x25519.clone(),
                            self.peer_enc_store.clone(),
                            from_peer.clone(),
                            bytes,
                        )
                        .await;
                    }
                    Err(e) => {
                        warn!(
                            "rejecting PeerInfo enc key for {from_peer}: {e} (invite/TOFU conflict)"
                        );
                        return Ok(());
                    }
                }
                if let (Some(reg_id), Some(spk_id), Some(spk_hex), Some(sig_hex)) = (
                    ratchet_registration_id,
                    ratchet_signed_prekey_id,
                    ratchet_signed_prekey_hex.as_deref(),
                    ratchet_signed_prekey_sig_hex.as_deref(),
                ) {
                    if let Ok(identity_key) =
                        crypto::parse_x25519_public_hex(&enc_pubkey_hex).map(|pk| *pk.as_bytes())
                    {
                        match mycelium_core::crypto::ratchet::prekey_bundle_from_peer_info(
                            reg_id,
                            identity_key,
                            spk_id,
                            spk_hex,
                            sig_hex,
                            ratchet_one_time_prekey_id,
                            ratchet_one_time_prekey_hex.as_deref(),
                        ) {
                            Ok(bundle)
                                if mycelium_core::crypto::ratchet::verify_prekey_bundle(
                                    &bundle,
                                ) =>
                            {
                                if let Err(e) =
                                    self.ratchet_store.save_peer_bundle(&from_peer, &bundle)
                                {
                                    warn!("failed to persist ratchet bundle for {from_peer}: {e}");
                                }
                            }
                            Ok(_) => {
                                warn!("rejecting invalid ratchet prekey sig from {from_peer}");
                            }
                            Err(e) => {
                                warn!("invalid ratchet bundle from {from_peer}: {e}");
                            }
                        }
                    }
                }
                let validated: HashSet<String> = supported_scopes
                    .into_iter()
                    .filter(|s| !s.is_empty() && s.len() <= 128 && !s.contains('\0'))
                    .take(32)
                    .collect();
                if !validated.is_empty() {
                    self.peer_scopes
                        .entry(from_peer.clone())
                        .or_default()
                        .extend(validated);
                }
                info!("stored peer info for {from_peer}");
            }
            WireMessage::EncryptedDirect {
                to_peer,
                sender_enc_pubkey,
                encrypted_payload,
                mesh_signature,
                hop_count,
                max_hops,
                e2e_version,
            } => {
                if to_peer == self.local_peer_id {
                    let logical =
                        resolve_logical_sender(&self.peer_x25519, &sender_enc_pubkey, &from_peer)
                            .await;
                    let plain = self.decrypt_e2e_sync(
                        &logical,
                        &from_peer,
                        &encrypted_payload,
                        e2e_version,
                    )?;
                    let (logical_from, app_payload) =
                        mycelium_core::e2e_direct_wrap::unwrap_inner(&plain, &from_peer);
                    match crypto::parse_x25519_public_hex(&sender_enc_pubkey) {
                        Ok(pk) => {
                            let bytes = *pk.as_bytes();
                            persist_peer_enc_key(
                                self.peer_x25519.clone(),
                                self.peer_enc_store.clone(),
                                logical_from.clone(),
                                bytes,
                            )
                            .await;
                        }
                        Err(e) => {
                            warn!(
                                "invalid sender_enc_pubkey on EncryptedDirect from {from_peer}: {e}"
                            );
                        }
                    }
                    let delivered_e2e = mycelium_core::crypto::ratchet::wire_e2e_version(
                        &encrypted_payload,
                        e2e_version,
                    );
                    let envelope = mycelium_core::data::Envelope::new_with_e2e_version(
                        logical_from,
                        Some(self.local_peer_id.clone()),
                        app_payload,
                        delivered_e2e,
                    );
                    let dm = DirectMessage {
                        envelope,
                        body: "[encrypted]".into(),
                    };
                    let logical = dm.envelope.from_peer.clone();
                    let _ = self.incoming_tx.send(dm);
                    info!(
                        "encrypted direct delivered from {} (logical {})",
                        from_peer, logical
                    );
                    let mut metrics = self.metrics.write().await;
                    metrics.messages_delivered_local += 1;
                } else if hop_count < max_hops {
                    let mut forward = WireMessage::EncryptedDirect {
                        to_peer,
                        sender_enc_pubkey,
                        encrypted_payload,
                        mesh_signature,
                        hop_count: hop_count.saturating_add(1),
                        max_hops,
                        e2e_version,
                    };
                    let relay_kp = self.relay_identity.signing_keypair();
                    let _ =
                        mycelium_core::transport::sign_encrypted_direct(&mut forward, &relay_kp);
                    self.forward_wire_to_others(&from_peer, &forward).await?;
                } else {
                    let mut metrics = self.metrics.write().await;
                    metrics.messages_dropped_hops += 1;
                }
            }
            WireMessage::EncryptedGroup { .. } => {
                self.forward_wire_to_others(&from_peer, &message).await?;
            }
            WireMessage::ScopeAnnounce { scopes } => {
                if mycelium_core::bootstrap::is_relay_peer(&from_peer) {
                    return Ok(());
                }
                let validated_scopes: HashSet<String> = scopes
                    .into_iter()
                    .filter(|s| !s.is_empty() && s.len() <= 128 && !s.contains('\0'))
                    .take(32)
                    .collect();
                if validated_scopes.is_empty() {
                    return Ok(());
                }
                self.peer_scopes
                    .entry(from_peer)
                    .or_default()
                    .extend(validated_scopes);
            }
        }
        Ok(())
    }

    async fn forward_once(&mut self) -> anyhow::Result<()> {
        self.maybe_rotate_relay_keypair();
        self.evict_expired();
        let budget = match self.node_state {
            NodeState::Active => 10,
            NodeState::Intermittent => 4,
            NodeState::Passive => 1,
        };
        for _ in 0..budget {
            let Some(pending) = self.pending.pop_front() else {
                break;
            };
            if pending.message.is_expired(now_ms()) {
                continue;
            }
            if !self
                .energy_policy
                .can_forward(self.node_state, pending.message.envelope.priority)
            {
                continue;
            }
            if let Err(err) = self
                .transport
                .send_direct(
                    pending.to_peer.clone(),
                    WireMessage::Data(pending.message.clone()),
                )
                .await
            {
                warn!("forward send failed to {}: {}", pending.to_peer, err);
            } else {
                let mut metrics = self.metrics.write().await;
                metrics.messages_forwarded += 1;
            }
        }
        let mut metrics = self.metrics.write().await;
        metrics.pending_queue_size = self.pending.len();
        metrics.seen_cache_size = self.seen_cache.len();
        self.update_transport_metrics(&mut metrics);
        Ok(())
    }

    async fn sync_known_peers(&mut self) -> anyhow::Result<()> {
        let peers = self.transport.known_peers();
        for peer in peers {
            self.send_sync_bloom(peer).await?;
        }
        Ok(())
    }

    async fn send_sync_bloom(&mut self, peer_id: String) -> anyhow::Result<()> {
        let ids = self.store.list_ids_window(Duration::from_secs(600)).await?;
        if ids.is_empty() {
            return Ok(());
        }
        let mut bloom = BloomFilter::new();
        for id in &ids {
            bloom.insert(id);
        }
        self.transport
            .send_direct(
                peer_id,
                WireMessage::SyncBloom {
                    bloom: bloom.to_bytes(),
                    count: ids.len() as u64,
                },
            )
            .await
    }

    async fn send_scope_announce(&mut self, peer_id: String) -> anyhow::Result<()> {
        let scopes = self
            .subscribed_scopes
            .read()
            .await
            .iter()
            .cloned()
            .collect();
        self.transport
            .send_direct(peer_id, WireMessage::ScopeAnnounce { scopes })
            .await
    }

    async fn send_peer_info(&mut self, peer_id: String) -> anyhow::Result<()> {
        let supported_scopes: Vec<String> = self
            .subscribed_scopes
            .read()
            .await
            .iter()
            .cloned()
            .collect();
        let bundle = self
            .local_prekeys
            .lock()
            .expect("local prekeys lock") // INVARIANT: mutex not poisoned
            .public_bundle(self.enc_keypair.as_core_keypair());
        let info = WireMessage::PeerInfo {
            enc_pubkey_hex: self.enc_keypair.public_hex(),
            display_name: self.display_name.clone(),
            supported_scopes,
            account_id: Some(self.account_id.clone()),
            transport_epoch: self.transport_epoch,
            ratchet_registration_id: Some(bundle.registration_id),
            ratchet_signed_prekey_id: Some(bundle.signed_prekey_id),
            ratchet_signed_prekey_hex: Some(hex::encode(bundle.signed_prekey)),
            ratchet_signed_prekey_sig_hex: Some(hex::encode(bundle.signed_prekey_sig)),
            ratchet_one_time_prekey_id: bundle.one_time_prekey_id,
            ratchet_one_time_prekey_hex: bundle.one_time_prekey.map(hex::encode),
        };
        self.transport.send_direct(peer_id, info).await
    }

    fn encrypt_e2e_for_peer_sync(
        &mut self,
        to_peer: &str,
        plaintext: &[u8],
        legacy_pk: Option<[u8; 32]>,
    ) -> anyhow::Result<(Vec<u8>, u8)> {
        use mycelium_core::crypto::ratchet::{
            self, RatchetSession, E2E_VERSION_LEGACY, E2E_VERSION_RATCHET,
        };

        if let Some(mut session) = self.ratchet_store.load_session(to_peer)? {
            let wire = ratchet::ratchet_encrypt(&mut session, plaintext)?;
            self.ratchet_store.save_session(&session)?;
            return Ok((wire, E2E_VERSION_RATCHET));
        }
        if let Some(bundle) = self.ratchet_store.load_peer_bundle(to_peer)? {
            if ratchet::verify_prekey_bundle(&bundle) {
                let (session, wire) = RatchetSession::init_outbound(
                    to_peer.to_string(),
                    self.enc_keypair.as_core_keypair(),
                    &bundle,
                    plaintext,
                )?;
                self.ratchet_store.save_session(&session)?;
                return Ok((wire, E2E_VERSION_RATCHET));
            }
        }
        let pk_bytes = legacy_pk.ok_or_else(|| anyhow::anyhow!("no enc key for peer {to_peer}"))?;
        let wire = crypto::encrypt_for(plaintext, &crypto::public_key_from_bytes(pk_bytes))?;
        Ok((wire, E2E_VERSION_LEGACY))
    }

    fn decrypt_e2e_sync(
        &mut self,
        logical: &str,
        from_peer: &str,
        payload: &[u8],
        e2e_version: u8,
    ) -> anyhow::Result<Vec<u8>> {
        use mycelium_core::crypto::ratchet::{self, RatchetSession, E2E_VERSION_RATCHET};

        let use_ratchet = e2e_version == E2E_VERSION_RATCHET || ratchet::is_ratchet_wire(payload);
        if !use_ratchet {
            return crypto::decrypt_with(payload, self.enc_keypair.as_core_keypair());
        }
        if ratchet::is_ratchet_init_frame(payload) {
            let mut prekeys = self.local_prekeys.lock().expect("local prekeys lock"); // INVARIANT: mutex not poisoned
            let (session, plain) = RatchetSession::init_inbound(
                logical.to_string(),
                self.enc_keypair.as_core_keypair(),
                &mut prekeys,
                payload,
            )?;
            self.ratchet_store.save_local_prekeys(&prekeys)?;
            drop(prekeys);
            let (author, _) = mycelium_core::e2e_direct_wrap::unwrap_inner(&plain, from_peer);
            let session = if author != logical {
                session.with_peer_id(author)
            } else {
                session
            };
            self.ratchet_store.save_session(&session)?;
            return Ok(plain);
        }
        let mut session = self
            .ratchet_store
            .load_session(logical)?
            .ok_or_else(|| anyhow::anyhow!("no ratchet session for {logical}"))?;
        let plain = ratchet::ratchet_decrypt(&mut session, payload)?;
        self.ratchet_store.save_session(&session)?;
        Ok(plain)
    }

    async fn forward_wire_to_others(
        &mut self,
        from_peer: &str,
        message: &WireMessage,
    ) -> anyhow::Result<()> {
        let mut peers = self.transport.known_peers();
        peers.retain(|p| p != from_peer);
        for p in peers {
            let dest = p.clone();
            if let Err(e) = self.transport.send_direct(p, message.clone()).await {
                warn!("wire forward failed to {dest}: {e}");
            }
        }
        Ok(())
    }

    async fn enqueue(&mut self, to_peer: String, message: DirectMessage) -> anyhow::Result<()> {
        if message.is_expired(now_ms()) {
            return Ok(());
        }
        if self.pending.len() >= self.pending_cap {
            self.evict_one();
        }
        if self.pending.len() >= self.pending_cap {
            let mut metrics = self.metrics.write().await;
            metrics.messages_dropped_queue += 1;
            return Err(anyhow::anyhow!("pending queue is full"));
        }
        self.pending.push_back(PendingMessage { to_peer, message });
        let mut metrics = self.metrics.write().await;
        metrics.pending_queue_size = self.pending.len();
        metrics.seen_cache_size = self.seen_cache.len();
        Ok(())
    }

    fn evict_expired(&mut self) {
        let now = now_ms();
        self.pending.retain(|item| !item.message.is_expired(now));
    }

    fn evict_one(&mut self) {
        self.evict_expired();
        if self.pending.len() < self.pending_cap {
            return;
        }

        let mut candidate_idx = None;
        let mut candidate_rank = 3u8;
        for (idx, item) in self.pending.iter().enumerate() {
            let rank = priority_rank(item.message.envelope.priority);
            if rank < candidate_rank {
                candidate_rank = rank;
                candidate_idx = Some(idx);
                if rank == 0 {
                    break;
                }
            }
        }
        if let Some(idx) = candidate_idx {
            let _ = self.pending.remove(idx);
        } else {
            let _ = self.pending.pop_front();
        }
    }
}

impl NodeRunner {
    fn config_forwarding_interval_ms(&self) -> u64 {
        self.forwarding_interval_ms
    }

    fn config_sync_interval_secs(&self) -> u64 {
        self.sync_interval_secs
    }

    fn targets_for_scope(&self, scope: &str) -> Vec<String> {
        let scope_obj = Scope(scope.to_string());
        self.peer_scopes
            .iter()
            .filter_map(|(peer, scopes)| {
                if scopes.iter().any(|pattern| scope_obj.matches(pattern)) {
                    Some(peer.clone())
                } else {
                    None
                }
            })
            .collect()
    }

    fn is_throttled(&self, peer: &str) -> bool {
        let now = now_ms();
        let mut reps = self.peer_reputations.lock().expect("reputation lock"); // INVARIANT: mutex not poisoned
        if let Some(rep) = reps.get_mut(peer) {
            if now.saturating_sub(rep.last_strike_ms) > 10 * 60 * 1000 {
                rep.strikes = 0;
            }
            return now < rep.throttled_until_ms;
        }
        false
    }

    fn record_ingest_reject(&self, reason: IngestReject) {
        let metrics = self.metrics.clone();
        tokio::spawn(async move {
            let mut m = metrics.write().await;
            match reason {
                IngestReject::RateLimit => m.messages_dropped_rate_limit += 1,
                IngestReject::Replay | IngestReject::Stale => m.messages_dropped_replay += 1,
                IngestReject::Oversize => m.messages_dropped_oversize += 1,
            }
        });
    }

    fn register_strike(&self, peer: &str) {
        let now = now_ms();
        let mut reps = self.peer_reputations.lock().expect("reputation lock"); // INVARIANT: mutex not poisoned
        let rep = reps.entry(peer.to_string()).or_default();
        if now.saturating_sub(rep.last_strike_ms) > 60 * 1000 {
            rep.strikes = 0;
        }
        rep.strikes = rep.strikes.saturating_add(1);
        rep.last_strike_ms = now;
        if rep.strikes >= 3 {
            rep.throttled_until_ms = now + 5 * 60 * 1000;
        }
    }

    fn reward_peer(&self, peer: &str) {
        let mut reps = self.peer_reputations.lock().expect("reputation lock"); // INVARIANT: mutex not poisoned
        if let Some(rep) = reps.get_mut(peer) {
            rep.strikes /= 2;
        }
    }
}

struct BasicSeenCache {
    cap: usize,
    order: VecDeque<String>,
    set: HashSet<String>,
}

impl BasicSeenCache {
    fn new(cap: usize) -> Self {
        Self {
            cap,
            order: VecDeque::new(),
            set: HashSet::new(),
        }
    }

    fn len(&self) -> usize {
        self.set.len()
    }
}

impl SeenCache for BasicSeenCache {
    fn contains(&self, message_id: &str) -> bool {
        self.set.contains(message_id)
    }

    fn insert(&mut self, message_id: String) {
        if self.set.insert(message_id.clone()) {
            self.order.push_back(message_id);
        }
        while self.order.len() > self.cap {
            if let Some(evicted) = self.order.pop_front() {
                self.set.remove(&evicted);
            }
        }
    }
}

#[derive(Clone)]
struct PendingMessage {
    to_peer: String,
    message: DirectMessage,
}

fn priority_rank(priority: Priority) -> u8 {
    match priority {
        Priority::Low => 0,
        Priority::Normal => 1,
        Priority::High => 2,
    }
}
