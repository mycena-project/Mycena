# Releasing

Semantic Versioning (`MAJOR.MINOR.PATCH`).

## Release process

1. Ensure CI is green on `main` (Rust + Android).
2. Update `CHANGELOG.md` if user-visible changes shipped.
3. Annotated tag: `git tag -a vX.Y.Z -m "vX.Y.Z"` && `git push origin vX.Y.Z`
4. GitHub Actions **Release** (`.github/workflows/release.yml`) builds:
   - Android APK via **reproducible container** (`scripts/reproducible-build.sh`)
   - Unsigned `release-integrity-*.json` + `build-report.json` (M-of-N co-sign offline per [docs/RELEASE_INTEGRITY.md](docs/RELEASE_INTEGRITY.md))
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
