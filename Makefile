SHELL := /bin/zsh

.PHONY: help desktop-dev desktop-stop desktop-nuke desktop-check-relay android-apk android-apk-ci android-apk-local android-install android-deploy android-nuke clean test test-fast test-integration test-unit test-sim check check-app-layer check-layer-boundaries check-unwrap-audit check-invariant-whitelist check-no-unwrap-prod clippy ci fmt fuzz-smoke docker-android-ci docker-compose-android gradle-wrapper sync-privacy-policy reproducible-build release-verify

help:
	@echo "Mycelium — the maintainer mesh client (no server stack)"
	@echo ""
	@echo "  make desktop-dev      Run desktop UI (Tauri) — local dev"
	@echo "  make desktop-stop     Stop desktop dev (Tauri / Vite / local relay)"
	@echo "  make desktop-nuke     Stop desktop + delete ALL local Mac data (scorched earth)"
	@echo "  make android-apk      Build Android debug APK (Docker, full rebuild)"
	@echo "  make android-install  Install debug APK on USB device (adb)"
	@echo "  make android-deploy   Stop desktop, rebuild APK, install on device"
	@echo "  make android-nuke     Clear ALL app data on USB device (scorched earth)"
	@echo "  make clean            Clean build artifacts (not a running mesh)"
	@echo "  make sync-privacy-policy  Copy docs/legal/privacy-policy.html → Android assets"
	@echo ""
	@echo "See docs/ECOSYSTEM.md · Android: ./android/build.sh"

.PHONY: test test-fast test-integration test-unit test-sim check check-app-layer check-layer-boundaries check-unwrap-audit check-invariant-whitelist check-no-unwrap-prod clippy ci fmt docker-android-ci docker-compose-android gradle-wrapper desktop-dev desktop-stop desktop-check-relay android-apk android-apk-ci android-apk-local android-install android-deploy android-nuke clean

# One-shot: add Gradle wrapper under android/ using Docker (no local Gradle needed).
gradle-wrapper:
	docker run --rm -v "$(CURDIR):/workspace" -w /workspace/android gradle:8.7-jdk17 \
		gradle wrapper --gradle-version=8.7 --distribution-type=bin

# Toolchain-only image (~3–4 GiB; NDK + JDK + Rust). No project COPY / no baked APK.
docker-android-ci:
	docker build --platform linux/amd64 -t mycelium-android-ci docker/android-ci

# Build APK via mounted repo (caches in Docker volumes, not on host).
docker-android-apk:
	./scripts/docker-android-build.sh

# Same toolchain image via Compose (profile "android").
docker-compose-android:
	docker compose --profile android build android-ci

check:
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo check --workspace

test-unit:
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo test --workspace --lib

test-sim:
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo test -p mycelium-sim -- --test-threads=1

test:
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo test --workspace

test-fast:
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo test --workspace --lib

test-integration:
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo test --workspace --tests

clippy:
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo clippy --workspace --all-targets -- -D warnings

check-app-layer:
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo check -p mycelium-l0-facade -p mycelium-l1-facade -p mycelium-miniapp -p mycelium-app -p mycelium-coin -p miniapp-sdk -p mycelium-desktop -p mycelium-cli

check-layer-boundaries:
	./scripts/check-layer-boundaries.sh

check-unwrap-audit:
	./scripts/check-unwrap-audit.sh

check-invariant-whitelist:
	./scripts/check-invariant-whitelist.sh

check-no-unwrap-prod:
	./scripts/check-no-unwrap-prod.sh

ci: check test-fast test-integration check-app-layer check-layer-boundaries check-no-unwrap-prod check-invariant-whitelist check-unwrap-audit clippy
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo test -p mycelium-core release_integrity --lib
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo test -p mycelium-node --test mutex_poison_recovery
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo test -p mycelium-ffi ffi_global_slots_survive_mutex_poison --lib
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo test -p mycelium-sim city_custody -- --test-threads=1
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo test -p mycelium-sim myceliumlab_ci_gates -- --test-threads=1
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo fmt --all --check

fuzz-smoke:
	./scripts/fuzz-smoke.sh

fmt:
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo fmt --all

desktop-check-relay:
	./scripts/check-mycelium-relay.sh

desktop-dev:
	./scripts/run-desktop.sh

desktop-stop:
	./scripts/stop-desktop.sh

desktop-nuke:
	./scripts/nuke-desktop-data.sh

# Default: mingc/android-build-box (no local rustup). See scripts/mingc-android-build.sh
android-apk:
	./scripts/mingc-android-build.sh

# Slim mycelium-android-ci image (scripts/docker-android-build.sh)
android-apk-ci:
	./scripts/docker-android-build.sh

# Host Rust + Gradle (needs rustup + Android NDK targets).
android-apk-local:
	./scripts/build-android-apk.sh --local

android-install:
	./scripts/install-android-apk.sh

android-install-github:
	./scripts/install-android-apk.sh --from-github

# Stop desktop dev, full Android rebuild (Rust + UniFFI + Gradle clean), adb install.
android-deploy: desktop-stop android-apk android-install

android-nuke:
	./scripts/nuke-android-data.sh

sync-privacy-policy:
	./scripts/sync-privacy-policy.sh

reproducible-build:
	./scripts/reproducible-build.sh

release-verify:
	@test -n "$(MANIFEST)" && test -n "$(ARTIFACT)" || (echo "Usage: make release-verify MANIFEST=release.json ARTIFACT=app.apk" >&2; exit 2)
	./scripts/release-verify.sh "$(MANIFEST)" "$(ARTIFACT)"

clean:
	./scripts/clean-repo.sh --deep
