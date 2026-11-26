# Building the Android app

`make android-apk` builds a debug APK on your machine with a **disk-space guard**.
It uses at most **10 GB** of disk, checks before it starts that enough space is free,
stops itself if it would use more, and removes all intermediates when it is done.

## One-time setup (macOS)

| Tool | Install | Disk (approx.) |
|---|---|---|
| Rust (rustup) | `curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs \| sh` | 1.5 GB |
| cargo-ndk | `cargo install cargo-ndk --locked` | small |
| Java 17 | `brew install --cask temurin@17` | 0.3 GB |
| Android SDK + NDK | Android Studio → SDK Manager → *SDK Platform 34*, *Build-Tools 34*, *NDK (Side by side)* | 3–4 GB |

The script finds the SDK via `ANDROID_HOME`, `android/local.properties` or
`~/Library/Android/sdk`, and the newest NDK under `$ANDROID_HOME/ndk/`.

## Build

```bash
make android-apk            # arm64-v8a (all current phones), ~3–5 GB peak, cleaned up afterwards
make android-apk-all        # arm64-v8a + armeabi-v7a + x86_64 (emulator)
./scripts/android-build-lean.sh --budget-gb 8 --keep-cache   # faster rebuilds, keeps intermediates
```

The APK is copied to `artifacts/android/mycena-<version>-<abi>-debug.apk`.
Install it with `adb install -r <apk>`.

### How the guard works

1. Before starting it requires *budget + reserve* free (default 10 GB + 5 GB).
2. Every 5 seconds it measures free space. If the build has used more than the
   budget, or free space drops below the reserve, it stops the whole build at once.
3. On success, failure or abort it deletes `.build-android/`, `android/app/build`,
   `android/uniffi-bindings/build` and `android/.gradle`.

## Why not Docker?

`make android-apk-docker` (the old `mingc/android-build-box` flow) runs an
x86 image under emulation on Apple Silicon. The image alone is larger than the
10 GB budget and Docker Desktop's disk image does not shrink by itself.

## Freeing space after the old Docker flow

```bash
docker image rm mingc/android-build-box:latest
docker volume rm mycena-mingc-cargo mycena-mingc-rustup mycena-mingc-target 2>/dev/null
docker builder prune -af
docker system df          # check what is left
```

Then, in Docker Desktop: *Settings → Resources → Advanced → Disk usage limit*,
lower it (for example 32 GB) and apply; this shrinks the disk image file.

Other regenerable caches on macOS:

```bash
rm -rf ~/.gradle/caches/transforms-* ~/.gradle/caches/build-cache-* ~/.gradle/daemon
cargo cache --autoclean 2>/dev/null || true   # needs: cargo install cargo-cache
```
