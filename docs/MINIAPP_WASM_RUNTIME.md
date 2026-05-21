# Mini-app WASM runtime (H22 / P3)

**Status:** Install-time gate only — **no WASM executor** in this release.

## Manifest

```json
{
  "runtime": "webview"
}
```

| Value | Behavior |
|-------|----------|
| `webview` (default) | HTML/JS in isolated WebView (Android `:miniapp` process) |
| `wasm` | **Rejected** at `MiniAppBundle::load` / `miniapp-sdk lint` |

## Future executor (not implemented)

When added, the host will:

1. Load `guest.wasm` from the bundle with Wasmtime (or equivalent)
2. Expose a narrow WASI surface (no network, no filesystem except pre-opened dirs)
3. Keep payment/identity/messaging on the existing Rust bridge

Until then, do not set `"runtime": "wasm"` in published bundles.

## SDK

```bash
miniapp-sdk lint ./app.mxa   # fails if runtime is wasm
```
