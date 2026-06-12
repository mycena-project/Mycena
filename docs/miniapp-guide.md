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
| `messaging.send` | `{ to_peer, payload }` → encrypted chat send (requires **accepted contact**). |
| `messaging.subscribe` | Registers `onMessage(handler)`; host pushes `[app/{id}]`-prefixed chat to the handler when a message arrives. |
| `mycelium.registerBulletinRefresh(fn)` | Called when a bulletin post arrives on a subscribed scope (host-triggered refresh). |
| `storage.get` / `set` / `delete` / `list` | Per-app key/value in `AppStorage` tree `miniapp_storage` (`app:{app_id}:{key}`). |
| `bulletin.get` | `{ scope }` → JSON list of posts (snake_case fields). Auto-subscribes the node to the scope. |

**Safe mode** (user- or policy-enforced) blocks writes including `proximity.start`, `bulletin.post`, and `messaging.send`. Reads such as `bulletin.get`, `storage.get`, and bulletin refresh hooks still work.

**Push delivery:** Bulletin posts trigger `registerBulletinRefresh` on Android and desktop. App-scoped chat (`[app/{manifest.id}] …` prefix from `messaging.send`) is pushed to `onMessage` handlers when the host receives encrypted chat from an accepted contact.

## Desktop (Tauri)

- Commands: `miniapp_list_installed`, `miniapp_install` (base64), `miniapp_uninstall`, `miniapp_browse_store`, `miniapp_get_entry_html`, `miniapp_bridge_call`, `miniapp_get_policy`, `miniapp_set_safe_mode`.

Developer CLI (`miniapp-sdk`):

```bash
miniapp-sdk lint ./app.mxa
miniapp-sdk hash ./app.mxa
miniapp-sdk attest ./app.mxa --recipe RECIPE.md   # → reproducible_build JSON (content hash, stable after merge)
```

Install preview runs **bundle scan** (H24): allowed file types, no `javascript:` in HTML, zip-bomb ratio, revoked app ids.

Curator nodes can publish revocations on gossip (`miniapp_publish_revocation` / desktop store UI).

Runtime tier 2–3 APIs require **`_cap`** (HMAC capability token from host after user grant) in addition to manifest permissions.

- UI: **Mini apps** in the sidebar → store + iframe runner with `postMessage` to the shell.

## Android

- **Settings → Mini apps** opens the store screen; **Open** loads a `WebView` with the same bridge semantics via `@JavascriptInterface` (`MyceliumMiniapp`).
- **`bridge_api.js`** is copied into `app/src/main/assets/miniapp/` on **`preBuild`** from the Rust crate (see `app/build.gradle.kts`).
- UniFFI: `list_installed_apps`, `install_app`, `uninstall_app`, `get_app_file`, `browse_app_store`, `miniapp_storage_*`.

## App store listings

`AppStore::cache_listing` persists distributor metadata; `browse_listings` reads the local cache. Installing still requires the **bundle bytes** (file picker).

- **Gossip ingest:** unsigned or invalid listings are rejected (`node.rs` → `verify_signature()`).
- **Install path:** file/sideload install does **not** yet verify listing signature or `bundle_hash` — see [MINIAPP_RUNTIME_HARDENING.md](./MINIAPP_RUNTIME_HARDENING.md) (H01).

## Security (zero-trust target)

- [MINIAPP_SECURITY_ARCHITECTURE.md](./MINIAPP_SECURITY_ARCHITECTURE.md)
- [MINIAPP_PERMISSION_MODEL.md](./MINIAPP_PERMISSION_MODEL.md)
- [MINIAPP_ATTACK_SCENARIOS.md](./MINIAPP_ATTACK_SCENARIOS.md)
- [MINIAPP_RUNTIME_HARDENING.md](./MINIAPP_RUNTIME_HARDENING.md)

## Further reading

- Rust: `crates/mycelium-app/src/miniapp/`
- FFI: `crates/mycelium-ffi/src/mycelium.udl`, `browse_app_store`, `get_app_file`
