# Mycelium patch — libp2p-gossipsub 0.49.4

Vendored from crates.io with runtime mesh hot-swap APIs for AR-06 density backoff:

- `Config::set_default_mesh_params`
- `Config::set_topic_mesh_params`
- `Behaviour::set_default_mesh_params`
- `Behaviour::set_topic_mesh_params`
- Re-export `TopicMeshConfig`

Wired via `[patch.crates-io]` in the workspace `Cargo.toml`. Upstream PR candidate for rust-libp2p.
