use async_trait::async_trait;
use mycelium_app::envelope::AppMessage;
use mycelium_app::groups::Group;
use mycelium_app::node::AppNode;
use mycelium_app::notify::NoopNotifier;
use mycelium_app::storage::AppStorage;
use mycelium_coin::{address_from_keypair, CoinNode, CoinTransport, LocalLedger};
use mycelium_node::{NodeCommand, NodeConfig, NodeHandle, NodeRunner};
use serde_json::json;
use std::sync::Arc;
use tauri::{AppHandle, Emitter, State};
use tokio::sync::RwLock;

pub struct AppState {
    handle: NodeHandle,
    app_node: Arc<AppNode>,
    app_storage: Arc<AppStorage>,
    app_store: Arc<mycelium_app::miniapp::AppStore>,
    coin_node: Arc<CoinNode>,
    local_peer_id: String,
    coin_identity_path: String,
}

type SharedState = Arc<RwLock<Option<AppState>>>;

#[derive(Clone)]
struct DesktopCoinTransport {
    handle: NodeHandle,
}

#[async_trait]
impl CoinTransport for DesktopCoinTransport {
    async fn broadcast_coin_inner(&self, coin_inner: Vec<u8>) -> anyhow::Result<()> {
        let payload = AppMessage::encode_coin_payload(&coin_inner)?;
        self.handle
            .send(NodeCommand::BroadcastPayload {
                scope: "mycelium/coin".to_string(),
                body: "coin:broadcast".to_string(),
                payload,
            })
            .await
    }

    async fn send_direct_coin_inner(
        &self,
        to_peer: String,
        coin_inner: Vec<u8>,
    ) -> anyhow::Result<()> {
        let payload = AppMessage::encode_coin_payload(&coin_inner)?;
        self.handle
            .send(NodeCommand::SendDirectPayload {
                to_peer,
                body: "coin:direct".to_string(),
                payload,
            })
            .await
    }
}

#[tauri::command]
fn get_default_db_path() -> String {
    dirs::data_dir()
        .unwrap_or_else(|| std::path::PathBuf::from("."))
        .join("mycelium")
        .to_string_lossy()
        .to_string()
}

#[tauri::command]
async fn start_node(
    state: State<'_, SharedState>,
    db_path: String,
    display_name: String,
    bootstrap_peers: Vec<String>,
) -> Result<String, String> {
    {
        let guard = state.read().await;
        if let Some(existing) = guard.as_ref() {
            return Ok(existing.local_peer_id.clone());
        }
    }

    let config = NodeConfig {
        listen_addr: "/ip4/0.0.0.0/tcp/0".parse().expect("valid listen addr"),
        db_path: db_path.clone(),
        keypair_path: Some(format!("{db_path}/identity")),
        forwarding_interval_ms: 500,
        sync_interval_secs: 30,
        bootstrap_peers,
        connectivity_rx: None,
        display_name: Some(display_name.clone()),
    };
    let (runner, handle) = NodeRunner::new(config).map_err(|e| e.to_string())?;
    let local_peer_id = runner.local_peer_id().to_string();
    tauri::async_runtime::spawn(async move {
        let _ = runner.run().await;
    });

    let app_storage =
        Arc::new(AppStorage::open(&format!("{db_path}/app")).map_err(|e| e.to_string())?);
    let app_store = Arc::new(
        mycelium_app::miniapp::AppStore::open(&format!("{db_path}/miniapp"))
            .map_err(|e| e.to_string())?,
    );
    let coin_ledger =
        Arc::new(LocalLedger::open(&format!("{db_path}/coin")).map_err(|e| e.to_string())?);
    let identity_path = format!("{db_path}/identity");
    let coin_keypair =
        mycelium_node::load_or_create_keypair(&identity_path).map_err(|e| e.to_string())?;
    let coin_addr = address_from_keypair(&coin_keypair);
    let coin_transport = Arc::new(DesktopCoinTransport {
        handle: handle.clone(),
    });
    let coin_node = Arc::new(CoinNode::new(
        coin_ledger,
        coin_transport,
        coin_addr,
        local_peer_id.clone(),
        identity_path,
    ));

    let (app_node, _inbox) = AppNode::new(
        handle.clone(),
        local_peer_id.clone(),
        display_name,
        app_storage.clone(),
        Arc::new(NoopNotifier),
        Some(coin_node.clone()),
    );
    let app_node = Arc::new(app_node);
    app_node.clone().start_incoming_task();

    *state.write().await = Some(AppState {
        handle,
        app_node,
        app_storage,
        app_store,
        coin_node,
        local_peer_id: local_peer_id.clone(),
        coin_identity_path: format!("{db_path}/identity"),
    });

    Ok(local_peer_id)
}

#[tauri::command]
async fn get_peers(state: State<'_, SharedState>) -> Result<Vec<String>, String> {
    let guard = state.read().await;
    let Some(s) = guard.as_ref() else {
        return Ok(vec![]);
    };
    Ok(s.handle.known_peers().await)
}

#[tauri::command]
async fn get_metrics(state: State<'_, SharedState>) -> Result<serde_json::Value, String> {
    let guard = state.read().await;
    let Some(s) = guard.as_ref() else {
        return Ok(json!({}));
    };
    let m = s.handle.metrics().await;
    Ok(json!({
        "messages_forwarded": m.messages_forwarded,
        "messages_dropped_ttl": m.messages_dropped_ttl,
        "messages_dropped_queue": m.messages_dropped_queue,
        "messages_delivered_local": m.messages_delivered_local,
        "pending_queue_size": m.pending_queue_size,
        "seen_cache_size": m.seen_cache_size,
    }))
}

#[tauri::command]
async fn send_chat(
    state: State<'_, SharedState>,
    to_peer: String,
    body: String,
) -> Result<(), String> {
    let guard = state.read().await;
    let s = guard
        .as_ref()
        .ok_or_else(|| "node not started".to_string())?;
    s.app_node
        .send_chat(Some(to_peer), body)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
async fn peer_has_enc_key(state: State<'_, SharedState>, peer_id: String) -> Result<bool, String> {
    let guard = state.read().await;
    let s = guard
        .as_ref()
        .ok_or_else(|| "node not started".to_string())?;
    Ok(s.handle.has_enc_key_for(&peer_id).await)
}

#[tauri::command]
async fn send_chat_encrypted(
    state: State<'_, SharedState>,
    to_peer: String,
    body: String,
) -> Result<String, String> {
    use mycelium_app::node::EncryptionResult;
    let guard = state.read().await;
    let s = guard
        .as_ref()
        .ok_or_else(|| "node not started".to_string())?;
    let r = s
        .app_node
        .send_chat_encrypted(to_peer, body)
        .await
        .map_err(|e| e.to_string())?;
    Ok(match r {
        EncryptionResult::Encrypted => "encrypted".to_string(),
        EncryptionResult::Unencrypted => "unencrypted".to_string(),
    })
}

#[tauri::command]
async fn chat_history(
    state: State<'_, SharedState>,
    peer_id: String,
    limit: u32,
) -> Result<Vec<serde_json::Value>, String> {
    let guard = state.read().await;
    let Some(s) = guard.as_ref() else {
        return Ok(vec![]);
    };
    Ok(s.app_storage
        .chat_history(&peer_id, limit as usize)
        .unwrap_or_default()
        .into_iter()
        .map(|m| {
            json!({
                "id": m.id.to_string(),
                "from_display_name": m.from_display_name,
                "body": m.body,
                "timestamp_ms": m.timestamp_ms,
            })
        })
        .collect())
}

#[tauri::command]
async fn send_mail(
    state: State<'_, SharedState>,
    to_peer: String,
    subject: String,
    body: String,
) -> Result<(), String> {
    let guard = state.read().await;
    let s = guard
        .as_ref()
        .ok_or_else(|| "node not started".to_string())?;
    s.app_node
        .send_mail(to_peer, subject, body, vec![])
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
async fn mail_inbox(
    state: State<'_, SharedState>,
    limit: u32,
) -> Result<Vec<serde_json::Value>, String> {
    let guard = state.read().await;
    let Some(s) = guard.as_ref() else {
        return Ok(vec![]);
    };
    Ok(s.app_storage
        .inbox(limit as usize)
        .unwrap_or_default()
        .into_iter()
        .map(|m| {
            json!({
                "id": m.id.to_string(),
                "from_peer": m.from_peer,
                "from_display_name": m.from_display_name,
                "subject": m.subject,
                "body": m.body,
                "timestamp_ms": m.timestamp_ms,
                "is_read": s.app_storage.is_read(&m.id).unwrap_or(false),
            })
        })
        .collect())
}

#[tauri::command]
async fn wallet_balance(state: State<'_, SharedState>) -> Result<serde_json::Value, String> {
    let guard = state.read().await;
    let Some(s) = guard.as_ref() else {
        return Ok(json!({"confirmed_muon": 0u64, "pending_muon": 0u64}));
    };
    let (confirmed, pending) = s.coin_node.balance().unwrap_or((0, 0));
    Ok(json!({"confirmed_muon": confirmed, "pending_muon": pending}))
}

#[tauri::command]
async fn wallet_address(state: State<'_, SharedState>) -> Result<String, String> {
    let guard = state.read().await;
    Ok(guard
        .as_ref()
        .map(|s| s.coin_node.local_address().to_string())
        .unwrap_or_default())
}

#[tauri::command]
async fn post_bulletin(
    state: State<'_, SharedState>,
    scope: String,
    title: String,
    body: String,
    ttl_secs: u64,
) -> Result<(), String> {
    let guard = state.read().await;
    let s = guard
        .as_ref()
        .ok_or_else(|| "node not started".to_string())?;
    s.app_node
        .post_bulletin(scope, title, body, ttl_secs)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
async fn bulletins_for_scope(
    state: State<'_, SharedState>,
    scope: String,
) -> Result<Vec<serde_json::Value>, String> {
    let guard = state.read().await;
    let Some(s) = guard.as_ref() else {
        return Ok(vec![]);
    };
    Ok(s.app_storage
        .bulletins_for_scope(&scope)
        .unwrap_or_default()
        .into_iter()
        .map(|p| {
            json!({
                "id": p.id.to_string(),
                "from_display_name": p.from_display_name,
                "title": p.title,
                "body": p.body,
                "scope": p.scope,
                "timestamp_ms": p.timestamp_ms,
                "expires_at_ms": p.expires_at_ms,
            })
        })
        .collect())
}

#[tauri::command]
async fn add_peer(state: State<'_, SharedState>, multiaddr: String) -> Result<(), String> {
    let guard = state.read().await;
    let s = guard
        .as_ref()
        .ok_or_else(|| "node not started".to_string())?;
    s.handle
        .send(NodeCommand::AddBootstrapPeer { multiaddr })
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
async fn list_groups(state: State<'_, SharedState>) -> Result<Vec<serde_json::Value>, String> {
    let guard = state.read().await;
    let s = guard
        .as_ref()
        .ok_or_else(|| "node not started".to_string())?;
    let groups = s
        .app_node
        .storage()
        .all_groups()
        .map_err(|e| e.to_string())?;
    Ok(groups
        .into_iter()
        .map(|g| {
            json!({
                "id": g.id,
                "name": g.name,
                "member_count": g.members.len(),
                "created_at_ms": g.created_at_ms,
            })
        })
        .collect())
}

#[tauri::command]
async fn create_group(
    state: State<'_, SharedState>,
    name: String,
) -> Result<serde_json::Value, String> {
    let guard = state.read().await;
    let s = guard
        .as_ref()
        .ok_or_else(|| "node not started".to_string())?;
    let g = Group::new(name);
    s.app_node
        .storage()
        .save_group(&g)
        .map_err(|e| e.to_string())?;
    Ok(json!({
        "id": g.id,
        "name": g.name,
        "member_count": g.members.len(),
        "created_at_ms": g.created_at_ms,
    }))
}

#[tauri::command]
async fn import_group_invite(
    state: State<'_, SharedState>,
    json_str: String,
) -> Result<serde_json::Value, String> {
    let guard = state.read().await;
    let s = guard
        .as_ref()
        .ok_or_else(|| "node not started".to_string())?;
    let g = Group::from_invite(&json_str).map_err(|e| e.to_string())?;
    s.app_node
        .storage()
        .save_group(&g)
        .map_err(|e| e.to_string())?;
    Ok(json!({
        "id": g.id,
        "name": g.name,
        "member_count": g.members.len(),
        "created_at_ms": g.created_at_ms,
    }))
}

#[tauri::command]
async fn export_group_invite(
    state: State<'_, SharedState>,
    group_id: String,
) -> Result<String, String> {
    let guard = state.read().await;
    let s = guard
        .as_ref()
        .ok_or_else(|| "node not started".to_string())?;
    Ok(s.app_node
        .storage()
        .group_by_id(&group_id)
        .map_err(|e| e.to_string())?
        .map(|g| g.export_invite())
        .unwrap_or_default())
}

#[tauri::command]
async fn send_group_message(
    state: State<'_, SharedState>,
    group_id: String,
    body: String,
) -> Result<(), String> {
    let guard = state.read().await;
    let s = guard
        .as_ref()
        .ok_or_else(|| "node not started".to_string())?;
    let g = s
        .app_node
        .storage()
        .group_by_id(&group_id)
        .map_err(|e| e.to_string())?
        .ok_or_else(|| "unknown group".to_string())?;
    s.app_node
        .send_group_message(&g, body)
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
async fn delete_group(state: State<'_, SharedState>, group_id: String) -> Result<(), String> {
    let guard = state.read().await;
    let s = guard
        .as_ref()
        .ok_or_else(|| "node not started".to_string())?;
    s.app_node
        .storage()
        .delete_group(&group_id)
        .map_err(|e| e.to_string())
}

#[tauri::command]
async fn group_chat_history(
    state: State<'_, SharedState>,
    group_id: String,
    limit: u32,
) -> Result<Vec<serde_json::Value>, String> {
    let guard = state.read().await;
    let s = guard
        .as_ref()
        .ok_or_else(|| "node not started".to_string())?;
    let gid = format!("group:{group_id}");
    let messages = s
        .app_node
        .storage()
        .group_chat_history(&group_id, limit as usize)
        .map_err(|e| e.to_string())?;
    Ok(messages
        .into_iter()
        .map(|m| {
            json!({
                "id": m.id.to_string(),
                "from_peer": &gid,
                "from_display_name": m.from_display_name,
                "body": m.body,
                "timestamp_ms": m.timestamp_ms,
            })
        })
        .collect())
}

#[tauri::command]
async fn wallet_send(
    state: State<'_, SharedState>,
    to_address: String,
    amount_muon: u64,
    fee_muon: u64,
    memo: Option<String>,
) -> Result<(), String> {
    let guard = state.read().await;
    let s = guard
        .as_ref()
        .ok_or_else(|| "node not started".to_string())?;
    s.coin_node
        .submit_transfer_from_identity_path(
            &s.coin_identity_path,
            to_address,
            amount_muon,
            fee_muon,
            memo,
        )
        .await
        .map_err(|e| e.to_string())
}

#[tauri::command]
async fn miniapp_list_installed(
    state: State<'_, SharedState>,
) -> Result<Vec<serde_json::Value>, String> {
    let g = state.read().await;
    let s = g.as_ref().ok_or_else(|| "node not started".to_string())?;
    let apps = s.app_store.installed_apps().map_err(|e| e.to_string())?;
    let mut out = Vec::new();
    for m in apps {
        out.push(serde_json::to_value(&m).map_err(|e| e.to_string())?);
    }
    Ok(out)
}

#[tauri::command]
async fn miniapp_install(
    state: State<'_, SharedState>,
    bundle_base64: String,
) -> Result<serde_json::Value, String> {
    use base64::Engine;
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(bundle_base64.trim())
        .map_err(|e| e.to_string())?;
    let g = state.read().await;
    let s = g.as_ref().ok_or_else(|| "node not started".to_string())?;
    let m = s.app_store.install(&bytes).map_err(|e| e.to_string())?;
    serde_json::to_value(&m).map_err(|e| e.to_string())
}

#[tauri::command]
async fn miniapp_uninstall(state: State<'_, SharedState>, app_id: String) -> Result<(), String> {
    let g = state.read().await;
    let s = g.as_ref().ok_or_else(|| "node not started".to_string())?;
    s.app_store.uninstall(&app_id).map_err(|e| e.to_string())
}

#[tauri::command]
async fn miniapp_browse_store(
    state: State<'_, SharedState>,
) -> Result<Vec<serde_json::Value>, String> {
    let g = state.read().await;
    let s = g.as_ref().ok_or_else(|| "node not started".to_string())?;
    let rows = s.app_store.browse_listings().map_err(|e| e.to_string())?;
    let mut out = Vec::new();
    for l in rows {
        out.push(serde_json::to_value(&l).map_err(|e| e.to_string())?);
    }
    Ok(out)
}

#[tauri::command]
async fn miniapp_get_entry_html(
    state: State<'_, SharedState>,
    app_id: String,
) -> Result<String, String> {
    let g = state.read().await;
    let s = g.as_ref().ok_or_else(|| "node not started".to_string())?;
    let m = s
        .app_store
        .get_manifest(&app_id)
        .map_err(|e| e.to_string())?
        .ok_or_else(|| "unknown app".to_string())?;
    let bytes = s
        .app_store
        .get_file(&app_id, &m.entry)
        .map_err(|e| e.to_string())?
        .ok_or_else(|| "entry missing".to_string())?;
    let body = String::from_utf8(bytes).map_err(|e| e.to_string())?;
    let bridge = mycelium_app::miniapp::bridge_api_js_source();
    let shim = r#"<script>
window.addEventListener("message", function (ev) {
  if (ev.data && ev.data.__mycelium_resolve && window.__mycelium_resolve) {
    var r = ev.data.__mycelium_resolve;
    window.__mycelium_resolve(r.id, r.result, r.error);
  }
});
window.__mycelium_native_call = function (json) {
  window.parent.postMessage({ __mycelium_call: json }, "*");
};
</script><script>
"#;
    Ok(format!("{shim}{bridge}</script>\n{body}"))
}

#[tauri::command]
async fn miniapp_bridge_call(
    state: State<'_, SharedState>,
    app_id: String,
    method: String,
    args: serde_json::Value,
) -> Result<serde_json::Value, String> {
    let g = state.read().await;
    let s = g.as_ref().ok_or_else(|| "node not started".to_string())?;
    match method.as_str() {
        "identity.get" => Ok(json!({
            "peer_id": s.local_peer_id,
            "display_name": s.app_node.display_name().await,
            "enc_pubkey": s.handle.local_enc_pubkey_hex(),
        })),
        "app.get_id" => Ok(json!(app_id)),
        "app.get_version" => Ok(json!(env!("CARGO_PKG_VERSION"))),
        "util.now" => Ok(json!(mycelium_core::data::now_ms())),
        "messaging.send" => {
            let to = args
                .get("to_peer")
                .and_then(|x| x.as_str())
                .ok_or_else(|| "to_peer required".to_string())?;
            let payload = args
                .get("payload")
                .and_then(|x| x.as_str())
                .ok_or_else(|| "payload required".to_string())?;
            s.app_node
                .send_chat_encrypted(to.to_string(), payload.to_string())
                .await
                .map_err(|e| e.to_string())?;
            Ok(json!("ok"))
        }
        "storage.get" => {
            let key = args
                .get("key")
                .and_then(|x| x.as_str())
                .ok_or_else(|| "key required".to_string())?;
            let v = s
                .app_store
                .miniapp_storage_get(&app_id, key)
                .map_err(|e| e.to_string())?;
            Ok(json!(v))
        }
        "storage.set" => {
            let key = args
                .get("key")
                .and_then(|x| x.as_str())
                .ok_or_else(|| "key required".to_string())?;
            let value = args
                .get("value")
                .and_then(|x| x.as_str())
                .ok_or_else(|| "value required".to_string())?;
            s.app_store
                .miniapp_storage_set(&app_id, key, value)
                .map_err(|e| e.to_string())?;
            Ok(json!("ok"))
        }
        "storage.delete" => {
            let key = args
                .get("key")
                .and_then(|x| x.as_str())
                .ok_or_else(|| "key required".to_string())?;
            s.app_store
                .miniapp_storage_delete(&app_id, key)
                .map_err(|e| e.to_string())?;
            Ok(json!("ok"))
        }
        "bulletin.get" => {
            let scope = args
                .get("scope")
                .and_then(|x| x.as_str())
                .ok_or_else(|| "scope required".to_string())?;
            let list = s
                .app_node
                .bulletins_for_scope(scope)
                .map_err(|e| e.to_string())?;
            serde_json::to_value(&list).map_err(|e| e.to_string())
        }
        _ => Err(format!("unsupported method: {method}")),
    }
}

fn setup_event_emitter(app_handle: AppHandle, state: SharedState) {
    tauri::async_runtime::spawn(async move {
        loop {
            tokio::time::sleep(std::time::Duration::from_millis(500)).await;
            let guard = state.read().await;
            let Some(s) = guard.as_ref() else {
                continue;
            };
            let metrics = s.handle.metrics().await;
            let peers = s.handle.known_peers().await.len();
            let _ = app_handle.emit(
                "metrics-updated",
                json!({
                    "forwarded": metrics.messages_forwarded,
                    "queue": metrics.pending_queue_size,
                    "peers": peers,
                }),
            );
        }
    });
}

pub fn run() {
    let shared_state: SharedState = Arc::new(RwLock::new(None));

    tauri::Builder::default()
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_dialog::init())
        .manage(shared_state.clone())
        .setup(move |app| {
            setup_event_emitter(app.handle().clone(), shared_state.clone());
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            get_default_db_path,
            start_node,
            get_peers,
            get_metrics,
            send_chat,
            peer_has_enc_key,
            send_chat_encrypted,
            chat_history,
            send_mail,
            mail_inbox,
            wallet_balance,
            wallet_address,
            wallet_send,
            post_bulletin,
            bulletins_for_scope,
            add_peer,
            list_groups,
            create_group,
            import_group_invite,
            export_group_invite,
            send_group_message,
            delete_group,
            group_chat_history,
            miniapp_list_installed,
            miniapp_install,
            miniapp_uninstall,
            miniapp_browse_store,
            miniapp_get_entry_html,
            miniapp_bridge_call,
        ])
        .run(tauri::generate_context!())
        .expect("error running tauri application");
}
