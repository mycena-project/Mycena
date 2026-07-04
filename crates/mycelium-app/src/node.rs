// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
use crate::contacts::{Contact, ContactStatus};
use crate::envelope::{
    AppId, AppMessage, AppPayload, Attachment, BulletinPost, ChatMessage, MailMessage,
};
use crate::groups::Group;
use crate::miniapp::store::AppStoreListing;
use crate::notify::NotificationSink;
use crate::proximity::{
    PresenceProfile, PresenceSignal, ProximityDirectMessage, ProximityInbox, ProximityMatchIntent,
    ProximityMatchState, ProximityMatcher, ProximityNearbyEntry, ProximityReceivedMessage,
    ProximityStore, PROXIMITY_SCOPE,
};
use crate::scope_key::ScopeKey;
use crate::storage::AppStorage;
use mycelium_coin::CoinNode;
use mycelium_l0_facade::consistency::{
    OP_DIRECT_MESSAGE_SEND, OP_GOSSIP_BULLETIN_PUBLISH, OP_SOS_BROADCAST,
};
use mycelium_l0_facade::data::now_ms;
use mycelium_l0_facade::{
    decrypt_group, encrypt_for, encrypt_group, parse_x25519_public_hex, EncKey,
};
use mycelium_l1_facade::{DirectMessage, NodeCommand, NodeHandle, WireMessage};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use tokio::sync::{broadcast, Mutex, RwLock};
use tracing::{info, warn};

pub struct AppNode {
    handle: NodeHandle,
    local_peer_id: String,
    display_name: Arc<RwLock<String>>,
    chat_tx: broadcast::Sender<ChatIncoming>,
    bulletin_tx: broadcast::Sender<BulletinPost>,
    mail_tx: broadcast::Sender<MailMessage>,
    appstore_tx: broadcast::Sender<AppStoreListing>,
    storage: Arc<AppStorage>,
    notifier: Arc<dyn NotificationSink>,
    coin: Option<Arc<CoinNode>>,
    app_store: Option<Arc<crate::miniapp::AppStore>>,
    enc_keypair: EncKey,
    proximity_store: Arc<Mutex<ProximityStore>>,
    proximity_matches: Arc<Mutex<ProximityMatchState>>,
    proximity_inbox: Arc<Mutex<ProximityInbox>>,
    proximity_active: Arc<AtomicBool>,
    my_presence: Arc<RwLock<Option<PresenceSignal>>>,
    my_proximity_profile: Arc<RwLock<PresenceProfile>>,
    listen_sos: Arc<AtomicBool>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EncryptionResult {
    Encrypted,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ChatSendResult {
    SentEncrypted,
    SentPlaintext,
}

fn enc_key_not_yet_exchanged_err() -> anyhow::Error {
    anyhow::anyhow!(
        "enc_key_not_yet_exchanged: message not sent. \
         Connect to the peer first and wait for key exchange."
    )
}

impl AppNode {
    #[allow(clippy::too_many_arguments)]
    pub fn new(
        handle: NodeHandle,
        local_peer_id: String,
        display_name: String,
        storage: Arc<AppStorage>,
        notifier: Arc<dyn NotificationSink>,
        coin: Option<Arc<CoinNode>>,
        app_store: Option<Arc<crate::miniapp::AppStore>>,
        enc_keypair: EncKey,
    ) -> (Self, AppInbox) {
        let (chat_tx, chat_rx) = broadcast::channel(256);
        let (bulletin_tx, bulletin_rx) = broadcast::channel(256);
        let (mail_tx, mail_rx) = broadcast::channel(256);
        let (appstore_tx, appstore_rx) = broadcast::channel(64);
        let node = Self {
            handle,
            local_peer_id,
            display_name: Arc::new(RwLock::new(display_name)),
            chat_tx,
            bulletin_tx,
            mail_tx,
            appstore_tx,
            storage,
            notifier,
            coin,
            app_store,
            enc_keypair,
            proximity_store: Arc::new(Mutex::new(ProximityStore::new())),
            proximity_matches: Arc::new(Mutex::new(ProximityMatchState::default())),
            proximity_inbox: Arc::new(Mutex::new(ProximityInbox::new())),
            proximity_active: Arc::new(AtomicBool::new(false)),
            my_presence: Arc::new(RwLock::new(None)),
            my_proximity_profile: Arc::new(RwLock::new(PresenceProfile::default())),
            listen_sos: Arc::new(AtomicBool::new(false)),
        };
        let inbox = AppInbox {
            chat_rx,
            bulletin_rx,
            mail_rx,
            appstore_rx,
        };
        (node, inbox)
    }

    pub fn start_incoming_task(self: Arc<Self>) {
        let mut rx = self.handle.subscribe_incoming();
        tokio::spawn(async move {
            loop {
                match rx.recv().await {
                    Ok(msg) => self.dispatch_incoming(&msg).await,
                    Err(err) => {
                        warn!("incoming subscription closed: {err}");
                        break;
                    }
                }
            }
        });
    }

    /// Re-dial relay circuits for saved accepted contacts after node restart.
    pub fn restore_accepted_contact_dials(self: Arc<Self>) {
        tokio::spawn(async move {
            // Let transport/bootstrap init settle before dialing.
            tokio::time::sleep(std::time::Duration::from_millis(500)).await;
            let contacts = match self.storage.contacts_with_status(ContactStatus::Accepted) {
                Ok(rows) => rows,
                Err(err) => {
                    warn!("restore contact dials: {err}");
                    return;
                }
            };
            for contact in contacts {
                let peer = contact.peer_id;
                let Some(addr) = mycelium_l0_facade::bootstrap::relay_circuit_multiaddr(&peer)
                else {
                    continue;
                };
                if let Err(err) = self
                    .handle
                    .send(NodeCommand::AddBootstrapPeer { multiaddr: addr })
                    .await
                {
                    warn!("restore dial {peer}: {err}");
                } else {
                    info!("restored relay dial toward {peer}");
                }
            }
        });
    }

    /// Remember relay circuits for reconnect when a remote peer comes online.
    /// Contacts are added only via QR invite / explicit accept — not on mere connectivity.
    pub fn start_peer_contact_sync(self: Arc<Self>) {
        tokio::spawn(async move {
            let mut bootstrapped = std::collections::HashSet::<String>::new();
            let mut tick = tokio::time::interval(std::time::Duration::from_secs(2));
            tick.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);
            loop {
                tick.tick().await;
                let peers = self.handle.known_peers().await;
                for peer in peers {
                    if !mycelium_l0_facade::bootstrap::is_mesh_peer_id(&peer)
                        || mycelium_l0_facade::bootstrap::is_relay_peer(&peer)
                    {
                        continue;
                    }
                    // Relay bootstrap only — contacts come from QR scan + signed ContactHello.
                    if !bootstrapped.contains(&peer) {
                        if let Some(addr) =
                            mycelium_l0_facade::bootstrap::relay_circuit_multiaddr(&peer)
                        {
                            if let Err(e) = self
                                .handle
                                .send(NodeCommand::AddBootstrapPeer { multiaddr: addr })
                                .await
                            {
                                warn!("auto-dial {peer}: {e}");
                            }
                        }
                        bootstrapped.insert(peer);
                    }
                }
            }
        });
    }

    fn ensure_inbound_chat_contact(&self, peer: &str, display_name: &str) -> bool {
        if self.storage.is_peer_blocked(peer).unwrap_or(false) {
            return false;
        }
        match self.storage.contact_by_id(peer) {
            Ok(Some(c)) if c.status == ContactStatus::Accepted => {
                self.maybe_update_stub_display_name(peer, display_name);
                true
            }
            Ok(Some(c)) if c.status == ContactStatus::Blocked => false,
            Ok(Some(_)) | Ok(None) => {
                let name = if display_name.is_empty() {
                    peer.chars().take(12).collect::<String>()
                } else {
                    display_name.to_string()
                };
                match self.add_contact(peer, &name, false) {
                    Ok(c) => {
                        self.notifier.on_contact_added(peer, &c.display_name);
                        false
                    }
                    Err(e) => {
                        warn!("inbound chat pending contact {peer}: {e}");
                        false
                    }
                }
            }
            Err(e) => {
                warn!("contact lookup failed for {peer}: {e}");
                false
            }
        }
    }

    fn maybe_update_stub_display_name(&self, peer: &str, display_name: &str) -> bool {
        let name = display_name.trim();
        if name.is_empty() {
            return false;
        }
        let Ok(Some(c)) = self.storage.contact_by_id(peer) else {
            return false;
        };
        if c.status != ContactStatus::Accepted {
            return false;
        }
        let stub = peer.chars().take(16).collect::<String>();
        let looks_like_stub = c.display_name == stub
            || (c.display_name.starts_with("12D3") && c.display_name.len() <= 20);
        if !looks_like_stub || c.display_name == name {
            return false;
        }
        match self.storage.upsert_contact_with_account(
            peer,
            name,
            ContactStatus::Accepted,
            c.account_id.as_deref(),
        ) {
            Ok(updated) => {
                self.notifier.on_contact_added(peer, &updated.display_name);
                true
            }
            Err(e) => {
                warn!("update stub display name for {peer}: {e}");
                false
            }
        }
    }

    async fn apply_identity_rotation(
        &self,
        rot: mycelium_l0_facade::identity_rotation::IdentityRotationGossip,
        transport_from: &str,
    ) {
        if !rot.verify() {
            warn!("rejected identity rotation from {transport_from}");
            return;
        }
        let old_peer_id = if !rot.previous_peer_id.is_empty() {
            rot.previous_peer_id.clone()
        } else if let Ok(Some(c)) = self.storage.contact_by_account_id(&rot.account_id) {
            c.peer_id
        } else {
            transport_from.to_string()
        };
        if let Err(e) = self
            .handle
            .remap_transport_peer(&old_peer_id, &rot.new_peer_id)
            .await
        {
            warn!("node remap {old_peer_id} -> {}: {e}", rot.new_peer_id);
        }
        match self
            .storage
            .remap_contact_peer(&rot.account_id, &rot.new_peer_id)
        {
            Ok(Some(c)) => {
                info!(
                    "contact account {} transport {} -> {}",
                    rot.account_id, old_peer_id, rot.new_peer_id
                );
                self.notifier.on_contact_added(&c.peer_id, &c.display_name);
            }
            Ok(None) => {}
            Err(e) => warn!("identity rotation remap failed: {e}"),
        }
        if let Err(e) = self
            .storage
            .group_remap_member_peer(&old_peer_id, &rot.new_peer_id)
        {
            warn!("group member remap failed: {e}");
        }
    }

    pub async fn dispatch_incoming(&self, msg: &DirectMessage) {
        if msg.body == "[mycelium:group]" {
            if let Ok(WireMessage::EncryptedGroup {
                group_id,
                encrypted_payload,
            }) = bincode::deserialize(&msg.envelope.payload)
            {
                let from = msg.envelope.from_peer.clone();
                match self.storage.group_by_id(&group_id) {
                    Ok(Some(group)) => match decrypt_group(&encrypted_payload, &group.key) {
                        Ok(plain) => {
                            if let Ok(app_msg) = AppMessage::decode(&plain) {
                                if let AppPayload::Chat(m) = app_msg.payload {
                                    if let Err(err) = self.storage.save_group_chat(&group_id, &m) {
                                        warn!("failed to save group chat: {err}");
                                    }
                                    if let Err(err) =
                                        self.storage.group_record_peer_seen(&group_id, &from)
                                    {
                                        warn!("group member record failed: {err}");
                                    }
                                    self.notifier.on_chat_received(
                                        &from,
                                        &m.body.chars().take(40).collect::<String>(),
                                    );
                                    let _ = self.chat_tx.send(ChatIncoming {
                                        from_peer: from.clone(),
                                        message: m,
                                    });
                                }
                            }
                        }
                        Err(e) => warn!("group decrypt failed: {e}"),
                    },
                    Ok(None) => {}
                    Err(e) => warn!("group lookup failed: {e}"),
                }
            }
            return;
        }
        if msg.body == "[proximity]" {
            self.handle_proximity_payload(&msg.envelope.payload).await;
            return;
        }
        if msg.body == "[encrypted]" {
            if let Some(rot) =
                mycelium_l0_facade::identity_rotation::IdentityRotationGossip::decode_e2e_announce(
                    &msg.envelope.payload,
                )
            {
                self.apply_identity_rotation(rot, &msg.envelope.from_peer)
                    .await;
                return;
            }
        }
        if msg.body.starts_with("[identity:") {
            warn!(
                "legacy plaintext identity rotation gossip from {} — ignored (use E2E announce)",
                msg.envelope.from_peer
            );
            return;
        }
        if msg.body == "[contact:hello]" {
            self.handle_contact_hello(&msg.envelope.from_peer, &msg.envelope.payload)
                .await;
            return;
        }
        if let Some(scope) = msg
            .body
            .strip_prefix("[bulletin:")
            .and_then(|s| s.strip_suffix(']'))
        {
            match self
                .handle_bulletin_payload(&msg.envelope.payload, scope)
                .await
            {
                Ok(Some(post)) => {
                    if let Err(err) = self.storage.save_bulletin(&post) {
                        warn!("failed to save bulletin: {err}");
                    }
                    if post.scope == crate::sos::SOS_SCOPE {
                        if self.listen_sos.load(Ordering::Relaxed) {
                            if let Ok(payload) = crate::sos::SosPayload::decode_json(&post.body) {
                                self.notifier.on_sos_received(&payload);
                            }
                        }
                    } else {
                        self.notifier.on_bulletin_posted(&post.scope, &post.title);
                    }
                    let _ = self.bulletin_tx.send(post);
                }
                Ok(None) => {}
                Err(e) => warn!("bulletin payload handling failed: {e}"),
            }
            return;
        }
        match AppMessage::decode(&msg.envelope.payload) {
            Ok(app_msg) => match app_msg.payload {
                AppPayload::Chat(m) => {
                    let peer = msg.envelope.from_peer.clone();
                    if peer == self.local_peer_id {
                        return;
                    }
                    let accepted = self.ensure_inbound_chat_contact(&peer, &m.from_display_name);
                    if let Err(err) = self.storage.save_chat(&peer, &m) {
                        warn!("failed to save chat: {err}");
                    }
                    if accepted {
                        self.notifier
                            .on_chat_received(&peer, &m.body.chars().take(40).collect::<String>());
                        let _ = self.chat_tx.send(ChatIncoming {
                            from_peer: peer.clone(),
                            message: m,
                        });
                    }
                }
                AppPayload::Bulletin(m) => {
                    if let Err(err) = self.storage.save_bulletin(&m) {
                        warn!("failed to save bulletin: {err}");
                    }
                    self.notifier.on_bulletin_posted(&m.scope, &m.title);
                    let _ = self.bulletin_tx.send(m);
                }
                AppPayload::Mail(m) => {
                    let addressed_to_us = m.to_peer == self.local_peer_id
                        || msg
                            .envelope
                            .to_peer
                            .as_deref()
                            .is_some_and(|p| p == self.local_peer_id);
                    if !addressed_to_us {
                        return;
                    }
                    if let Err(err) = self.storage.save_mail_inbox(&m) {
                        warn!("failed to save inbox mail: {err}");
                    }
                    self.notifier
                        .on_mail_received(&m.from_display_name, &m.subject);
                    let _ = self.mail_tx.send(m);
                }
                AppPayload::Coin(inner) => {
                    if let Some(coin) = &self.coin {
                        let from_peer = msg.envelope.from_peer.clone();
                        match bincode::deserialize::<mycelium_coin::CoinPayload>(&inner) {
                            Ok(payload) => {
                                if let Err(err) = coin.handle_incoming(payload, &from_peer).await {
                                    warn!("coin dispatch failed: {err}");
                                }
                            }
                            Err(err) => warn!("failed to decode CoinPayload: {err}"),
                        }
                    }
                }
                AppPayload::MiniAppRevocation(entry) => {
                    let entry = entry.as_ref();
                    if let Some(ref store) = self.app_store {
                        match store.ingest_revocation_gossip(entry) {
                            Ok(true) => tracing::info!("ingested revocation for {}", entry.app_id),
                            Ok(false) => {
                                warn!("rejected revocation gossip for {} (bad sig)", entry.app_id)
                            }
                            Err(e) => warn!("failed to ingest revocation {}: {e}", entry.app_id),
                        }
                    }
                }
                AppPayload::AppStoreListing(listing) => {
                    let listing = listing.as_ref();
                    match listing.verify_signature() {
                        Ok(true) => {
                            if let Some(ref store) = self.app_store {
                                match store.cache_listing(listing) {
                                    Err(e) => warn!(
                                        "failed to cache app listing {}: {e}",
                                        listing.manifest.id
                                    ),
                                    Ok(()) => {
                                        tracing::info!(
                                            "cached app listing: {} v{}",
                                            listing.manifest.id,
                                            listing.manifest.version
                                        );
                                        let _ = self.appstore_tx.send(listing.clone());
                                    }
                                }
                            }
                        }
                        Ok(false) => {
                            warn!(
                                "rejected listing with invalid signature: {}",
                                listing.manifest.id
                            );
                        }
                        Err(e) => warn!("signature verification error: {e}"),
                    }
                }
            },
            Err(err) => {
                if msg.body == "[encrypted]" {
                    warn!("failed to decode E2E direct chat payload: {err}");
                } else {
                    warn!("failed to decode AppMessage: {err}");
                }
            }
        }
    }

    pub async fn send_chat(
        &self,
        to_peer: Option<String>,
        body: String,
    ) -> anyhow::Result<ChatSendResult> {
        match to_peer {
            None => {
                let display_name = self.display_name.read().await.clone();
                let chat = ChatMessage {
                    id: uuid::Uuid::new_v4(),
                    from_display_name: display_name,
                    body: body.clone(),
                    timestamp_ms: now_ms(),
                    reply_to: None,
                };
                let payload = AppMessage {
                    app_id: AppId::Chat,
                    payload: AppPayload::Chat(chat),
                }
                .encode()?;
                self.handle
                    .mesh_broadcast(
                        "mycelium/chat".to_string(),
                        body,
                        payload,
                        OP_GOSSIP_BULLETIN_PUBLISH,
                    )
                    .await?;
                Ok(ChatSendResult::SentPlaintext)
            }
            Some(to) => {
                self.ensure_contact_accepted_for_send(&to, "")?;
                if self.handle.has_enc_key_for(&to).await {
                    self.send_chat_encrypted(to, body).await?;
                    Ok(ChatSendResult::SentEncrypted)
                } else {
                    warn!(
                        "SD-081: no enc key for {to} — refusing plaintext. \
                         Key will arrive via PeerInfo on next connection."
                    );
                    Err(enc_key_not_yet_exchanged_err())
                }
            }
        }
    }

    /// Broadcast a chat payload on an arbitrary mesh scope (e.g. `mycelium/chat`).
    pub async fn broadcast_chat(&self, scope: String, body: String) -> anyhow::Result<()> {
        let display_name = self.display_name.read().await.clone();
        let chat = ChatMessage {
            id: uuid::Uuid::new_v4(),
            from_display_name: display_name,
            body: body.clone(),
            timestamp_ms: now_ms(),
            reply_to: None,
        };
        let payload = AppMessage {
            app_id: AppId::Chat,
            payload: AppPayload::Chat(chat),
        }
        .encode()?;
        self.handle
            .mesh_broadcast(scope, body, payload, OP_GOSSIP_BULLETIN_PUBLISH)
            .await?;
        Ok(())
    }

    pub async fn send_chat_encrypted(
        &self,
        to_peer: String,
        body: String,
    ) -> anyhow::Result<EncryptionResult> {
        self.ensure_contact_accepted_for_send(&to_peer, "")?;
        let display_name = self.display_name.read().await.clone();
        let chat = ChatMessage {
            id: uuid::Uuid::new_v4(),
            from_display_name: display_name,
            body: body.clone(),
            timestamp_ms: now_ms(),
            reply_to: None,
        };
        let plaintext = AppMessage {
            app_id: AppId::Chat,
            payload: AppPayload::Chat(chat.clone()),
        }
        .encode()?;
        let wrapped =
            mycelium_l0_facade::e2e_direct_wrap::wrap_inner(&self.local_peer_id, &plaintext);
        let candidates = self.storage.transport_peer_candidates(&to_peer)?;
        let mut last_err = None;
        for target in candidates {
            if !self.handle.has_enc_key_for(&target).await {
                continue;
            }
            let (encrypted, e2e_version) = match self.handle.encrypt_e2e_payload(&target, wrapped.clone()).await {
                Ok(v) => v,
                Err(e) => {
                    last_err = Some(e);
                    continue;
                }
            };
            let wire = WireMessage::EncryptedDirect {
                to_peer: target.clone(),
                sender_enc_pubkey: self.handle.local_enc_pubkey_hex(),
                encrypted_payload: encrypted,
                mesh_signature: None,
                hop_count: 0,
                max_hops: 8,
                e2e_version,
            };
            match self
                .handle
                .mesh_send_wire_or_ble(target.clone(), wire, OP_DIRECT_MESSAGE_SEND)
                .await
            {
                Ok(()) => {
                    self.storage.save_chat(&to_peer, &chat)?;
                    tracing::info!("sent encrypted chat to {target} ({} bytes)", body.len());
                    return Ok(EncryptionResult::Encrypted);
                }
                Err(e) => last_err = Some(e),
            }
        }
        if let Some(e) = last_err {
            return Err(e);
        }
        Err(enc_key_not_yet_exchanged_err())
    }

    pub async fn send_group_message(&self, group: &Group, body: String) -> anyhow::Result<()> {
        let _ = self
            .storage
            .group_record_peer_seen(&group.id, &self.local_peer_id);
        let display_name = self.display_name.read().await.clone();
        let chat = ChatMessage {
            id: uuid::Uuid::new_v4(),
            from_display_name: display_name,
            body: body.clone(),
            timestamp_ms: now_ms(),
            reply_to: None,
        };
        self.storage.save_group_chat(&group.id, &chat)?;
        let plaintext = AppMessage {
            app_id: AppId::Chat,
            payload: AppPayload::Chat(chat),
        }
        .encode()?;
        let encrypted = encrypt_group(&plaintext, &group.key)?;
        let wire = WireMessage::EncryptedGroup {
            group_id: group.id.clone(),
            encrypted_payload: encrypted,
        };
        let payload = bincode::serialize(&wire)?;
        let scope = format!("mycelium/group/{}", group.id);
        let _ = self
            .handle
            .send(NodeCommand::SubscribeScope("mycelium/group/*".into()))
            .await;
        self.handle
            .mesh_broadcast(
                scope,
                "[group message]".into(),
                payload,
                OP_GOSSIP_BULLETIN_PUBLISH,
            )
            .await?;
        Ok(())
    }

    pub async fn post_bulletin(
        &self,
        scope: String,
        title: String,
        body: String,
        ttl_secs: u64,
    ) -> anyhow::Result<()> {
        let scope = normalize_bulletin_scope(&scope)?;
        let _ = self
            .handle
            .send(NodeCommand::SubscribeBulletinScope(scope.clone()))
            .await;

        let display_name = self.display_name.read().await.clone();
        let now = now_ms();
        let post = BulletinPost {
            id: uuid::Uuid::new_v4(),
            from_display_name: display_name,
            title: title.clone(),
            body: body.clone(),
            scope: scope.clone(),
            timestamp_ms: now,
            expires_at_ms: now + ttl_secs * 1000,
        };
        self.storage.save_bulletin(&post)?;

        let plaintext = AppMessage {
            app_id: AppId::Bulletin,
            payload: AppPayload::Bulletin(post),
        }
        .encode()?;

        let payload = if let Some(sk) = self.storage.get_scope_key(&scope)? {
            let encrypted = encrypt_group(&plaintext, &sk.key)?;
            let mut marker = b"enc1:".to_vec();
            marker.extend_from_slice(&encrypted);
            marker
        } else {
            plaintext
        };

        let body_tag = format!("[bulletin:{scope}]");
        if scope == crate::sos::SOS_SCOPE {
            self.handle
                .mesh_broadcast_sos(scope.clone(), body_tag, payload, OP_SOS_BROADCAST)
                .await?;
        } else {
            self.handle
                .mesh_broadcast(scope.clone(), body_tag, payload, OP_GOSSIP_BULLETIN_PUBLISH)
                .await?;
        }
        Ok(())
    }

    async fn handle_bulletin_payload(
        &self,
        raw_payload: &[u8],
        scope: &str,
    ) -> anyhow::Result<Option<BulletinPost>> {
        if raw_payload.starts_with(b"enc1:") {
            let ciphertext = &raw_payload[5..];
            if let Some(sk) = self.storage.get_scope_key(scope)? {
                match decrypt_group(ciphertext, &sk.key) {
                    Ok(plaintext) => {
                        let msg = AppMessage::decode(&plaintext)?;
                        if let AppPayload::Bulletin(post) = msg.payload {
                            return Ok(Some(post));
                        }
                    }
                    Err(_) => {
                        tracing::debug!(
                            "encrypted bulletin in scope {scope} could not be decrypted (wrong key?)"
                        );
                        return Ok(None);
                    }
                }
            } else {
                tracing::debug!("encrypted bulletin in scope {scope} — no key, not displaying");
                return Ok(None);
            }
        }

        let msg = AppMessage::decode(raw_payload)?;
        if let AppPayload::Bulletin(post) = msg.payload {
            return Ok(Some(post));
        }
        Ok(None)
    }

    pub fn list_scope_keys(&self) -> anyhow::Result<Vec<ScopeKey>> {
        self.storage.all_scope_keys()
    }

    pub async fn create_scope_key(
        &self,
        scope: String,
        display_name: String,
    ) -> anyhow::Result<ScopeKey> {
        let sk = ScopeKey::new(scope.clone(), display_name);
        self.storage.save_scope_key(&sk)?;
        let _ = self
            .handle
            .send(NodeCommand::SubscribeBulletinScope(scope))
            .await;
        Ok(sk)
    }

    pub async fn add_scope_key_from_invite(&self, invite_json: &str) -> anyhow::Result<ScopeKey> {
        let sk = ScopeKey::from_invite(invite_json)?;
        self.storage.save_scope_key(&sk)?;
        let _ = self
            .handle
            .send(NodeCommand::SubscribeBulletinScope(sk.scope.clone()))
            .await;
        Ok(sk)
    }

    pub fn export_scope_key(&self, scope: &str) -> anyhow::Result<String> {
        let sk = self
            .storage
            .get_scope_key(scope)?
            .ok_or_else(|| anyhow::anyhow!("scope key not found"))?;
        Ok(sk.export_invite())
    }

    pub async fn delete_scope_key(&self, scope: &str) -> anyhow::Result<()> {
        self.storage.delete_scope_key(scope)?;
        let _ = self
            .handle
            .send(NodeCommand::UnsubscribeBulletinScope(scope.to_string()))
            .await;
        Ok(())
    }

    pub fn scope_is_encrypted(&self, scope: &str) -> anyhow::Result<bool> {
        Ok(self.storage.get_scope_key(scope)?.is_some())
    }

    pub async fn send_mail(
        &self,
        to_peer: String,
        subject: String,
        body: String,
        attachments: Vec<Attachment>,
    ) -> anyhow::Result<()> {
        let to_peer = self.resolve_mail_recipient(&to_peer)?;
        let display_name = self.display_name.read().await.clone();
        let mail = MailMessage {
            id: uuid::Uuid::new_v4(),
            from_peer: self.local_peer_id.clone(),
            from_display_name: display_name,
            to_peer: to_peer.clone(),
            subject: subject.clone(),
            body: body.clone(),
            timestamp_ms: now_ms(),
            attachments,
        };
        self.storage.save_mail_sent(&mail)?;
        let payload = AppMessage {
            app_id: AppId::Mail,
            payload: AppPayload::Mail(mail),
        }
        .encode()?;
        self.handle
            .mesh_send_direct(
                to_peer,
                format!("mail:{subject}"),
                payload,
                OP_DIRECT_MESSAGE_SEND,
            )
            .await?;
        Ok(())
    }

    pub async fn set_display_name(&self, name: String) {
        *self.display_name.write().await = name;
    }

    pub fn local_peer_id(&self) -> &str {
        &self.local_peer_id
    }

    pub fn node_handle(&self) -> NodeHandle {
        self.handle.clone()
    }

    pub async fn display_name(&self) -> String {
        self.display_name.read().await.clone()
    }

    pub fn storage(&self) -> Arc<AppStorage> {
        self.storage.clone()
    }

    pub fn app_storage(&self) -> Arc<AppStorage> {
        self.storage.clone()
    }

    pub fn is_proximity_active(&self) -> bool {
        self.proximity_active.load(Ordering::Relaxed)
    }

    pub async fn start_proximity(
        &self,
        profile: PresenceProfile,
        ttl_secs: u32,
    ) -> anyhow::Result<()> {
        profile.validate()?;
        let enc_pubkey_hex = self.handle.local_enc_pubkey_hex();
        let signal = PresenceSignal::new(enc_pubkey_hex, profile.clone(), ttl_secs);
        let payload = signal.encode()?;

        *self.my_presence.write().await = Some(signal);
        *self.my_proximity_profile.write().await = profile;
        self.proximity_active.store(true, Ordering::Relaxed);

        self.handle
            .send(NodeCommand::SubscribeScope(PROXIMITY_SCOPE.into()))
            .await?;
        self.broadcast_presence(payload).await?;

        let handle = self.handle.clone();
        let presence = self.my_presence.clone();
        let profile_ref = self.my_proximity_profile.clone();
        let active = self.proximity_active.clone();
        let enc = self.handle.local_enc_pubkey_hex();

        tokio::spawn(async move {
            while active.load(Ordering::Relaxed) {
                tokio::time::sleep(std::time::Duration::from_secs(60)).await;
                if !active.load(Ordering::Relaxed) {
                    break;
                }
                let profile = profile_ref.read().await.clone();
                let ttl = presence
                    .read()
                    .await
                    .as_ref()
                    .map(|p| p.ttl_secs)
                    .unwrap_or(300);
                let refreshed = PresenceSignal::new(enc.clone(), profile, ttl);
                if let Ok(payload) = refreshed.encode() {
                    *presence.write().await = Some(refreshed);
                    let _ = handle
                        .mesh_broadcast(
                            PROXIMITY_SCOPE.into(),
                            "[proximity]".into(),
                            payload,
                            OP_GOSSIP_BULLETIN_PUBLISH,
                        )
                        .await;
                }
            }
        });

        Ok(())
    }

    pub async fn stop_proximity(&self) {
        self.proximity_active.store(false, Ordering::Relaxed);
        *self.my_presence.write().await = None;
        self.proximity_store.lock().await.clear();
        self.proximity_matches.lock().await.clear();
        self.proximity_inbox.lock().await.clear();
        let _ = self
            .handle
            .send(NodeCommand::UnsubscribeScope(PROXIMITY_SCOPE.into()))
            .await;
    }

    pub async fn nearby_profiles(&self) -> Vec<ProximityNearbyEntry> {
        let mut store = self.proximity_store.lock().await;
        store.prune();
        let profile = self.my_proximity_profile.read().await.clone();
        let matches = self.proximity_matches.lock().await;
        let matcher = ProximityMatcher {
            my_looking_for: profile.looking_for.clone(),
            my_interests: profile.interests.clone(),
        };
        let mut scored: Vec<(u32, ProximityNearbyEntry)> = store
            .active_signals()
            .iter()
            .map(|s| {
                let enc = s.enc_pubkey_hex.clone();
                let interest_sent = matches.interest_sent(&enc);
                let interest_received = matches.interest_received(&enc);
                let is_mutual = matches.is_mutual(&enc);
                (
                    matcher.score(s).score,
                    ProximityNearbyEntry {
                        signal: (*s).clone(),
                        interest_sent,
                        interest_received,
                        is_mutual,
                    },
                )
            })
            .collect();
        scored.sort_by_key(|b| std::cmp::Reverse(b.0));
        scored.into_iter().map(|(_, entry)| entry).collect()
    }

    pub async fn express_proximity_interest(&self, enc_pubkey_hex: String) -> anyhow::Result<bool> {
        {
            let store = self.proximity_store.lock().await;
            let visible = store
                .active_signals()
                .iter()
                .any(|s| s.enc_pubkey_hex == enc_pubkey_hex);
            if !visible {
                anyhow::bail!("profile not currently nearby");
            }
        }
        self.proximity_matches
            .lock()
            .await
            .record_outgoing(&enc_pubkey_hex);
        let intent = ProximityMatchIntent::new(self.handle.local_enc_pubkey_hex());
        let payload = intent.encode()?;
        self.handle
            .mesh_broadcast(
                PROXIMITY_SCOPE.into(),
                "[proximity]".into(),
                payload,
                OP_GOSSIP_BULLETIN_PUBLISH,
            )
            .await?;
        Ok(self
            .proximity_matches
            .lock()
            .await
            .is_mutual(&enc_pubkey_hex))
    }

    pub async fn proximity_messages(&self, since_ms: u64) -> Vec<ProximityReceivedMessage> {
        if since_ms == 0 {
            self.proximity_inbox.lock().await.all()
        } else {
            self.proximity_inbox.lock().await.since(since_ms)
        }
    }

    pub async fn send_proximity_message(
        &self,
        enc_pubkey_hex: String,
        message: String,
    ) -> anyhow::Result<()> {
        if !self
            .proximity_matches
            .lock()
            .await
            .is_mutual(&enc_pubkey_hex)
        {
            anyhow::bail!("mutual match required before messaging");
        }
        let recipient = parse_x25519_public_hex(&enc_pubkey_hex)?;
        let encrypted = encrypt_for(message.as_bytes(), &recipient)?;
        let direct = ProximityDirectMessage {
            target_enc_pubkey_hex: enc_pubkey_hex,
            sender_enc_pubkey_hex: self.handle.local_enc_pubkey_hex(),
            encrypted_payload: encrypted,
            created_at_ms: now_ms(),
        };
        let payload = direct.encode()?;
        self.handle
            .mesh_broadcast(
                PROXIMITY_SCOPE.into(),
                "[proximity]".into(),
                payload,
                OP_GOSSIP_BULLETIN_PUBLISH,
            )
            .await?;
        Ok(())
    }

    async fn broadcast_presence(&self, payload: Vec<u8>) -> anyhow::Result<()> {
        self.handle
            .mesh_broadcast(
                PROXIMITY_SCOPE.into(),
                "[proximity]".into(),
                payload,
                OP_GOSSIP_BULLETIN_PUBLISH,
            )
            .await?;
        Ok(())
    }

    async fn handle_proximity_payload(&self, payload: &[u8]) {
        if !self.proximity_active.load(Ordering::Relaxed) {
            return;
        }
        if let Ok(msg) = ProximityDirectMessage::decode(payload) {
            if msg.target_enc_pubkey_hex != self.handle.local_enc_pubkey_hex() {
                return;
            }
            if !self
                .proximity_matches
                .lock()
                .await
                .is_mutual(&msg.sender_enc_pubkey_hex)
            {
                warn!("proximity message dropped: no mutual match");
                return;
            }
            match self.enc_keypair.decrypt_direct(&msg.encrypted_payload) {
                Ok(plain) => {
                    let text = String::from_utf8_lossy(&plain).into_owned();
                    self.proximity_inbox
                        .lock()
                        .await
                        .push(ProximityReceivedMessage {
                            from_enc_pubkey_hex: msg.sender_enc_pubkey_hex.clone(),
                            body: text.clone(),
                            received_at_ms: now_ms(),
                        });
                    self.notifier.on_chat_received("proximity", &text);
                }
                Err(e) => warn!("proximity message decrypt failed: {e}"),
            }
            return;
        }
        if let Ok(signal) = PresenceSignal::decode(payload) {
            if signal.is_expired() {
                return;
            }
            if signal.enc_pubkey_hex == self.handle.local_enc_pubkey_hex() {
                return;
            }
            self.proximity_store.lock().await.insert(signal);
            return;
        }
        if let Ok(intent) = ProximityMatchIntent::decode(payload) {
            if intent.is_expired() {
                return;
            }
            if intent.from_enc_pubkey_hex == self.handle.local_enc_pubkey_hex() {
                return;
            }
            self.proximity_matches
                .lock()
                .await
                .record_incoming(&intent.from_enc_pubkey_hex);
        }
    }

    pub async fn chat_history(
        &self,
        peer_id: &str,
        limit: usize,
    ) -> anyhow::Result<Vec<ChatMessage>> {
        self.storage.chat_history(peer_id, limit)
    }

    pub fn ensure_contact_accepted_for_send(
        &self,
        peer_id: &str,
        display_name: &str,
    ) -> anyhow::Result<()> {
        let name = if display_name.is_empty() {
            self.storage
                .contact_by_id(peer_id)?
                .map(|c| c.display_name)
                .unwrap_or_else(|| peer_id.chars().take(12).collect())
        } else {
            display_name.to_string()
        };
        self.storage
            .upsert_contact(peer_id, &name, ContactStatus::Accepted)?;
        Ok(())
    }

    async fn wait_peer_connected(
        &self,
        peer_id: &str,
        attempts: u32,
        delay: std::time::Duration,
    ) -> bool {
        for i in 0..attempts {
            if self.handle.is_peer_connected(peer_id).await {
                return true;
            }
            if i + 1 < attempts {
                tokio::time::sleep(delay).await;
            }
        }
        false
    }

    pub fn list_contacts(&self) -> anyhow::Result<Vec<Contact>> {
        self.storage.all_contacts()
    }

    pub fn list_accepted_contacts(&self) -> anyhow::Result<Vec<Contact>> {
        self.storage.contacts_with_status(ContactStatus::Accepted)
    }

    pub fn list_pending_contacts(&self) -> anyhow::Result<Vec<Contact>> {
        self.storage.contacts_with_status(ContactStatus::Pending)
    }

    fn resolve_mail_recipient(&self, to: &str) -> anyhow::Result<String> {
        let trimmed = to.trim();
        if trimmed.is_empty() {
            anyhow::bail!("recipient required");
        }
        if trimmed.starts_with("12D3") {
            if let Ok(Some(contact)) = self.storage.contact_by_id(trimmed) {
                return Ok(contact.peer_id);
            }
            return Ok(trimmed.to_string());
        }
        for contact in self.storage.contacts_with_status(ContactStatus::Accepted)? {
            if contact.display_name.eq_ignore_ascii_case(trimmed) {
                return Ok(contact.peer_id);
            }
        }
        anyhow::bail!("unknown recipient — pick a contact or use a peer ID");
    }

    /// Parse QR/deep-link invite: seed E2E key (v2), add contact, dial direct + relay.
    pub async fn connect_from_invite(self: Arc<Self>, raw: &str) -> anyhow::Result<()> {
        let target = mycelium_l0_facade::invite::parse_invite(raw.trim())
            .ok_or_else(|| anyhow::anyhow!("invalid invite"))?;
        match target {
            mycelium_l0_facade::invite::InviteTarget::Peer(info) => {
                let target_peer = info.peer_id.clone();
                self.register_transport_for_peer(&info).await?;
                let label = info.peer_id.chars().take(16).collect::<String>();
                let contact = self.storage.upsert_contact_with_account(
                    &info.peer_id,
                    &label,
                    ContactStatus::Accepted,
                    info.account_id.as_deref(),
                )?;
                self.notifier
                    .on_contact_added(&info.peer_id, &contact.display_name);
                self.schedule_contact_hello(target_peer);
            }
            mycelium_l0_facade::invite::InviteTarget::Multiaddr(addr) => {
                self.handle
                    .send(NodeCommand::AddBootstrapPeer { multiaddr: addr })
                    .await?;
            }
        }
        Ok(())
    }

    /// BLE mesh handoff (F1): dial and seed keys only — no chat contact, no ContactHello.
    pub async fn register_transport_from_invite(self: Arc<Self>, raw: &str) -> anyhow::Result<()> {
        let target = mycelium_l0_facade::invite::parse_invite(raw.trim())
            .ok_or_else(|| anyhow::anyhow!("invalid invite"))?;
        match target {
            mycelium_l0_facade::invite::InviteTarget::Peer(info) => {
                self.register_transport_for_peer(&info).await?;
            }
            mycelium_l0_facade::invite::InviteTarget::Multiaddr(addr) => {
                self.handle
                    .send(NodeCommand::AddBootstrapPeer { multiaddr: addr })
                    .await?;
            }
        }
        Ok(())
    }

    async fn register_transport_for_peer(
        &self,
        info: &mycelium_l0_facade::invite::PeerInviteInfo,
    ) -> anyhow::Result<()> {
        if let Some(enc) = &info.enc_pubkey_hex {
            self.handle.seed_peer_enc_key(&info.peer_id, enc).await?;
        }
        if let Some(bundle) = &info.ratchet_bundle {
            self.handle
                .seed_peer_ratchet_bundle(&info.peer_id, bundle.clone())
                .await?;
        }
        for addr in info.dial_multiaddrs() {
            self.handle
                .send(NodeCommand::AddBootstrapPeer { multiaddr: addr })
                .await?;
        }
        Ok(())
    }

    fn schedule_contact_hello(self: &Arc<Self>, target_peer: String) {
        let this = Arc::clone(self);
        tokio::spawn(async move {
            for attempt in 0..15 {
                if this
                    .wait_peer_connected(&target_peer, 1, std::time::Duration::from_secs(2))
                    .await
                {
                    match this.send_contact_hello(&target_peer).await {
                        Ok(()) => return,
                        Err(e) if attempt + 1 >= 15 => {
                            warn!("contact hello to {target_peer}: {e}");
                        }
                        Err(e) => {
                            warn!("contact hello retry {target_peer} ({attempt}): {e}");
                        }
                    }
                }
                if attempt + 1 >= 15 {
                    warn!("contact hello: {target_peer} never connected");
                    return;
                }
            }
        });
    }

    async fn send_contact_hello(&self, to_peer: &str) -> anyhow::Result<()> {
        let display_name = self.display_name.read().await.clone();
        let hello = self
            .handle
            .build_contact_hello(&self.local_peer_id, &display_name)?;
        let payload = hello.encode()?;
        self.handle
            .mesh_send_direct(
                to_peer.to_string(),
                "[contact:hello]".into(),
                payload,
                OP_DIRECT_MESSAGE_SEND,
            )
            .await
    }

    async fn handle_contact_hello(&self, from_peer: &str, payload: &[u8]) {
        match mycelium_l0_facade::contact_hello::ContactHello::decode(payload) {
            Ok(hello) if hello.verify() => {
                if hello.peer_id != from_peer {
                    warn!(
                        "rejected contact hello: peer_id mismatch ({from_peer} vs {})",
                        hello.peer_id
                    );
                    return;
                }
                if self.storage.is_peer_blocked(from_peer).unwrap_or(false) {
                    return;
                }
                let had_accepted = self
                    .storage
                    .contact_by_id(from_peer)
                    .ok()
                    .flatten()
                    .is_some_and(|c| c.status == ContactStatus::Accepted);
                if let Err(e) = self
                    .handle
                    .seed_peer_enc_key(from_peer, &hello.enc_pubkey_hex)
                    .await
                {
                    warn!("contact hello enc key seed for {from_peer}: {e}");
                }
                let name = if hello.display_name.trim().is_empty() {
                    from_peer.chars().take(16).collect::<String>()
                } else {
                    hello.display_name.trim().to_string()
                };
                match self.storage.upsert_contact_with_account(
                    from_peer,
                    &name,
                    ContactStatus::Accepted,
                    Some(&hello.account_id),
                ) {
                    Ok(c) => {
                        self.notifier.on_contact_added(from_peer, &c.display_name);
                    }
                    Err(e) => warn!("contact hello upsert {from_peer}: {e}"),
                }
                if !had_accepted {
                    if let Err(e) = self.send_contact_hello(from_peer).await {
                        warn!("contact hello reply to {from_peer}: {e}");
                    }
                }
            }
            Ok(_) => warn!("rejected contact hello from {from_peer}: bad signature"),
            Err(e) => warn!("contact hello decode from {from_peer}: {e}"),
        }
    }

    pub fn add_contact(
        &self,
        peer_id: &str,
        display_name: &str,
        accepted: bool,
    ) -> anyhow::Result<Contact> {
        let status = if accepted {
            ContactStatus::Accepted
        } else {
            ContactStatus::Pending
        };
        let name = if display_name.is_empty() {
            peer_id.to_string()
        } else {
            display_name.to_string()
        };
        self.storage.upsert_contact(peer_id, &name, status)
    }

    pub fn accept_contact(&self, peer_id: &str) -> anyhow::Result<Contact> {
        let existing = self.storage.contact_by_id(peer_id)?;
        let name = existing
            .map(|c| c.display_name)
            .unwrap_or_else(|| peer_id.to_string());
        self.storage
            .upsert_contact(peer_id, &name, ContactStatus::Accepted)
    }

    pub fn reject_contact(&self, peer_id: &str) -> anyhow::Result<()> {
        self.storage.delete_contact(peer_id)
    }

    pub fn block_contact(&self, peer_id: &str) -> anyhow::Result<Contact> {
        let existing = self.storage.contact_by_id(peer_id)?;
        let name = existing
            .as_ref()
            .map(|c| c.display_name.clone())
            .unwrap_or_else(|| peer_id.to_string());
        let account_id = existing.and_then(|c| c.account_id);
        self.storage.upsert_contact_with_account(
            peer_id,
            &name,
            ContactStatus::Blocked,
            account_id.as_deref(),
        )
    }

    pub fn unblock_contact(&self, peer_id: &str) -> anyhow::Result<Contact> {
        let existing = self.storage.contact_by_id(peer_id)?;
        let name = existing
            .as_ref()
            .map(|c| c.display_name.clone())
            .unwrap_or_else(|| peer_id.to_string());
        let account_id = existing.and_then(|c| c.account_id);
        self.storage.upsert_contact_with_account(
            peer_id,
            &name,
            ContactStatus::Accepted,
            account_id.as_deref(),
        )
    }

    pub fn list_blocked_contacts(&self) -> anyhow::Result<Vec<Contact>> {
        self.storage.contacts_with_status(ContactStatus::Blocked)
    }

    pub async fn post_sos(
        &self,
        geohash: &str,
        message: &str,
        ttl_secs: u64,
    ) -> anyhow::Result<()> {
        use crate::sos::{SosPayload, SOS_SCOPE};
        let payload = SosPayload {
            geohash: geohash.trim().to_string(),
            message: message.trim().to_string(),
            created_at_ms: now_ms(),
        };
        payload.validate()?;
        let body = payload.encode_json()?;
        self.post_bulletin(
            SOS_SCOPE.to_string(),
            format!("SOS {}", payload.geohash),
            body,
            ttl_secs,
        )
        .await
    }

    pub fn list_sos_posts(&self) -> anyhow::Result<Vec<crate::sos::SosPayload>> {
        use crate::sos::{SosPayload, SOS_SCOPE};
        let posts = self.storage.bulletins_for_scope(SOS_SCOPE)?;
        let mut out = Vec::new();
        for post in posts {
            if let Ok(p) = SosPayload::decode_json(&post.body) {
                out.push(p);
            }
        }
        Ok(out)
    }

    pub async fn set_listen_sos(&self, enabled: bool) -> anyhow::Result<()> {
        self.listen_sos.store(enabled, Ordering::Relaxed);
        if enabled {
            self.handle
                .send(NodeCommand::SubscribeBulletinScope(
                    crate::sos::SOS_SCOPE.into(),
                ))
                .await?;
        } else {
            self.handle
                .send(NodeCommand::UnsubscribeBulletinScope(
                    crate::sos::SOS_SCOPE.into(),
                ))
                .await?;
        }
        Ok(())
    }

    pub fn is_listen_sos(&self) -> bool {
        self.listen_sos.load(Ordering::Relaxed)
    }

    pub fn remove_contact(&self, peer_id: &str) -> anyhow::Result<()> {
        self.storage.delete_contact(peer_id)
    }

    pub fn bulletins_for_scope(&self, scope: &str) -> anyhow::Result<Vec<BulletinPost>> {
        self.storage.bulletins_for_scope(scope)
    }

    pub fn mail_inbox(&self, limit: usize) -> anyhow::Result<Vec<MailMessage>> {
        self.storage.inbox(limit)
    }

    pub fn mail_sent(&self, limit: usize) -> anyhow::Result<Vec<MailMessage>> {
        self.storage.sent(limit)
    }

    /// Publishes a signed app listing on gossip scope `mycelium/appstore/v1`.
    pub async fn publish_app_listing(&self, listing: &AppStoreListing) -> anyhow::Result<()> {
        if !listing.verify_signature()? {
            anyhow::bail!("listing has invalid signature – sign with developer key first");
        }
        let payload = AppMessage {
            app_id: AppId::AppStore,
            payload: AppPayload::AppStoreListing(Box::new(listing.clone())),
        }
        .encode()?;
        self.handle
            .mesh_broadcast(
                "mycelium/appstore/v1".into(),
                format!("[app:{}]", listing.manifest.id),
                payload,
                OP_GOSSIP_BULLETIN_PUBLISH,
            )
            .await?;
        tracing::info!(
            "published listing: {} v{}",
            listing.manifest.id,
            listing.manifest.version
        );
        Ok(())
    }

    /// Publishes a signed revocation on gossip scope `mycelium/appstore/revocations/v1`.
    pub async fn publish_app_revocation(
        &self,
        entry: &crate::miniapp::revocation::RevocationGossip,
    ) -> anyhow::Result<()> {
        if !entry.verify_signature()? {
            anyhow::bail!("revocation has invalid signature");
        }
        let payload = AppMessage {
            app_id: AppId::MiniAppRevocation,
            payload: AppPayload::MiniAppRevocation(Box::new(entry.clone())),
        }
        .encode()?;
        self.handle
            .mesh_broadcast(
                "mycelium/appstore/revocations/v1".into(),
                format!("[revoke:{}]", entry.app_id),
                payload,
                OP_GOSSIP_BULLETIN_PUBLISH,
            )
            .await?;
        if let Some(ref store) = self.app_store {
            let _ = store.ingest_revocation_gossip(entry);
        }
        tracing::info!("published revocation for {}", entry.app_id);
        Ok(())
    }

    pub fn mark_mail_read(&self, mail_id: &uuid::Uuid) -> anyhow::Result<()> {
        self.storage.mark_read(mail_id)
    }

    pub fn is_mail_read(&self, mail_id: &uuid::Uuid) -> anyhow::Result<bool> {
        self.storage.is_read(mail_id)
    }
}

fn normalize_bulletin_scope(scope: &str) -> anyhow::Result<String> {
    let scope = scope.trim();
    if scope.is_empty() {
        anyhow::bail!("scope required");
    }
    Ok(if scope.starts_with("mycelium/") {
        scope.to_lowercase()
    } else {
        scope.to_string()
    })
}

#[derive(Debug, Clone)]
pub struct ChatIncoming {
    pub from_peer: String,
    pub message: ChatMessage,
}

pub struct AppInbox {
    pub chat_rx: broadcast::Receiver<ChatIncoming>,
    pub bulletin_rx: broadcast::Receiver<BulletinPost>,
    pub mail_rx: broadcast::Receiver<MailMessage>,
    pub appstore_rx: broadcast::Receiver<AppStoreListing>,
}

#[cfg(test)]
mod dispatch_tests {
    use super::*;
    use crate::envelope::{AppId, AppMessage, AppPayload, BulletinPost};
    use crate::miniapp::manifest::MiniAppManifest;
    use crate::miniapp::store::{AppSource, AppStore};
    use crate::proximity::{PresenceProfile, PresenceSignal, ProximityMatchIntent};
    use mycelium_l0_facade::data::Envelope;
    use mycelium_l0_facade::{encrypt_for, encrypt_group, parse_x25519_public_hex, EncKey};
    use mycelium_l1_facade::DirectMessage;

    fn sample_manifest(peer_id: Option<String>) -> MiniAppManifest {
        MiniAppManifest {
            id: "com.test.app".into(),
            name: "Test".into(),
            description: "d".into(),
            version: "0.1.0".into(),
            developer: "T".into(),
            developer_peer_id: peer_id,
            entry: "index.html".into(),
            icon_base64: None,
            permissions: vec![],
            min_mycelium_version: "0.1.0".into(),
            accepts_payments: false,
            payment_address: None,
            categories: vec![],
            runtime: "webview".into(),
            bulletin_scopes: vec![],
            reproducible_build: None,
        }
    }

    #[tokio::test]
    async fn appstore_listing_cached_after_dispatch() {
        let dir = tempfile::tempdir().expect("tempdir");
        let base = dir.path().to_str().expect("path");
        let store = Arc::new(AppStore::open(&format!("{base}/miniapp")).expect("store"));
        let storage = Arc::new(AppStorage::open(&format!("{base}/app")).expect("storage"));

        let storage_key = blake3::derive_key("mycelium-test-vault", base.as_bytes());
        let mut config = mycelium_l1_facade::NodeConfig::with_defaults(base);
        config.storage_key = Some(storage_key);
        let (runner, handle) = mycelium_l1_facade::NodeRunner::new(config).expect("node");
        tokio::spawn(async move {
            let _ = runner.run().await;
        });

        let keypair = mycelium_l1_facade::secrets::load_or_create_keypair(
            &format!("{base}/identity"),
            Some(storage_key),
        )
        .expect("identity");
        let peer_id = keypair.public().to_peer_id().to_string();
        let manifest = sample_manifest(Some(peer_id.clone()));
        let listing = AppStoreListing::new_signed(
            manifest,
            b"bundle-bytes",
            vec![AppSource::Peer(peer_id.clone())],
            &keypair,
        )
        .expect("signed listing");

        let (app_node, _) = AppNode::new(
            handle,
            peer_id,
            "test".into(),
            storage,
            Arc::new(crate::notify::NoopNotifier),
            None,
            Some(store.clone()),
            mycelium_l0_facade::secrets::load_or_create_enc_keypair(
                &format!("{base}/identity"),
                Some(storage_key),
            )
            .expect("enc keypair"),
        );
        let app_node = Arc::new(app_node);

        let app_msg = AppMessage {
            app_id: AppId::AppStore,
            payload: AppPayload::AppStoreListing(Box::new(listing.clone())),
        };
        let payload = app_msg.encode().expect("encode");
        let dm = DirectMessage {
            envelope: Envelope::new("remote-peer".into(), None, payload),
            body: "[appstore]".into(),
        };
        app_node.dispatch_incoming(&dm).await;

        let rows = store.browse_listings().expect("browse");
        let cached = rows
            .iter()
            .find(|r| r.manifest.id == listing.manifest.id)
            .expect("dispatched listing cached");
        assert_eq!(cached.manifest.version, listing.manifest.version);
    }

    #[test]
    fn sd081_enc_key_error_is_actionable() {
        let err = enc_key_not_yet_exchanged_err();
        assert!(err.to_string().contains("enc_key_not_yet_exchanged"));
    }

    async fn spawn_test_app_node() -> (Arc<AppNode>, Arc<AppStorage>, EncKey, tempfile::TempDir) {
        let dir = tempfile::tempdir().expect("tempdir");
        let base = dir.path().to_str().expect("path");
        let storage = Arc::new(AppStorage::open(&format!("{base}/app")).expect("storage"));
        let storage_key = blake3::derive_key("mycelium-test-vault", base.as_bytes());
        let mut config = mycelium_l1_facade::NodeConfig::with_defaults(base);
        config.storage_key = Some(storage_key);
        let (runner, handle) = mycelium_l1_facade::NodeRunner::new(config).expect("node");
        tokio::spawn(async move {
            let _ = runner.run().await;
        });
        let enc_keypair = mycelium_l0_facade::secrets::load_or_create_enc_keypair(
            &format!("{base}/identity"),
            Some(storage_key),
        )
        .expect("enc keypair");
        let keypair = mycelium_l1_facade::secrets::load_or_create_keypair(
            &format!("{base}/identity"),
            Some(storage_key),
        )
        .expect("identity");
        let peer_id = keypair.public().to_peer_id().to_string();
        let (app_node, _) = AppNode::new(
            handle,
            peer_id,
            "test".into(),
            storage.clone(),
            Arc::new(crate::notify::NoopNotifier),
            None,
            None,
            enc_keypair.clone(),
        );
        (Arc::new(app_node), storage, enc_keypair, dir)
    }

    #[tokio::test]
    async fn encrypted_bulletin_direct_relay_stored_decrypted() {
        use crate::scope_key::ScopeKey;
        use encrypt_group;

        let (app_node, storage, _enc, _dir) = spawn_test_app_node().await;
        let sk = ScopeKey::new("mycelium/rescue/test".into(), "Team Alpha".into());
        storage.save_scope_key(&sk).expect("save scope key");

        let now = now_ms();
        let post = BulletinPost {
            id: uuid::Uuid::new_v4(),
            from_display_name: "alice".into(),
            title: "help".into(),
            body: "need water".into(),
            scope: sk.scope.clone(),
            timestamp_ms: now,
            expires_at_ms: now + 3_600_000,
        };
        let plaintext = AppMessage {
            app_id: AppId::Bulletin,
            payload: AppPayload::Bulletin(post),
        }
        .encode()
        .expect("encode");
        let encrypted = encrypt_group(&plaintext, &sk.key).expect("encrypt");
        let mut payload = b"enc1:".to_vec();
        payload.extend_from_slice(&encrypted);

        let dm = DirectMessage {
            envelope: Envelope::new("remote-peer".into(), None, payload),
            body: format!("[bulletin:{}]", sk.scope),
        };
        app_node.dispatch_incoming(&dm).await;

        let posts = storage.bulletins_for_scope(&sk.scope).expect("list");
        assert_eq!(posts.len(), 1);
        assert_eq!(posts[0].body, "need water");
        assert_eq!(posts[0].title, "help");
    }

    #[tokio::test]
    async fn proximity_presence_visible_when_listening() {
        let (app_node, _storage, _enc, _dir) = spawn_test_app_node().await;
        app_node
            .start_proximity(
                PresenceProfile {
                    display_name: Some("me".into()),
                    ..PresenceProfile::default()
                },
                300,
            )
            .await
            .expect("start proximity");

        let peer_enc = EncKey::generate();
        let signal = PresenceSignal::new(
            peer_enc.public_hex(),
            PresenceProfile {
                display_name: Some("bob".into()),
                ..PresenceProfile::default()
            },
            300,
        );
        let payload = signal.encode().expect("encode");
        let dm = DirectMessage {
            envelope: Envelope::new("remote".into(), None, payload),
            body: "[proximity]".into(),
        };
        app_node.dispatch_incoming(&dm).await;

        let nearby = app_node.nearby_profiles().await;
        assert_eq!(nearby.len(), 1);
        assert_eq!(
            nearby[0].signal.profile.display_name.as_deref(),
            Some("bob")
        );
    }

    #[tokio::test]
    async fn proximity_mutual_match_after_bidirectional_interest() {
        let (app_node, _storage, _local_enc, _dir) = spawn_test_app_node().await;
        app_node
            .start_proximity(PresenceProfile::default(), 300)
            .await
            .expect("start");

        let peer_enc = EncKey::generate();
        let peer_hex = peer_enc.public_hex();
        let signal = PresenceSignal::new(peer_hex.clone(), PresenceProfile::default(), 300);
        app_node
            .dispatch_incoming(&DirectMessage {
                envelope: Envelope::new("remote".into(), None, signal.encode().unwrap()),
                body: "[proximity]".into(),
            })
            .await;

        let intent = ProximityMatchIntent::new(peer_hex.clone());
        app_node
            .dispatch_incoming(&DirectMessage {
                envelope: Envelope::new("remote".into(), None, intent.encode().unwrap()),
                body: "[proximity]".into(),
            })
            .await;

        let is_mutual = app_node
            .express_proximity_interest(peer_hex)
            .await
            .expect("express interest");
        assert!(is_mutual);
        let nearby = app_node.nearby_profiles().await;
        assert!(nearby.iter().any(|e| e.is_mutual));
    }

    #[tokio::test]
    async fn encrypted_chat_payload_roundtrip_and_dispatch() {
        use crate::envelope::ChatMessage;
        use encrypt_for;
        use mycelium_l0_facade::e2e_direct_wrap;

        let remote_peer = "12D3KooWRemotePeerIdForTestOnly12";
        let (app_node, storage, local_enc, _dir) = spawn_test_app_node().await;

        let chat = ChatMessage {
            id: uuid::Uuid::new_v4(),
            from_display_name: "Remote".into(),
            body: "encrypted hello".into(),
            timestamp_ms: now_ms(),
            reply_to: None,
        };
        let app_msg = AppMessage {
            app_id: AppId::Chat,
            payload: AppPayload::Chat(chat.clone()),
        };
        let plaintext = app_msg.encode().expect("encode");
        let wrapped = e2e_direct_wrap::wrap_inner(remote_peer, &plaintext);
        let local_pk = parse_x25519_public_hex(&local_enc.public_hex()).expect("pk");
        let encrypted = encrypt_for(&wrapped, &local_pk).expect("encrypt");
        let plain = local_enc.decrypt_direct(&encrypted).expect("decrypt");
        let (logical_from, app_bytes) = e2e_direct_wrap::unwrap_inner(&plain, "relay-hop");
        assert_eq!(logical_from, remote_peer);

        app_node
            .dispatch_incoming(&DirectMessage {
                envelope: Envelope::new(logical_from, None, app_bytes),
                body: "[encrypted]".into(),
            })
            .await;

        let pending = app_node.list_pending_contacts().expect("pending");
        assert_eq!(pending.len(), 1);
        assert_eq!(pending[0].peer_id, remote_peer);

        let history = storage.chat_history(remote_peer, 10).expect("history");
        assert_eq!(history.len(), 1);
        assert_eq!(history[0].body, "encrypted hello");

        app_node.accept_contact(remote_peer).expect("accept");
        assert_eq!(
            app_node.list_accepted_contacts().expect("accepted").len(),
            1
        );
    }

    #[tokio::test]
    async fn inbound_chat_from_unknown_peer_is_pending() {
        use crate::envelope::ChatMessage;

        let (app_node, _, _, _dir) = spawn_test_app_node().await;
        let remote = "12D3KooWUnknownSenderForTestOnly1";
        let chat = ChatMessage {
            id: uuid::Uuid::new_v4(),
            from_display_name: "Stranger".into(),
            body: "hi there".into(),
            timestamp_ms: now_ms(),
            reply_to: None,
        };
        let app_msg = AppMessage {
            app_id: AppId::Chat,
            payload: AppPayload::Chat(chat),
        };
        app_node
            .dispatch_incoming(&DirectMessage {
                envelope: Envelope::new(remote.into(), None, app_msg.encode().unwrap()),
                body: "hi".into(),
            })
            .await;

        assert_eq!(app_node.list_pending_contacts().expect("pending").len(), 1);
        assert!(app_node
            .list_accepted_contacts()
            .expect("accepted")
            .is_empty());
    }

    #[tokio::test]
    async fn send_chat_encrypted_requires_peer_key() {
        let (app_node, _, _, _dir) = spawn_test_app_node().await;
        let remote = "12D3KooWRemoteNeedsKeyForTest1";
        app_node
            .add_contact(remote, "Remote", true)
            .expect("contact");
        let err = app_node
            .send_chat(Some(remote.into()), "test".into())
            .await
            .expect_err("must fail without enc key");
        assert!(err.to_string().contains("enc_key_not_yet_exchanged"));
    }
}
