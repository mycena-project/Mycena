# Rust workspace: `cargo test` + `cargo clippy -D warnings`.
# Repo is bind-mounted at run time (see docker-compose.yml).
#
# Build context should be this directory only (`docker/rust-tests/`) — no COPY of
# the workspace; avoids multi-GB Docker build context uploads from repo root.

FROM rust:1.83-bookworm

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        pkg-config \
        libssl-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# Standard: nichts ausführen — Compose setzt `command`.
CMD ["sh", "-c", "cargo test --workspace && cargo clippy --workspace --all-targets -- -D warnings"]
