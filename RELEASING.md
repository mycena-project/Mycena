# Releasing

Semantic Versioning (`MAJOR.MINOR.PATCH`).

## Release process

1. Ensure CI is green on `main` (Rust + Android).
2. Record user-visible changes on the **GitHub Release** for the tag. This repository has no root `CHANGELOG.md`.
3. Annotated tag: `git tag -a vX.Y.Z -m "vX.Y.Z"` && `git push origin vX.Y.Z`
4. GitHub Actions **Release** (`.github/workflows/release.yml`) builds:
   - Android APK via **reproducible container** (`scripts/reproducible-build.sh`)
   - Unsigned `release-integrity-*.json` + `build-report.json` (M-of-N co-sign offline per [docs/RELEASE_INTEGRITY.md](docs/RELEASE_INTEGRITY.md))
   - CycloneDX SBOM (`mycelium-sbom-*.cdx.json`) — see [docs/DEPENDENCY_RISK.md](docs/DEPENDENCY_RISK.md)
   - Tauri desktop bundles (Linux / macOS / Windows)
   - `SHA256SUMS.txt` for all artifacts
5. Verify the GitHub Release page and install smoke-test on one platform.
6. Independent signers co-sign the artifact hash; users verify with `./scripts/release-verify.sh`.

## License reminder (AGPL-3.0-or-later)

Release notes include AGPL obligations. Operators who host a **modified relay** must comply — see [mycelium-relay](https://github.com/mycena-project/mycelium-relay).

## Related repos (separate tags)

| Repo | Deploy on tag |
|------|----------------|
| [mycelium-relay](https://github.com/mycena-project/mycelium-relay) | Fly.io |
| [mycelium-web](https://github.com/mycena-project/mycelium-web) | Vercel |

## Not in this workflow

- Play Store upload (`android-release-play.yml` — separate)
- Docker / server compose — client-only releases

---

## Desktop release runbook (Tauri)

### Prerequisites

- Rust stable + platform targets (`x86_64-apple-darwin`, `aarch64-apple-darwin`, `x86_64-pc-windows-msvc`, `x86_64-unknown-linux-gnu`)
- Node.js 20+ for `crates/mycelium-desktop/ui`
- macOS: Xcode CLT; Windows: WebView2 + VS Build Tools; Linux: `webkit2gtk`, `patchelf`

### Local build

```bash
cd crates/mycelium-desktop/ui && npm ci && npm run build
cd ../.. && cargo tauri build -p mycelium-desktop
```

Artifacts land under `crates/mycelium-desktop/target/release/bundle/` (`.dmg`, `.msi`, `.AppImage`, etc.).

### Code signing

| Platform | Notes |
|----------|--------|
| **macOS** | Sign with `codesign --deep --force --options runtime` using Developer ID Application. Notarize with `notarytool submit` + staple. Set `APPLE_SIGNING_IDENTITY`, `APPLE_ID`, `APPLE_PASSWORD`, `APPLE_TEAM_ID` in CI secrets. |
| **Windows** | Authenticode sign `.msi`/`.exe` with `signtool` or `osslsigncode`. EV cert recommended for SmartScreen trust. |
| **Linux** | Optional GPG-sign `.AppImage` or distro packages; upstream CI ships unsigned AppImage + `SHA256SUMS.txt`. |

### Auto-update (Tauri v2)

1. Configure updater pubkey in `crates/mycelium-desktop/tauri.conf.json` (`plugins.updater`).
2. GitHub Release attaches `{target}-update.json` + signed bundle (see `release.yml`).
3. Desktop checks on launch; offline mesh use continues if update fetch fails.

### Smoke test before tagging

1. Fresh install bundle on each target OS.
2. Start node → Settings → verify connectivity banner + crisis toggle.
3. Headless regression: `mycelium --headless --api-port 7760` then `curl -H "Authorization: Bearer $TOKEN" localhost:7760/api/v1/status` (token is printed at start).
4. Panic wipe from Settings (desktop) or `mycelium panic --yes --db .mycelium-node` (CLI) on a throwaway profile.

See also [RELEASE_INTEGRITY.md](docs/RELEASE_INTEGRITY.md) for artifact co-signing.
