# WIP stash note — Android share

**Written:** 2026-08-01
**Status as of 2026-08-29:** ShareApp is **already on `main`**.

On 2026-08-01, incomplete Android share-path work was stashed so the working tree could stay clean:

```bash
git stash push -u -m "wip: android share"
```

That stash is **historical**. It does **not** mean the current Share implementation is uncommitted.

**Current tree:** phone-to-phone ShareApp is on `main` (see [OFFLINE_DISTRIBUTION.md](./OFFLINE_DISTRIBUTION.md)).

**Local stash:** a stash with that message **may** still contain extra deltas. Do **not** apply it blindly — it can conflict with or regress committed ShareApp. Inspect with `git stash list` / `git stash show` before any `apply` or `pop`.

No release tag was cut for the 2026-08-01 hygiene pass.
