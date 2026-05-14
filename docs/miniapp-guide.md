# Mycelium mini-app platform

Mini-apps are offline-first HTML bundles shipped as **`.mxa`** (ZIP) archives. The host loads the manifest (`mycelium-app.json`), extracts files into a local **sled** database, injects the **JavaScript bridge** before the entry HTML, and exposes a small native API for identity, mesh messaging, bulletin reads, and per-app storage.

## Bundle layout

- **`mycelium-app.json`** — manifest (see `mycelium_app::miniapp::MiniAppManifest` in Rust).
- **Entry HTML** — path given by `entry` in the manifest (often `index.html`).
- **Other assets** — any safe relative paths (no `..`, no absolute paths); the Android WebView resolves `https://miniapp.local/…` via `get_app_file`.

Maximum unpacked size on install is **10 MB** (enforced in `AppStore::install`).

## Bridge (`window.mycelium`)

The bridge script is `crates/mycelium-app/src/miniapp/bridge_api.js`. The host must define **`window.__mycelium_native_call(jsonString)`** and call **`window.__mycelium_resolve(id, result, error)`** when the async native work finishes.

Implemented methods today (subset; others return “unsupported” on some hosts):

| Method | Purpose |
|--------|---------|
| `identity.get` | Local peer id, display name, encryption pubkey hex. |
| `app.get_id` / `app.get_version` | Installed app id and manifest version. |
| `util.now` | Host monotonic wall clock ms. |
| `messaging.send` | `{ to_peer, payload }` → encrypted chat send. |
| `messaging.subscribe` | Ack only (no push pipe yet). |
| `storage.get` / `set` / `delete` / `list` | Per-app key/value in sled (`miniapp_kv:{app_id}`). |
| `bulletin.get` | `{ scope }` → JSON list of posts (snake_case fields). |

## Desktop (Tauri)

- Commands: `miniapp_list_installed`, `miniapp_install` (base64), `miniapp_uninstall`, `miniapp_browse_store`, `miniapp_get_entry_html`, `miniapp_bridge_call`.
- UI: **Mini apps** in the sidebar → store + iframe runner with `postMessage` to the shell.

## Android

- **Settings → Mini apps** opens the store screen; **Open** loads a `WebView` with the same bridge semantics via `@JavascriptInterface` (`MyceliumMiniapp`).
- **`bridge_api.js`** is copied into `app/src/main/assets/miniapp/` on **`preBuild`** from the Rust crate (see `app/build.gradle.kts`).
- UniFFI: `list_installed_apps`, `install_app`, `uninstall_app`, `get_app_file`, `browse_app_store`, `miniapp_storage_*`.

## App store listings

`AppStore::cache_listing` persists distributor metadata; `browse_listings` reads the local cache. Installing still requires the **bundle bytes** (file picker). Verifying signatures over listings is future work (`listing_from_manifest_and_bundle` currently hashes for a placeholder sig).

## Further reading

- Rust: `crates/mycelium-app/src/miniapp/`
- FFI: `crates/mycelium-ffi/src/mycelium.udl`, `browse_app_store`, `get_app_file`
