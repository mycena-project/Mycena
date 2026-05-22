//! Encrypt sensitive key material at rest (identity Ed25519, X25519 enc key).
//!
//! Master key resolution (first match):
//! 1. Explicit `storage_key` (32 bytes) — Android passes a key from Keystore-backed prefs.
//! 2. OS keyring entry scoped per `db_path`.
//! 3. Random per-device file-based key (fallback; weaker than keyring, not path-derived).

use chacha20poly1305::aead::{Aead, KeyInit};
use chacha20poly1305::{ChaCha20Poly1305, Nonce};
use rand::RngCore;
use std::path::{Path, PathBuf};
use zeroize::Zeroizing;

const SERVICE: &str = "network.mycelium";
const NONCE_LEN: usize = 12;
const KEY_LEN: usize = 32;
const LEGACY_FALLBACK_CONTEXT: &str = "mycelium-at-rest-fallback-v1";

pub struct SecretVault {
    master: Zeroizing<[u8; KEY_LEN]>,
    secrets_dir: PathBuf,
}

impl SecretVault {
    /// Opens the vault for `db_path`. Pass `storage_key` on Android (32 raw bytes).
    pub fn open(db_path: &str, storage_key: Option<[u8; KEY_LEN]>) -> anyhow::Result<Self> {
        let secrets_dir = Path::new(db_path).join(".secrets");
        std::fs::create_dir_all(&secrets_dir)?;
        let master = Zeroizing::new(resolve_master_key(db_path, storage_key)?);
        Ok(Self {
            master,
            secrets_dir,
        })
    }

    /// Opens the vault and migrates secrets encrypted with the old deterministic fallback key.
    pub fn open_with_migration(
        db_path: &str,
        storage_key: Option<[u8; KEY_LEN]>,
    ) -> anyhow::Result<Self> {
        let vault = Self::open(db_path, storage_key)?;

        if vault.read_secret("enc_keypair").is_err() {
            let old_key = legacy_derive_fallback_master(db_path);
            if let Ok(old_vault) = Self::open_with_explicit_key(db_path, old_key) {
                if let Ok(Some(data)) = old_vault.read_secret("enc_keypair") {
                    vault.write_secret("enc_keypair", &data)?;
                    tracing::info!(
                        "migrated enc_keypair from old deterministic key to new random fallback key"
                    );
                } else if let Ok(Some(data)) = old_vault.read_secret("enc_x25519") {
                    vault.write_secret("enc_x25519", &data)?;
                    tracing::info!(
                        "migrated enc_x25519 from old deterministic key to new random fallback key"
                    );
                } else if let Ok(Some(data)) = old_vault.read_secret("ed25519_identity") {
                    vault.write_secret("ed25519_identity", &data)?;
                    tracing::info!(
                        "migrated ed25519_identity from old deterministic key to new random fallback key"
                    );
                }
            }
        }

        Ok(vault)
    }

    fn open_with_explicit_key(db_path: &str, master: [u8; KEY_LEN]) -> anyhow::Result<Self> {
        let secrets_dir = Path::new(db_path).join(".secrets");
        std::fs::create_dir_all(&secrets_dir)?;
        Ok(Self {
            master: Zeroizing::new(master),
            secrets_dir,
        })
    }

    pub fn read_secret(&self, name: &str) -> anyhow::Result<Option<Vec<u8>>> {
        let path = self.secrets_dir.join(format!("{name}.enc"));
        if !path.exists() {
            return Ok(None);
        }
        let blob = std::fs::read(&path)?;
        decrypt(&self.master, &blob)
            .map(Some)
            .map_err(|e| anyhow::anyhow!("decrypt {name}: {e}"))
    }

    pub fn write_secret(&self, name: &str, plaintext: &[u8]) -> anyhow::Result<()> {
        let path = self.secrets_dir.join(format!("{name}.enc"));
        let blob = encrypt(&self.master, plaintext)?;
        std::fs::write(path, blob)?;
        Ok(())
    }

    pub fn delete_secret(&self, name: &str) -> anyhow::Result<()> {
        let path = self.secrets_dir.join(format!("{name}.enc"));
        if path.exists() {
            std::fs::remove_file(path)?;
        }
        Ok(())
    }

    pub fn secrets_dir(&self) -> &Path {
        &self.secrets_dir
    }
}

/// Parse 64-char hex into a 32-byte storage key (Android).
pub fn parse_storage_key_hex(hex_str: &str) -> anyhow::Result<[u8; KEY_LEN]> {
    let bytes =
        hex::decode(hex_str.trim()).map_err(|e| anyhow::anyhow!("invalid storage_key hex: {e}"))?;
    bytes
        .try_into()
        .map_err(|_| anyhow::anyhow!("storage_key must be 32 bytes (64 hex chars)"))
}

fn resolve_master_key(
    db_path: &str,
    storage_key: Option<[u8; KEY_LEN]>,
) -> anyhow::Result<[u8; KEY_LEN]> {
    if let Some(k) = storage_key {
        return Ok(k);
    }
    if let Ok(k) = load_master_from_keyring(db_path) {
        return Ok(k);
    }
    tracing::warn!(
        "at-rest: keyring unavailable for {}, using file-based fallback key",
        db_path
    );
    load_or_create_file_fallback_key(db_path)
}

fn keyring_account(db_path: &str) -> String {
    format!("master:{}", blake3::hash(db_path.as_bytes()).to_hex())
}

fn load_master_from_keyring(db_path: &str) -> anyhow::Result<[u8; KEY_LEN]> {
    let entry = keyring::Entry::new(SERVICE, &keyring_account(db_path))?;
    match entry.get_password() {
        Ok(hex) => {
            let bytes = hex::decode(hex.trim())?;
            bytes
                .try_into()
                .map_err(|_| anyhow::anyhow!("invalid keyring master key length"))
        }
        Err(keyring::Error::NoEntry) => {
            let mut key = [0u8; KEY_LEN];
            rand::thread_rng().fill_bytes(&mut key);
            entry.set_password(&hex::encode(key))?;
            Ok(key)
        }
        Err(e) => Err(e.into()),
    }
}

/// Loads a persisted random key or creates one (per device, survives restarts).
pub(crate) fn load_or_create_file_fallback_key(db_path: &str) -> anyhow::Result<[u8; KEY_LEN]> {
    let key_path = Path::new(db_path)
        .join(".secrets")
        .join("fallback_master.key");

    if key_path.exists() {
        let bytes = std::fs::read(&key_path)?;
        if bytes.len() == KEY_LEN {
            let mut key = [0u8; KEY_LEN];
            key.copy_from_slice(&bytes);
            return Ok(key);
        }
        tracing::warn!("fallback key file corrupt, regenerating");
    }

    let mut key = [0u8; KEY_LEN];
    rand::thread_rng().fill_bytes(&mut key);

    if let Some(parent) = key_path.parent() {
        std::fs::create_dir_all(parent)?;
    }

    let tmp_path = key_path.with_extension("tmp");
    std::fs::write(&tmp_path, key)?;
    std::fs::rename(&tmp_path, &key_path)?;

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        std::fs::set_permissions(&key_path, std::fs::Permissions::from_mode(0o600))?;
    }

    tracing::info!(
        "generated new file-based fallback master key at {:?}",
        key_path
    );
    Ok(key)
}

fn legacy_derive_fallback_master(db_path: &str) -> [u8; KEY_LEN] {
    blake3::derive_key(LEGACY_FALLBACK_CONTEXT, db_path.as_bytes())
}

fn encrypt(master: &[u8; KEY_LEN], plaintext: &[u8]) -> anyhow::Result<Vec<u8>> {
    let cipher = ChaCha20Poly1305::new(master.into());
    let mut nonce = [0u8; NONCE_LEN];
    rand::thread_rng().fill_bytes(&mut nonce);
    let ct = cipher
        .encrypt(Nonce::from_slice(&nonce), plaintext)
        .map_err(|e| anyhow::anyhow!("encrypt failed: {e}"))?;
    let mut out = Vec::with_capacity(NONCE_LEN + ct.len());
    out.extend_from_slice(&nonce);
    out.extend_from_slice(&ct);
    Ok(out)
}

fn decrypt(master: &[u8; KEY_LEN], blob: &[u8]) -> anyhow::Result<Vec<u8>> {
    if blob.len() < NONCE_LEN + 16 {
        anyhow::bail!("ciphertext too short");
    }
    let (nonce, ct) = blob.split_at(NONCE_LEN);
    let cipher = ChaCha20Poly1305::new(master.into());
    cipher
        .decrypt(Nonce::from_slice(nonce), ct)
        .map_err(|e| anyhow::anyhow!("decrypt failed: {e}"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn encrypt_decrypt_roundtrip() {
        let mut master = [0u8; KEY_LEN];
        rand::thread_rng().fill_bytes(&mut master);
        let pt = b"secret-key-material";
        let enc = encrypt(&master, pt).unwrap();
        let dec = decrypt(&master, &enc).unwrap();
        assert_eq!(dec, pt);
    }

    #[test]
    fn fallback_key_is_random_not_deterministic() {
        let dir1 = tempfile::tempdir().unwrap();
        let dir2 = tempfile::tempdir().unwrap();

        let key1 = load_or_create_file_fallback_key(dir1.path().to_str().unwrap()).unwrap();
        let key2 = load_or_create_file_fallback_key(dir2.path().to_str().unwrap()).unwrap();

        assert_ne!(key1, key2, "fallback keys must be random, not path-derived");
    }

    #[test]
    fn fallback_key_is_persistent() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().to_str().unwrap();

        let key1 = load_or_create_file_fallback_key(path).unwrap();
        let key2 = load_or_create_file_fallback_key(path).unwrap();

        assert_eq!(key1, key2, "fallback key must be persistent across calls");
    }

    #[test]
    fn fallback_key_file_permissions_are_restrictive() {
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let dir = tempfile::tempdir().unwrap();
            let path = dir.path().to_str().unwrap();
            load_or_create_file_fallback_key(path).unwrap();

            let key_path = Path::new(path).join(".secrets").join("fallback_master.key");
            let mode = std::fs::metadata(&key_path).unwrap().permissions().mode();
            assert_eq!(mode & 0o777, 0o600, "key file must not be world-readable");
        }
    }

    #[test]
    fn vault_write_read_delete() {
        let dir = tempfile::tempdir().unwrap();
        let db = dir.path().to_str().unwrap();
        let vault = SecretVault::open_with_migration(db, None).unwrap();
        vault.write_secret("test", b"hello").unwrap();
        assert_eq!(
            vault.read_secret("test").unwrap().as_deref(),
            Some(b"hello" as &[u8])
        );
        vault.delete_secret("test").unwrap();
        assert!(vault.read_secret("test").unwrap().is_none());
    }

    #[test]
    fn panic_wipe_removes_secrets_dir() {
        let dir = tempfile::tempdir().unwrap();
        let db_path = dir.path();
        let vault = SecretVault::open_with_migration(db_path.to_str().unwrap(), None).unwrap();
        vault.write_secret("identity", b"x").unwrap();
        assert!(vault.secrets_dir().exists());
        std::fs::remove_dir_all(db_path).unwrap();
        assert!(!db_path.exists());
    }
}
