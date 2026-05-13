# Rust workspace: `cargo test` + `cargo clippy -D warnings`.
# Repo wird per Volume gemountet (siehe docker-compose.yml).
#
# Basis-Image enthält nur Toolchain + OpenSSL für native crates.

FROM rust:1.83-bookworm

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        pkg-config \
        libssl-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# Standard: nichts ausführen — Compose setzt `command`.
CMD ["sh", "-c", "cargo test --workspace && cargo clippy --workspace --all-targets -- -D warnings"]
