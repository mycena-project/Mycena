//! Central bridge dispatcher for mini-app `window.mycelium.*` calls (shared by hosts).

use std::sync::Arc;

use mycelium_coin::{CoinNode, PaymentRequest};
use serde_json::{json, Value};

use crate::miniapp::manifest::Permission;
use crate::miniapp::AppStore;
use crate::node::AppNode;
use crate::storage::AppStorage;

pub struct BridgeHost {
    app_id: String,
    permissions: Vec<Permission>,
    app_node: Arc<AppNode>,
    app_storage: Arc<AppStorage>,
    app_store: Arc<AppStore>,
    coin: Option<Arc<CoinNode>>,
    local_peer_id: String,
    enc_pubkey_hex: String,
}

impl BridgeHost {
    #[allow(clippy::too_many_arguments)]
    pub fn new(
        app_id: String,
        permissions: Vec<Permission>,
        app_node: Arc<AppNode>,
        app_storage: Arc<AppStorage>,
        app_store: Arc<AppStore>,
        coin: Option<Arc<CoinNode>>,
        local_peer_id: String,
        enc_pubkey_hex: String,
    ) -> Self {
        Self {
            app_id,
            permissions,
            app_node,
            app_storage,
            app_store,
            coin,
            local_peer_id,
            enc_pubkey_hex,
        }
    }

    fn scoped_key(&self, key: &str) -> String {
        format!("app:{}:{key}", self.app_id)
    }

    fn scoped_prefix(&self, prefix: &str) -> String {
        format!("app:{}:{prefix}", self.app_id)
    }

    fn require_permission(&self, perm: Permission) -> Result<(), String> {
        if self.permissions.contains(&perm) {
            Ok(())
        } else {
            Err(format!("permission denied: {perm:?}"))
        }
    }

    pub async fn handle(&self, method: &str, args: &Value) -> Result<Value, String> {
        match method {
            "identity.get" => Ok(json!({
                "peer_id": self.local_peer_id,
                "display_name": self.app_node.display_name().await,
                "enc_pubkey": self.enc_pubkey_hex,
            })),

            "messaging.send" => {
                self.require_permission(Permission::Messaging)?;
                let to = args
                    .get("to_peer")
                    .and_then(|x| x.as_str())
                    .ok_or_else(|| "missing to_peer".to_string())?;
                let payload = args
                    .get("payload")
                    .and_then(|x| x.as_str())
                    .ok_or_else(|| "missing payload".to_string())?;
                let body = format!("[app/{}] {}", self.app_id, payload);
                self.app_node
                    .send_chat(Some(to.to_string()), body)
                    .await
                    .map_err(|e| e.to_string())?;
                Ok(json!("ok"))
            }

            "messaging.subscribe" => {
                self.require_permission(Permission::Messaging)?;
                Ok(json!("subscribed"))
            }

            "messaging.broadcast" => {
                self.require_permission(Permission::Messaging)?;
                let payload = args
                    .get("payload")
                    .and_then(|x| x.as_str())
                    .ok_or_else(|| "missing payload".to_string())?;
                let _scope = args
                    .get("scope")
                    .and_then(|s| s.as_str())
                    .unwrap_or("mycelium/chat");
                let body = format!("[app/{}] {}", self.app_id, payload);
                self.app_node
                    .send_chat(None, body)
                    .await
                    .map_err(|e| e.to_string())?;
                Ok(json!("ok"))
            }

            "payment.request" => {
                self.require_permission(Permission::Payments)?;
                let amount = json_u64(args, "amount_muon")?;
                let memo = args.get("memo").and_then(|m| m.as_str()).map(String::from);
                Ok(json!({
                    "_bridge_action": "payment_confirmation_required",
                    "amount_muon": amount,
                    "memo": memo,
                }))
            }

            "payment.create_qr" => {
                self.require_permission(Permission::Payments)?;
                let coin = self
                    .coin
                    .as_ref()
                    .ok_or_else(|| "coin not available".to_string())?;
                let amount = json_u64(args, "amount_muon")?;
                let memo = args.get("memo").and_then(|m| m.as_str()).map(String::from);
                let address = coin.local_address().to_string();
                let req = PaymentRequest::new(address, amount, memo);
                let uri = req.to_uri();
                Ok(json!({
                    "uri": uri,
                    "_bridge_action": "render_qr",
                    "_qr_content": uri,
                }))
            }

            "payment.get_balance" => {
                self.require_permission(Permission::Payments)?;
                let coin = self
                    .coin
                    .as_ref()
                    .ok_or_else(|| "coin not available".to_string())?;
                let (confirmed, pending) = coin.balance().map_err(|e| e.to_string())?;
                Ok(json!({
                    "confirmed_muon": confirmed,
                    "pending_muon": pending,
                }))
            }

            "storage.get" => {
                self.require_permission(Permission::Storage)?;
                let key = args
                    .get("key")
                    .and_then(|x| x.as_str())
                    .ok_or_else(|| "missing key".to_string())?;
                let sk = self.scoped_key(key);
                let v = self
                    .app_storage
                    .miniapp_get(&sk)
                    .map_err(|e| e.to_string())?;
                Ok(v.map(|s| json!(s)).unwrap_or(Value::Null))
            }

            "storage.set" => {
                self.require_permission(Permission::Storage)?;
                let key = args
                    .get("key")
                    .and_then(|x| x.as_str())
                    .ok_or_else(|| "missing key".to_string())?;
                let value = args
                    .get("value")
                    .and_then(|x| x.as_str())
                    .ok_or_else(|| "missing value".to_string())?;
                let sk = self.scoped_key(key);
                self.app_storage
                    .miniapp_set(&sk, value)
                    .map_err(|e| e.to_string())?;
                Ok(json!("ok"))
            }

            "storage.delete" => {
                self.require_permission(Permission::Storage)?;
                let key = args
                    .get("key")
                    .and_then(|x| x.as_str())
                    .ok_or_else(|| "missing key".to_string())?;
                let sk = self.scoped_key(key);
                self.app_storage
                    .miniapp_delete(&sk)
                    .map_err(|e| e.to_string())?;
                Ok(json!("ok"))
            }

            "storage.list" => {
                self.require_permission(Permission::Storage)?;
                let prefix = args.get("prefix").and_then(|p| p.as_str()).unwrap_or("");
                let pfx = self.scoped_prefix(prefix);
                let keys = self
                    .app_storage
                    .miniapp_list(&pfx)
                    .map_err(|e| e.to_string())?;
                let strip = format!("app:{}:", self.app_id);
                let out: Vec<String> = keys
                    .into_iter()
                    .filter_map(|k| k.strip_prefix(&strip).map(str::to_string))
                    .collect();
                Ok(json!(out))
            }

            "bulletin.get" => {
                self.require_permission(Permission::BulletinRead)?;
                let scope = args
                    .get("scope")
                    .and_then(|x| x.as_str())
                    .ok_or_else(|| "missing scope".to_string())?;
                let posts = self
                    .app_node
                    .bulletins_for_scope(scope)
                    .map_err(|e| e.to_string())?;
                let list: Vec<Value> = posts
                    .iter()
                    .map(|p| {
                        json!({
                            "id": p.id.to_string(),
                            "title": p.title,
                            "body": p.body,
                            "from_display_name": p.from_display_name,
                            "scope": p.scope,
                            "timestamp_ms": p.timestamp_ms,
                            "expires_at_ms": p.expires_at_ms,
                        })
                    })
                    .collect();
                Ok(Value::Array(list))
            }

            "bulletin.post" => {
                self.require_permission(Permission::BulletinWrite)?;
                let scope = args
                    .get("scope")
                    .and_then(|x| x.as_str())
                    .ok_or_else(|| "missing scope".to_string())?;
                let title = args
                    .get("title")
                    .and_then(|x| x.as_str())
                    .ok_or_else(|| "missing title".to_string())?;
                let body = args
                    .get("body")
                    .and_then(|x| x.as_str())
                    .ok_or_else(|| "missing body".to_string())?;
                let ttl = args.get("ttl_secs").and_then(json_as_u64).unwrap_or(86_400);
                self.app_node
                    .post_bulletin(scope.into(), title.into(), body.into(), ttl)
                    .await
                    .map_err(|e| e.to_string())?;
                Ok(json!("ok"))
            }

            "peers.nearby" => {
                self.require_permission(Permission::PeerDiscovery)?;
                let peers = self.app_node.node_handle().known_peers().await;
                Ok(json!(peers))
            }

            "util.now" => Ok(json!(mycelium_core::data::now_ms())),

            "util.scan_qr" => Ok(json!({ "_bridge_action": "open_qr_scanner" })),

            "util.alert" => {
                let message = args
                    .get("message")
                    .and_then(|x| x.as_str())
                    .ok_or_else(|| "missing message".to_string())?;
                Ok(json!({
                    "_bridge_action": "show_alert",
                    "message": message,
                }))
            }

            "util.confirm" => {
                let message = args
                    .get("message")
                    .and_then(|x| x.as_str())
                    .ok_or_else(|| "missing message".to_string())?;
                Ok(json!({
                    "_bridge_action": "show_confirm",
                    "message": message,
                }))
            }

            "app.get_id" => Ok(json!(self.app_id)),

            "app.get_version" => {
                let v = self
                    .app_store
                    .get_manifest(&self.app_id)
                    .map_err(|e| e.to_string())?
                    .map(|m| m.version)
                    .unwrap_or_default();
                Ok(json!(v))
            }

            _ => Err(format!("unknown bridge method: {method}")),
        }
    }
}

fn json_u64(args: &Value, key: &str) -> Result<u64, String> {
    args.get(key)
        .and_then(json_as_u64)
        .ok_or_else(|| format!("missing or invalid {key}"))
}

fn json_as_u64(v: &Value) -> Option<u64> {
    if let Some(n) = v.as_u64() {
        return Some(n);
    }
    if let Some(n) = v.as_i64() {
        return u64::try_from(n).ok();
    }
    None
}
