# Rust workspace: `cargo test` + `cargo clippy -D warnings`.
# Repo is bind-mounted at run time (see docker-compose.yml).
#
# Use **slim** base (much smaller pull than `rust:*-bookworm`) + GTK/WebKit dev
# packages so `mycelium-desktop` (Tauri) links on Linux. Build context is only
# this directory (see docker-compose.yml).

FROM rust:1.83-slim-bookworm

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        pkg-config \
        libssl-dev \
        libwebkit2gtk-4.1-dev \
        libayatana-appindicator3-dev \
        librsvg2-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

CMD ["sh", "-c", "cargo test --workspace && cargo clippy --workspace --all-targets -- -D warnings"]
