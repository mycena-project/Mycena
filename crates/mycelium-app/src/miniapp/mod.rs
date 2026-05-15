//! Mini-app platform: manifests, `.mxa` bundles, local store, and JS bridge source.

pub mod bridge_host;
pub mod bundle;
pub mod manifest;
pub mod store;

pub use bundle::MiniAppBundle;
pub use manifest::{MiniAppManifest, Permission};
pub use store::{listing_from_manifest_and_bundle, AppSource, AppStore, AppStoreListing};

/// JavaScript bridge injected into the mini-app host (`window.mycelium`).
pub fn bridge_api_js_source() -> &'static str {
    include_str!("bridge_api.js")
}
