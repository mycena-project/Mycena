# WIP stash note — Android share

**Date:** 2026-08-01

Incomplete Android share-path work on `main` was **not** committed. It was stashed so the working tree stays clean for ship discipline:

```bash
git stash push -u -m "wip: android share"
```

Restore later with `git stash list` / `git stash apply` (or `pop`) when continuing that feature. No release tag was cut for this hygiene pass.
