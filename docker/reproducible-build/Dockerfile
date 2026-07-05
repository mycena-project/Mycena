# Reproducible Mycelium core build environment (Android APK primary artifact).
#
# Build:
#   docker build -t mycelium-reproducible-build docker/reproducible-build
#
# Run (from repo root):
#   ./scripts/reproducible-build.sh
#
# Recipe digest is computed from this directory + rust-toolchain.toml + Cargo.lock
# (see mycelium_core::release_integrity::compute_build_recipe_digest).

FROM --platform=linux/amd64 debian:bookworm-slim

ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    RUSTUP_HOME=/opt/rustup \
    CARGO_HOME=/opt/cargo

COPY toolchain.env /etc/mycelium/toolchain.env
RUN set -a && . /etc/mycelium/toolchain.env && set +a \
    && echo "NDK ${NDK_VERSION} Rust ${RUST_VERSION} epoch ${SOURCE_DATE_EPOCH}"

ENV ANDROID_NDK_HOME=/opt/android-sdk/ndk/26.1.10909125
ENV PATH="${CARGO_HOME}/bin:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        openjdk-17-jdk-headless \
        curl \
        ca-certificates \
        unzip \
        wget \
        build-essential \
        pkg-config \
        libssl-dev \
        perl \
        git \
    && rm -rf /var/lib/apt/lists/*

RUN set -a && . /etc/mycelium/toolchain.env && set +a \
    && mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && wget -q "https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}" -O /tmp/cmdline.zip \
    && unzip -q /tmp/cmdline.zip -d /tmp \
    && mv /tmp/cmdline-tools "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && rm -f /tmp/cmdline.zip \
    && yes | sdkmanager --licenses >/dev/null \
    && sdkmanager --install \
        "platforms;android-${ANDROID_PLATFORM}" \
        "build-tools;${ANDROID_BUILD_TOOLS}" \
        "ndk;${NDK_VERSION}" \
    && rm -rf "${ANDROID_SDK_ROOT}/.cache" /tmp/* /root/.android/cache 2>/dev/null || true

RUN set -a && . /etc/mycelium/toolchain.env && set +a \
    && curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs \
        | sh -s -- -y --default-toolchain "${RUST_VERSION}" --profile minimal \
    && rustup component add rustfmt clippy \
    && rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android \
    && cargo install cargo-ndk --locked \
    && rm -rf "${CARGO_HOME}/registry/cache" "${CARGO_HOME}/git/db" 2>/dev/null || true

WORKDIR /src
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
CMD ["./scripts/reproducible-build.sh"]
