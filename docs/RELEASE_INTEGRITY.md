# Release integrity — reproducible builds and M-of-N signing

Mycena **core** releases (Android APK, future desktop bundles) must be **reproducible** and **co-signed** by independent operators before they are marked **official**. Mini-apps continue to use [`reproducible_build`](../crates/mycena-miniapp/src/reproducible_build.rs) in their manifests; this document covers the main application.

**Goal:** No single person or CI job can ship a malicious binary unnoticed. Users compare the published **BLAKE3 hash** against a self-built artifact and check that **≥3 of 5** independent signers attested the same hash.

---

## 1. Threat model

| Attacker capability | Mitigation |
|---------------------|------------|
| Compromised GitHub Actions / one CI secret | M-of-N signers must co-sign **artifact hash** offline; CI alone cannot mark `official` |
| Coerced release engineer | Signers are geographically distributed; threshold requires collusion across jurisdictions |
| Supply-chain swap of download artifact | User verifies BLAKE3 locally; manifest binds hash + recipe digest |
| Recipe drift (toolchain changed silently) | `build_recipe_digest_hex` covers pinned recipe files + `Cargo.lock` |

---

## 2. Reproducible build recipe

### Pinned inputs

| Component | Location |
|-----------|----------|
| Rust toolchain | `rust-toolchain.toml` (currently **1.88.0**) |
| Crate lockfile | `Cargo.lock` (`cargo build --locked`) |
| Container definition | `docker/reproducible-build/` (`Dockerfile`, `toolchain.env`, `entrypoint.sh`) |
| Build driver | `scripts/reproducible-build.sh` |
| Android driver | `android/build.sh`, `android/gradle.properties` |

`docker/reproducible-build/toolchain.env` pins NDK **26.1.10909125**, JDK **17**, Android platform **34**, and `SOURCE_DATE_EPOCH=1700000000`.

### Build (anyone can reproduce)

```bash
# One-time or after recipe changes:
docker build -t mycena-reproducible-build docker/reproducible-build

# Reproducible Android APK + build report:
./scripts/reproducible-build.sh --tag v0.1.62
# → target/reproducible/mycena-v0.1.62-android.apk
# → target/reproducible/build-report.json
```

Inside the container:

- `SOURCE_DATE_EPOCH`, `CARGO_INCREMENTAL=0`, `RUSTFLAGS=-C debuginfo=0`
- Gradle daemon/cache disabled for determinism
- `cargo` / Gradle use locked dependencies

### Compare your hash to the published release

```bash
cargo run -p mycena-cli -- release hash target/reproducible/mycena-v0.1.62-android.apk
# Must match artifact_blake3_hex in the signed manifest
```

### Recipe digest

```bash
cargo run -p mycena-cli -- release recipe-digest .
```

Computed from sorted recipe files listed in [`RECIPE_PATHS`](../crates/mycena-core/src/release_integrity.rs). Any recipe change requires a new digest and fresh co-signatures.

---

## 3. M-of-N signature schema (`mycena-release-integrity/v1`)

Default threshold: **3 of 5** independent signers.

| Signer ID | Region |
|-----------|--------|
| `release-signer-eu` | Frankfurt / EU |
| `release-signer-na-east` | Virginia / US-East |
| `release-signer-na-west` | Oregon / US-West |
| `release-signer-ap` | Singapore / APAC |
| `release-signer-sa` | São Paulo / South America |

Signer Ed25519 keys are embedded in the client (`release_signer_pubkey_hex` derived deterministically for dev/CI; **production signers use offline HSM keys** with the same IDs once provisioned).

### Manifest (published alongside GitHub Release)

See [`docs/examples/release_integrity.example.json`](./examples/release_integrity.example.json).

Signed payload prefix: `MYCENA/RELEASE/ATTEST/v1\0` + `release_tag` + `artifact_name` + `artifact_blake3_hex` + `build_recipe_digest_hex`.

A release is **official** when:

1. `artifact_blake3_hex` matches the downloaded file.
2. `build_recipe_digest_hex` matches the tagged source tree.
3. ≥ `threshold.required` valid signatures from distinct known signer IDs.

Implementation: [`crates/mycena-core/src/release_integrity.rs`](../crates/mycena-core/src/release_integrity.rs).

---

## 4. Release signing workflow

### Step A — reproducible build (any builder)

```bash
./scripts/reproducible-build.sh --tag v0.1.62
```

### Step B — create manifest template

```bash
cargo run -p mycena-cli -- release init \
  --tag v0.1.62 \
  --artifact target/reproducible/mycena-v0.1.62-android.apk \
  --out release-integrity-v0.1.62.json
```

### Step C — independent signers (offline, out-of-band)

Each signer verifies the artifact locally, then signs:

```bash
./scripts/release-sign.sh release-integrity-v0.1.62.json release-signer-eu
# Repeat until ≥3 distinct signer_id entries exist
```

Signers MUST NOT share keys. CI MUST NOT hold ≥3 signer keys.

### Step D — publish

Attach to the GitHub Release:

- The APK (or desktop bundle)
- `release-integrity-v0.1.62.json`
- `SHA256SUMS.txt` (legacy; BLAKE3 in manifest is authoritative for official status)

Mark the release **official** in notes only when Step C threshold is met.

---

## 5. User verification

```bash
# Download APK + release-integrity-v0.1.62.json from GitHub Releases
./scripts/release-verify.sh release-integrity-v0.1.62.json mycena-v0.1.62-android.apk
```

Optional: rebuild from source tag and compare:

```bash
git checkout v0.1.62
./scripts/reproducible-build.sh --tag v0.1.62
cargo run -p mycena-cli -- release hash target/reproducible/mycena-v0.1.62-android.apk
# → must equal artifact_blake3_hex
```

---

## 6. CLI reference

```bash
mycena release hash ARTIFACT
mycena release recipe-digest [REPO_ROOT]
mycena release init --tag TAG --artifact PATH [--out MANIFEST.json]
mycena release sign MANIFEST.json --signer-id release-signer-eu
mycena release verify MANIFEST.json ARTIFACT [--repo .]
```

---

## 7. What is not yet reproducible

- **Desktop Tauri bundles** — recipe extension planned; use Android path as reference.
- **Play Store AAB** — separate signing key; sideload APK is the integrity reference artifact today.
- **100% byte-identical APK zip** — timestamps inside ZIP may vary slightly across Gradle versions; BLAKE3 of the **published CI/container artifact** is the attested hash. Goal: identical output across independent container rebuilds on the same tag.

---

## 8. Related documents

- [RELEASING.md](../RELEASING.md) — tag and GitHub Release process
- [RELAY_OPERATOR_GUIDE.md](./RELAY_OPERATOR_GUIDE.md) — federated infra (separate trust domain)
- [SECURITY_ASSUMPTIONS.md](./SECURITY_ASSUMPTIONS.md) — baked release expectations
