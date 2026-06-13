SHELL := /bin/zsh

.PHONY: help desktop-dev desktop-check-relay android-apk android-apk-ci android-apk-local android-install android-deploy clean test test-fast test-unit test-sim check clippy ci fmt docker-android-ci docker-compose-android gradle-wrapper

help:
	@echo "Mycelium — the maintainer mesh client (no server stack)"
	@echo ""
	@echo "  make desktop-dev     Run desktop UI (Tauri) — local dev"
	@echo "  make android-apk     Build Android debug APK"
	@echo "  make clean           Clean build artifacts (not a running mesh)"
	@echo ""
	@echo "See docs/ECOSYSTEM.md · Android: ./android/build.sh"

.PHONY: test test-fast test-unit test-sim check clippy ci fmt docker-android-ci docker-compose-android gradle-wrapper desktop-dev desktop-check-relay android-apk android-apk-ci android-apk-local android-install android-deploy clean

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
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo test -p mycelium-sim

test:
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo test --workspace

test-fast:
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo test --workspace --lib

clippy:
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo clippy --workspace --all-targets -- -D warnings

ci: check test-fast clippy
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo fmt --all --check

fmt:
	CARGO_HOME="$(CURDIR)/.cargo-home" cargo fmt --all

desktop-check-relay:
	./scripts/check-mycelium-relay.sh

desktop-dev:
	./scripts/run-desktop.sh

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

android-deploy: android-apk android-install

clean:
	./scripts/clean-repo.sh --deep
