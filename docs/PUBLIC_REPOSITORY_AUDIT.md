# Public repository readiness audit

**Date:** 2026-08-29
**HEAD:** `fb32b1e5c89b318e14b1adc2a76b7dcefac6b3ec` (`main`)
**Scope:** Mycena git tree (`origin`: `https://github.com/mycena-project/Mycena.git`). This workspace also contains **uncommitted** freeze/hygiene diffs; findings below distinguish **tracked HEAD** vs **this working tree**.
**Method:** `git ls-files`, pattern search (keys/tokens/paths), history `git log -S` for PEM headers, license/docs review, `cargo check -p mycena-core --locked`. Gitleaks Action exists but **no job URL** was recorded for this SHA. GitHub visibility was **not** confirmed (`gh repo view` failed in this environment).

**Does not replace:** [CI_PUBLIC_TRANSITION.md](./CI_PUBLIC_TRANSITION.md) (day-of switch), [RELEASE_FREEZE_PLAN.md](./RELEASE_FREEZE_PLAN.md) G8.

---

## Verdict

Conditionally ready.

The **tracked tree has no confirmed secrets, signing material, or personal SDK paths**. License, security contact, and known-limitations docs exist. A stranger can follow `./android/build.sh` / `cargo` without private files.

It is **not** `READY FOR PUBLICATION` until the conditions in §7 are done. It is **not** `NOT READY`: no live credential was found in tracked files.

Making the GitHub repository Public **today without those conditions** would be operationally incomplete (CI tier, Dependabot enablement, secret-scan evidence), not a secret leak from the current index.

---

## 1. Findings

Severity: **Critical** = credential in git; **High** = likely leak or legal block; **Medium** = public confusion / G8 gap; **Low** = hygiene.

| ID | Area | Severity | Finding | Evidence | Remediation | Remaining risk |
|----|------|----------|---------|----------|-------------|----------------|
| PUB-01 | Secrets | — | No PEM/JKS/`.env`/keystore in `git ls-files`. Play workflow uses `${{ secrets.* }}` names only. | `git ls-files` empty for `android/local.properties`, `*.jks`, `.env`; `.github/workflows/android-release-play.yml` | None required for tracked files. | **Git history** not fully scanned with Gitleaks in this session. RFB-SEC-04 still needs a green `secret-scan.yml` job URL. |
| PUB-02 | Secrets | Low | Filename hits `secrets.rs` / `toolchain.env` are **code/config**, not cloud keys. | `crates/mycena-node/src/secrets.rs`; `docker/reproducible-build/toolchain.env` | No change. | None. |
| PUB-03 | History | Medium | `git log -S` for PEM RSA and OpenSSH private-key armor headers returned **no commits**. Filename add of `*.pem`/`*.jks`/`.env` empty. | Commands on this clone | Run Gitleaks with `fetch-depth: 0` on freeze SHA (workflow already does this). If a hit appears, treat as P0 (rotate + consider history rewrite). | Full regex Gitleaks corpus not executed locally. |
| PUB-04 | Generated | — | `target/`, `android/**/build/`, `android/rust-build/`, `android/local.properties`, `artifacts/`, UniFFI `mycena.kt` are gitignored and untracked. Only expected binary: `android/gradle/wrapper/gradle-wrapper.jar`. | `.gitignore`; `git ls-files` | None. | Maintainers must not `git add -f` local.properties (RFB-HYG-03). |
| PUB-05 | Personal | Low | `android/local.properties` contains `sdk.dir=/Users/mycena-project/Library/Android/sdk` **on disk**, gitignored. | Local file; `git check-ignore` | Keep ignored. Do not commit. | Clone machines will recreate via `ANDROID_HOME` in `android/build.sh`. |
| PUB-06 | Personal | — | Org emails `github.com/mycena-project/Mycena/security/advisories/new`, `github.com/mycena-project/Mycena/issues`, `github.com/mycena-project/Mycena/issues` are **appropriate** public contacts. Third-party author `Age@AgeManning.com` is upstream attribution. | `SECURITY.md`; vendored Cargo.toml | None. | None. |
| PUB-07 | Personal | Low | A maintainer-only workflow named a former grant plan and old issue numbers. | (removed) | **Fixed 2026-11-24:** workflow deleted; roadmap issues recreated without funding references. | None. |
| PUB-08 | Infra | — | `mycena-relay.fly.dev` and GitHub/Vercel URLs are **documented public** bootstrap/web, not credentials. | `README.md`; `SECURITY.md` | None. | Relay `/status` telemetry is already a known limitation (SD-003). |
| PUB-09 | License | Medium | Vendored `third-party/libp2p-gossipsub` is MIT but had **no LICENSE file**. | Directory listing before fix | **Fixed:** added MIT `LICENSE`; README-MYCENA notes AGPL workspace vs MIT crate. | Upstream copyright line is Parity/Age Manning (standard rust-libp2p MIT). |
| PUB-10 | License | — | Root `LICENSE` is AGPL-3.0. `deny.toml` allows AGPL + common permissive licenses. `SECURITY.md` states AGPL network source obligation. | `LICENSE`; `deny.toml` | None. | `cargo deny` not re-run in this audit (RFB-SC-01). |
| PUB-11 | License | Medium | SD-150 claimed Dependabot missing and SBOM/repro/signing missing while scripts exist. | `SECURITY_DEBT_REGISTER.md` before fix | **Fixed:** SD-150 matches `check-dependencies.sh`, SBOM, reproducible build, integrity docs. Added `.github/dependabot.yml`. | Dependabot **GitHub setting** may still be off until public (intentional for budget CI). |
| PUB-12 | Docs | Medium | README architecture listed BLE as a mesh layer without hardware verification. | README diagram | **Fixed:** BLE marked opt-in / **not hardware-verified**; link to KNOWN_LIMITATIONS. Samsung already untested in KNOWN_LIMITATIONS. | Pocket-mesh still needs RFB-HW-03 or remaining DOC-05 on other docs (LEVEL0 / disaster mesh). RFB-DOC-01 §11 not fully disposed. |
| PUB-13 | Docs | Medium | `RELEASING.md` required root `CHANGELOG.md` which does not exist (C12). | `RELEASING.md` vs tree | **Fixed:** step 2 → GitHub Release notes. | None for G8; G9 still needs a real RC. |
| PUB-14 | Docs | — | No certification claim in README/BETA_SECURITY_ASSESSMENT; both refuse Signal-level security. External review **not commissioned**. | README; `EXTERNAL_SECURITY_REVIEW.md` | None (do not fake an audit). | Public must not treat this audit as a security certification. |
| PUB-15 | Docs | Low | `CI_PUBLIC_TRANSITION.md` day-of boxes unchecked; `ci.yml` still describes budget-default for private. | Checkboxes; workflow comment | Fill on visibility flip (RFB-HYG-01). Set `MYCENA_CI_TIER=full` when public. | Budget mode on a **public** repo would under-test Android/cross; document if delayed. |
| PUB-16 | Build | Medium | UniFFI Kotlin is gitignored; `./gradlew assembleDebug` on a clean tree fails. Documented happy path is `./android/build.sh` (regenerates bindings). | `.gitignore`; `android/build.sh` | **Fixed:** `android/README.md` §2 warns that Gradle-only needs prior UniFFI + NDK output. | This audit **did not** run `./android/build.sh` (NDK/time). `cargo check -p mycena-core --locked` **succeeded**. |
| PUB-17 | CI hygiene | Low | Path filter `crates/mycena-android/**` (crate does not exist). | `android-ci.yml`, `ci.yml` | **Fixed:** removed. | None. |
| PUB-18 | Process | Medium | Working tree has large **uncommitted** freeze (share, Samsung, harness). Publishing HEAD `fb32b1e` would **omit** those docs/fixes. | `git status` | Commit/push the intended public snapshot before flipping visibility. | Accidental public of stale `main` vs local work. |

---

## 2. What was not found (tracked)

- Private keys, API keys, `ghp_` / `AKIA` / `AIza` patterns in searched text files
- Signing keystores or Play passwords in git
- Tracked APK/`.so`/Gradle caches (other than wrapper JAR)
- IDE (`.idea`, `.vscode`) or editor agent transcripts in `git ls-files`

---

## 3. Documentation honesty (public claims)

| Claim class | Status |
|-------------|--------|
| Security certification | Not claimed. |
| Play Store / production money (MXC) | README: experimental preview; not money. |
| Samsung support | KNOWN_LIMITATIONS: **not hardware-verified**. |
| BLE pocket mesh | README now **not hardware-verified**; hardware matrix empty. |
| Planned-as-done | Freeze §11 still has open contradictions (G1 / RFB-DOC-01). Do not treat LEVEL0 A2 as signed off. |

Do **not** hide [KNOWN_LIMITATIONS.md](./KNOWN_LIMITATIONS.md) or the debt register.

---

## 4. Build (documented clean checkout)

| Path | Documented command | This audit |
|------|-------------------|------------|
| Rust core | `cargo test --workspace` / clippy (CONTRIBUTING) | `cargo check -p mycena-core --locked` **OK** (2026-08-29). Full workspace tests **not** run. |
| Android APK | `./android/build.sh` then `adb install` (README) | Script regenerates UniFFI + NDK `.so`. **Not executed** here. |
| Android Gradle-only | `./gradlew assembleDebug` | Will fail without generated `uniffi` — now documented. |
| Docker Android | `./scripts/docker-android-build.sh` | Not executed. |

Missing private files required to build: **none** (SDK path is generated or `local.properties` local-only).

---

## 5. Remediation applied in this task

- `third-party/libp2p-gossipsub/LICENSE` (MIT) + README note
- `.github/dependabot.yml` + SD-150 honesty
- Removed phantom `mycena-android` CI paths
- `RELEASING.md` no longer requires missing CHANGELOG
- `android/README.md` UniFFI/clean-checkout warning
- README BLE hardware-verification wording
- Freeze backlog: RFB-DOC-04, RFB-SC-02, RFB-HYG-02 `done`; RFB-HYG-03 / RFB-DOC-05 `in_progress`

---

## 6. Explicitly not done

- GitHub visibility change, `MYCENA_CI_TIER=full`, ticking CI_PUBLIC_TRANSITION
- Enabling Dependabot in GitHub **settings**
- History rewrite
- OSS-Fuzz submission (blocked on public upstream; P2)
- Full `cargo test --workspace` / Android APK / Gitleaks CLI
- Disposing remaining freeze §11 rows (RFB-DOC-01)
- Filling [HARDWARE_TEST_MATRIX.md](./HARDWARE_TEST_MATRIX.md)

---

## 7. Conditions to reach `READY FOR PUBLICATION`

1. **Commit and push** the snapshot you intend to publish (or accept that origin `fb32b1e` is the public tree).
2. Green **Secret Scan** (`secret-scan.yml`) on that SHA — RFB-SEC-04. If Gitleaks hits history, rotate and remediate before Public.
3. Fill [CI_PUBLIC_TRANSITION.md](./CI_PUBLIC_TRANSITION.md) day-of (or waiver). Set **`MYCENA_CI_TIER=full`** when public, or write that budget mode remains with impact.
4. Enable **Dependabot** in GitHub settings (file is ready) **or** document leaving it off.
5. Keep Samsung/BLE claims limited until hardware evidence exists.
6. Optional but G8-adjacent: stranger clone + `./android/build.sh` (or Docker) smoke.

Until 1–4: stay **CONDITIONALLY READY**.
