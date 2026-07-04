# Play Console submission checklist

**Use with:** [PLAY_CONSOLE_DATA_SAFETY.md](./PLAY_CONSOLE_DATA_SAFETY.md) · [docs/legal/privacy-policy.html](./legal/privacy-policy.html)

---

## Before submit

- [ ] GitHub Pages live: `https://example.invalid/Mycelium/privacy-policy.html`
- [ ] In-app policy matches web (`make sync-privacy-policy` + CI green)
- [ ] Release build tested on physical device
- [ ] MeshCoin experimental disclaimer visible in wallet UI

---

## Play Console → App content

### Privacy policy

| Field | Value |
|-------|-------|
| **URL** | `https://example.invalid/Mycelium/privacy-policy.html` |

### Data safety (summary answers)

| Question | Answer |
|----------|--------|
| Collect or share user data? | **Yes** |
| Data encrypted in transit? | **Partially** — per-type; E2E for direct chat when active |
| Users can request deletion? | **Yes** — Panic wipe (local only; disclose mesh copies) |
| Data sold? | **No** |
| Data used for advertising? | **No** |

### Data types to declare (tick Collected + Shared where noted)

See [PLAY_CONSOLE_DATA_SAFETY.md §2](./PLAY_CONSOLE_DATA_SAFETY.md#2-data-types-to-declare-collected-andor-shared) for full table.

| Type | Collected | Shared | Notes |
|------|-----------|--------|-------|
| User IDs (PeerId) | Yes | Yes (peers) | App functionality |
| Name (display name) | Optional | Yes (peers) | User-chosen |
| Messages | Yes | Yes (peers) | E2E direct chat when keys exchanged |
| Financial (MeshCoin) | Optional | Yes (peers, coin scope) | Experimental utility credit |
| Device/other IDs | No GAID | PeerId to peers | Not for ads |

### Third parties

| Party | Shared? |
|-------|---------|
| Other Mycelium users (P2P) | Yes |
| Relay operator (IP, circuit metadata) | Yes when Internet path used |
| the maintainer analytics | No |

---

## Store listing copy (honest)

- Do **not** claim “anonymous” or “Signal-level security”
- Do state: decentralized mesh, E2E direct chat when keys exchanged, experimental MeshCoin
- Link to GitHub KNOWN_LIMITATIONS for high-risk users

---

## After submit

- [ ] Save Data Safety export / screenshot for audit trail
- [ ] Note submission date in [PRE_RELEASE_GAPS.md](./PRE_RELEASE_GAPS.md)
- [ ] Schedule [EXTERNAL_SECURITY_REVIEW.md](./EXTERNAL_SECURITY_REVIEW.md) if not started

---

## Change log

| Date | Notes |
|------|-------|
| Jul 2026 | Initial checklist |
