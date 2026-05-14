use mycelium_core::data::now_ms;
use serde::{Deserialize, Serialize};

use super::manifest::MiniAppManifest;

/// App store listing distributed via bulletin (scope `mycelium/appstore/v1`).
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct AppStoreListing {
    pub manifest: MiniAppManifest,
    pub bundle_hash: String,
    pub sources: Vec<AppSource>,
    pub published_at_ms: u64,
    pub updated_at_ms: u64,
    pub signature: Vec<u8>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum AppSource {
    Peer(String),
    Url(String),
}

pub struct AppStore {
    db: sled::Db,
    installed: sled::Tree,
    listings: sled::Tree,
}

impl AppStore {
    pub fn open(db_path: &str) -> anyhow::Result<Self> {
        let db = sled::Config::new()
            .path(db_path)
            .flush_every_ms(Some(5000))
            .open()?;
        let installed = db.open_tree("installed_apps")?;
        let listings = db.open_tree("app_listings")?;
        Ok(Self {
            db,
            installed,
            listings,
        })
    }

    pub fn install(&self, bundle_bytes: &[u8]) -> anyhow::Result<MiniAppManifest> {
        let bundle = super::bundle::MiniAppBundle::load_from_bytes(bundle_bytes)?;
        if bundle.total_size() > 10 * 1024 * 1024 {
            anyhow::bail!("app bundle exceeds 10 MB limit");
        }

        let manifest = bundle.manifest.clone();
        let key = manifest.id.as_bytes();
        self.installed
            .insert(key, serde_json::to_vec(&manifest)?)?;

        let files_tree = self.db.open_tree(format!("app_files:{}", manifest.id))?;
        for (path, data) in &bundle.files {
            files_tree.insert(path.as_bytes(), data.as_slice())?;
        }

        tracing::info!("installed mini-app: {} v{}", manifest.name, manifest.version);
        Ok(manifest)
    }

    pub fn uninstall(&self, app_id: &str) -> anyhow::Result<()> {
        self.installed.remove(app_id.as_bytes())?;
        let tree_name = format!("app_files:{app_id}");
        let _ = self.db.drop_tree(tree_name.as_bytes());
        let kv_name = format!("miniapp_kv:{app_id}");
        let _ = self.db.drop_tree(kv_name.as_bytes());
        Ok(())
    }

    pub fn installed_apps(&self) -> anyhow::Result<Vec<MiniAppManifest>> {
        let mut apps = Vec::new();
        for item in self.installed.iter() {
            let (_, v) = item?;
            if let Ok(m) = serde_json::from_slice::<MiniAppManifest>(&v) {
                apps.push(m);
            }
        }
        Ok(apps)
    }

    pub fn get_file(&self, app_id: &str, path: &str) -> anyhow::Result<Option<Vec<u8>>> {
        let tree = self.db.open_tree(format!("app_files:{app_id}"))?;
        Ok(tree.get(path.as_bytes())?.map(|v| v.to_vec()))
    }

    pub fn get_manifest(&self, app_id: &str) -> anyhow::Result<Option<MiniAppManifest>> {
        match self.installed.get(app_id.as_bytes())? {
            Some(v) => Ok(Some(serde_json::from_slice(&v)?)),
            None => Ok(None),
        }
    }

    pub fn cache_listing(&self, listing: &AppStoreListing) -> anyhow::Result<()> {
        let key = listing.manifest.id.as_bytes();
        self.listings
            .insert(key, serde_json::to_vec(listing)?)?;
        Ok(())
    }

    pub fn browse_listings(&self) -> anyhow::Result<Vec<AppStoreListing>> {
        let mut listings = Vec::new();
        for item in self.listings.iter() {
            let (_, v) = item?;
            if let Ok(l) = serde_json::from_slice::<AppStoreListing>(&v) {
                listings.push(l);
            }
        }
        listings.sort_by(|a, b| b.updated_at_ms.cmp(&a.updated_at_ms));
        Ok(listings)
    }

    pub fn miniapp_storage_get(&self, app_id: &str, key: &str) -> anyhow::Result<Option<String>> {
        let tree = self.db.open_tree(format!("miniapp_kv:{app_id}"))?;
        Ok(tree
            .get(key.as_bytes())?
            .map(|v| String::from_utf8_lossy(&v).into_owned()))
    }

    pub fn miniapp_storage_set(&self, app_id: &str, key: &str, value: &str) -> anyhow::Result<()> {
        let tree = self.db.open_tree(format!("miniapp_kv:{app_id}"))?;
        tree.insert(key.as_bytes(), value.as_bytes())?;
        Ok(())
    }

    pub fn miniapp_storage_delete(&self, app_id: &str, key: &str) -> anyhow::Result<()> {
        let tree = self.db.open_tree(format!("miniapp_kv:{app_id}"))?;
        tree.remove(key.as_bytes())?;
        Ok(())
    }

    pub fn miniapp_storage_list_keys(&self, app_id: &str, prefix: &str) -> anyhow::Result<Vec<String>> {
        let tree = self.db.open_tree(format!("miniapp_kv:{app_id}"))?;
        let mut out = Vec::new();
        for item in tree.iter() {
            let (k, _) = item?;
            let ks = String::from_utf8_lossy(&k);
            if ks.starts_with(prefix) {
                out.push(ks.into_owned());
            }
        }
        out.sort();
        Ok(out)
    }
}

/// Build a signed listing placeholder (signature verification is future work).
pub fn listing_from_manifest_and_bundle(
    manifest: MiniAppManifest,
    bundle_bytes: &[u8],
    sources: Vec<AppSource>,
) -> AppStoreListing {
    let bundle_hash = blake3::hash(bundle_bytes).to_hex().to_string();
    let now = now_ms();
    let mut body = Vec::new();
    body.extend_from_slice(bundle_hash.as_bytes());
    body.extend_from_slice(manifest.id.as_bytes());
    let sig = blake3::hash(&body).as_bytes().to_vec();
    AppStoreListing {
        manifest,
        bundle_hash,
        sources,
        published_at_ms: now,
        updated_at_ms: now,
        signature: sig,
    }
}
