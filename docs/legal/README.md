# Legal documents (GitHub Pages)

Canonical **web** privacy policy for Play Console and public links.

| File | Purpose |
|------|---------|
| [privacy-policy.html](./privacy-policy.html) | Source of truth for web + in-app (via sync script) |

## Public URL (after Pages enabled)

```
https://mycena-project.github.io/Mycena/privacy-policy.html
```

Use this URL in **Google Play Console → App content → Privacy policy**.

## One-time GitHub setup

1. Repository **Settings → Pages → Build and deployment**
2. **Source:** GitHub Actions (not “Deploy from branch”)
3. Merge `.github/workflows/pages.yml` to `main`
4. After first workflow run: verify URL above loads

## Sync in-app copy

The Android asset is a copy of this file (without the canonical `<link>` tag):

```bash
make sync-privacy-policy
# or: ./scripts/sync-privacy-policy.sh
```

Run after editing `docs/legal/privacy-policy.html` before release builds.

## Related

- [PLAY_CONSOLE_DATA_SAFETY.md](../PLAY_CONSOLE_DATA_SAFETY.md) — Data Safety form mapping
- [PRIVACY_ROADMAP.md](../PRIVACY_ROADMAP.md) — compliance phases
