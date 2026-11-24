# Minimal Android + Rust toolchain image (no project sources baked in).
#
# Build image (small context — only this directory):
#   docker build --platform linux/amd64 -t mycena-android-ci docker/android-ci
#
# Build APK (mount repo at run time):
#   ./scripts/docker-android-build.sh
#
# Image size is mostly Android NDK + JDK + Rust targets (~3–4 GiB). Avoid COPY . /
# RUN ./android/build.sh here — that duplicates Gradle/Rust output into layers.

FROM --platform=linux/amd64 debian:bookworm-slim

ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    RUSTUP_HOME=/opt/rustup \
    CARGO_HOME=/opt/cargo

ENV ANDROID_NDK_HOME="${ANDROID_SDK_ROOT}/ndk/26.1.10909125"
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
    && rm -rf /var/lib/apt/lists/*

# Android SDK: cmdline-tools, platform 34, build-tools, NDK (matches app/build.gradle.kts).
RUN mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && wget -q "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -O /tmp/cmdline.zip \
    && unzip -q /tmp/cmdline.zip -d /tmp \
    && mv /tmp/cmdline-tools "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && rm -f /tmp/cmdline.zip \
    && yes | sdkmanager --licenses >/dev/null \
    && sdkmanager --install \
        "platforms;android-34" \
        "build-tools;34.0.0" \
        "ndk;26.1.10909125" \
    && rm -rf "${ANDROID_SDK_ROOT}/.cache" /tmp/* /root/.android/cache 2>/dev/null || true

# Rust (minimal profile) + Android targets + cargo-ndk.
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain stable --profile minimal \
    && rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android \
    && cargo install cargo-ndk --locked \
    && rm -rf "${CARGO_HOME}/registry/cache" "${CARGO_HOME}/git/db" 2>/dev/null || true

WORKDIR /src
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
CMD ["./android/build.sh"]
