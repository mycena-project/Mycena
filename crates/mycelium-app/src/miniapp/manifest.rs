use serde::{Deserialize, Serialize};

/// Each mini-app ships a manifest as `mycelium-app.json` at the bundle root.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct MiniAppManifest {
    /// Reverse-domain id, e.g. `com.example.delivery`
    pub id: String,
    pub name: String,
    pub description: String,
    pub version: String,
    pub developer: String,
    pub developer_peer_id: Option<String>,
    pub entry: String,
    pub icon_base64: Option<String>,
    pub permissions: Vec<Permission>,
    pub min_mycelium_version: String,
    pub accepts_payments: bool,
    pub payment_address: Option<String>,
    pub categories: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, Hash)]
#[serde(rename_all = "PascalCase")]
pub enum Permission {
    Messaging,
    Identity,
    Payments,
    Storage,
    BulletinRead,
    BulletinWrite,
    PeerDiscovery,
    Camera,
}

impl MiniAppManifest {
    pub fn validate(&self) -> anyhow::Result<()> {
        if self.id.is_empty() || !self.id.contains('.') {
            anyhow::bail!("app id must be reverse-domain format (e.g. com.example.app)");
        }
        if self.name.is_empty() {
            anyhow::bail!("name is required");
        }
        if self.description.chars().count() > 120 {
            anyhow::bail!("description must be ≤ 120 characters");
        }
        if self.entry.is_empty() {
            anyhow::bail!("entry point is required");
        }
        Ok(())
    }
}
