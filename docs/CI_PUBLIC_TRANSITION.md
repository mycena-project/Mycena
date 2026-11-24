# CI public transition checklist

When the Mycena repository moves from **private (budget CI)** to **public (pilot launch)**, follow this list. The goal is a **one-variable switch** plus an OSS-Fuzz PR — not a CI rewrite.

**Repository variable (Settings → Secrets and variables → Actions → Variables):**

| Variable | Private pilot | Public pilot |
|----------|---------------|--------------|
| `MYCENA_CI_TIER` | `budget` (default if unset) | `full` |

> **Now (private):** Create `MYCENA_CI_TIER` = `budget` in repository variables so behaviour is explicit. If the variable is omitted, workflows default to `budget` via `${{ vars.MYCENA_CI_TIER || 'budget' }}`.

---

## Day-of checklist

### 1. Repository visibility

- [ ] Set GitHub repository visibility to **Public**.
- [ ] Confirm default branch protection still requires `Mycena CI`, `Fuzz (PR smoke)`, and other required checks.
- [ ] Update clone URLs in docs (`README`, `CONTRIBUTING`) if they still say private.

### 2. Enable full CI tier

- [ ] Set **`MYCENA_CI_TIER`** to **`full`** (repository variable).
- [ ] Push an empty commit or re-run workflows on `main` to verify:
  - [ ] `rust-full-linux` job runs (desktop UI + integration tests via nextest).
  - [ ] `rust-cross` matrix runs on **Windows** and **macOS**.
  - [ ] `Fuzz (nightly)` **scheduled** run is enabled (02:00 UTC).
  - [ ] `MycenaLab nightly` scheduled run is enabled (03:00 UTC).
  - [ ] `Android CI` runs on `main` even without Android-only diffs.
  - [ ] `CodeQL` weekly schedule runs.

### 3. OSS-Fuzz submission

Prepared artifacts: [`integrations/oss-fuzz/`](../integrations/oss-fuzz/).

- [ ] Confirm `project.yaml` / `Dockerfile` still use `mycena-project` and `github.com/mycena-project/Mycena/security/advisories/new` (placeholders were removed).
- [ ] Fork [google/oss-fuzz](https://github.com/google/oss-fuzz).
- [ ] Add `projects/mycena/` with `project.yaml`, `Dockerfile`, `build.sh` from this repo.
- [ ] Open PR against google/oss-fuzz (requires **public** upstream — blocked until step 1).
- [ ] After merge, confirm ClusterFuzz reports appear in the OSS-Fuzz dashboard.

### 4. Cross-platform matrix smoke

- [ ] Manually re-run **Mycena CI** on `main` with `MYCENA_CI_TIER=full`.
- [ ] Confirm **Windows** and **macOS** jobs pass (clippy + nextest `--lib`).
- [ ] Confirm **Linux full** job passes integration tests under `crates/mycena-node/tests/`.

### 5. Release integrity (public verifiability)

If [RELEASE_INTEGRITY.md](./RELEASE_INTEGRITY.md) is in use:

- [ ] Published release manifests, BLAKE3 hashes, and M-of-N signer list are visible without repo membership.
- [ ] `docs/examples/release_integrity.example.json` matches the live manifest format.
- [ ] Reproducible build recipe (`docker/reproducible-build/`, `rust-toolchain.toml`) is tagged consistently with the release tag.
- [ ] CI release workflow (`.github/workflows/release.yml`) artifacts are downloadable by unauthenticated users.

### 6. Fuzzing posture

| Mode | Workflow | When active |
|------|----------|-------------|
| PR smoke (45s × 4 targets) | `fuzz-pr.yml` | Every PR/push that touches Rust (not gated on `MYCENA_CI_TIER`) |
| GitHub nightly (multi-hour) | `fuzz-nightly.yml` | `MYCENA_CI_TIER=full` schedule + manual |
| OSS-Fuzz cluster | google/oss-fuzz | After step 3 merge |
| Local / ad-hoc long runs | `workflow_dispatch` on `fuzz-nightly.yml` | Any time (costs private minutes if repo still private) |

- [ ] Document security contact for fuzzer crash reports (OSS-Fuzz `primary_contact`).

### 7. CI minutes review (one week after `full`)

Public repos use **free** GitHub-hosted minutes, but each job is still capped at **6 hours**.

- [ ] After ~7 days on `full`, review Actions usage (Settings → Billing / Insights).
- [ ] If `fuzz-nightly` approaches the 6h job limit, split targets across multiple workflow jobs or reduce `seconds_per_target` default.
- [ ] Keep `concurrency.cancel-in-progress: true` enabled on PR workflows (already configured).

---

## What stays the same (no rewrite needed)

| Feature | Location |
|---------|----------|
| Cargo cache (registry + git + target) | `.github/actions/setup-rust-cache` |
| Parallel tests | `cargo nextest` + `.config/nextest.toml` |
| Path filters (docs-only → markdown lint) | `ci.yml` → `dorny/paths-filter` |
| PR concurrency cancel | All primary workflows |
| Budget vs full gating | `vars.MYCENA_CI_TIER` |

---

## Rollback

To return to budget mode (e.g. fork stays private):

1. Set `MYCENA_CI_TIER` back to **`budget`**.
2. Nightly fuzz / MycenaLab schedules stop automatically. PR smoke fuzz (`fuzz-pr.yml`) still runs on Rust diffs.

---

## Related docs

- [PUBLIC_REPOSITORY_AUDIT.md](./PUBLIC_REPOSITORY_AUDIT.md) — secrets / license / publication verdict
- [FUZZING.md](./FUZZING.md) — local fuzz commands and target list
- [integrations/oss-fuzz/README.md](../integrations/oss-fuzz/README.md) — OSS-Fuzz PR prep
- [MYCENALAB.md](./MYCENALAB.md) — simulation CI gates
- [RELEASE_INTEGRITY.md](./RELEASE_INTEGRITY.md) — reproducible releases and signing
