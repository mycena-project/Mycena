# Play Console — Data Safety Form Mapping

**Purpose:** Fill-in guide for Google Play Console → **App content → Data safety**
**Policy source:** [privacy_policy.html](../android/app/src/main/assets/privacy_policy.html) (July 2026)
**Threat / privacy context:** [THREAT_MODEL.md](./THREAT_MODEL.md) · [PRIVACY_ROADMAP.md](./PRIVACY_ROADMAP.md)

**Audience:** Person submitting the Play listing (product / legal / release manager).
**Not legal advice.** Google’s form labels change; verify against the live Console UI before submit.

---

## 0. Before you start

| Console field | Recommended answer | Notes |
|---------------|-------------------|-------|
| **Privacy policy URL** | `https://mycena-project.github.io/Mycena/privacy-policy.html` | Enable Pages: [docs/legal/README.md](../legal/README.md) · canonical source: [privacy-policy.html](../legal/privacy-policy.html) |
| **Does your app collect or share any of the required user data types?** | **Yes** | P2P mesh transmits user-generated data to **other users’ devices** and optional **relay infrastructure**. Google treats off-device transmission as collection/sharing even without a central Mycena server. |
| **Is all of the user data collected by your app encrypted in transit?** | **Partially / depends on mode** | Direct chat uses E2E when keys are exchanged. Bulletin, mail metadata, PeerInfo, and coin gossip are **not** all E2E. Select **Yes** only if Google allows “some data types”; otherwise answer honestly per type below. |
| **Do you provide a way for users to request that their data is deleted?** | **Yes** (device-local) | **Panic wipe** in Settings. Clarify in “Deletion” notes: mesh copies on other devices are **not** deletable by the Mycena project. |

---

## 1. Data not collected by the Mycena project (publisher)

Declare **No** for publisher collection where the open-source client does **not** send data to the Mycena project servers:

| Data type (Console category) | Collected by us? | Shared by us? |
|------------------------------|------------------|---------------|
| Email address | **No** | **No** |
| Phone number | **No** | **No** |
| Physical address | **No** | **No** |
| Analytics / advertising IDs | **No** | **No** |
| In-app search history (to publisher) | **No** | **No** |
| Crash logs **to the Mycena project** | **No** | **No** |

**Separate note:** Google Play may collect **install / crash diagnostics** under Google’s own data safety entry — that is **Google**, not Mycena app code. Do not claim “no crash data” globally if Play Services reporting is enabled; check Android manifest and Play dependencies.

---

## 2. Data types to declare (collected and/or shared)

Use **Collected** = transmitted off the user’s device (includes P2P to other users and relay).
Use **Shared** = transmitted to **third parties** other than the user’s chosen peers.

For Mycena, peers are **other users**, not the Mycena project. Relay operator is a **third party** for IP/circuit metadata only.

### 2.1 Personal info

| Console sub-type | Collected? | Shared? | With whom | Purpose (Console) | Required? | Encrypted in transit? | Deletion |
|------------------|------------|---------|-----------|-------------------|-----------|------------------------|----------|
| **User IDs** (libp2p PeerId, account id, public keys) | **Yes** | **Yes** | Other mesh peers; relay sees circuit IDs | App functionality | **Yes** (core mesh) | Partial (E2E inner); IDs are public | Panic wipe (local); mesh copies remain |
| **Name** (display name if user sets one) | **Yes** | **Yes** | Other mesh peers | App functionality | **Optional** (user-chosen) | No (plaintext in PeerInfo) | Panic wipe |
| **Other info** (contacts list = peer IDs + labels) | **Yes** | **No** to publisher; **Yes** to peers when messaging | Peers when user sends traffic | App functionality | Optional | E2E for chat body when active | Panic wipe |

**Console notes field (paste):**

> Cryptographic peer identifiers and optional display names are generated on-device and transmitted to other users over the decentralized mesh. We do not operate a central user database.

### 2.2 Messages

| Console sub-type | Collected? | Shared? | With whom | Purpose | Required? | Encrypted in transit? | Deletion |
|------------------|------------|---------|-----------|---------|-----------|------------------------|----------|
| **Emails** (if Console has generic “messages”) | **Yes** | **Yes** | Other users (mail/chat/bulletin) | App functionality | Optional per feature | **Partial** — E2E for direct chat after key exchange; groups use shared key; bulletins scoped | Panic wipe local; mesh replication not erasable centrally |
| **Other in-app messages** | **Yes** | **Yes** | Other users | App functionality | Optional | Partial | Same |

**Console notes:**

> End-to-end encryption applies to direct chat when keys are exchanged. Group and bulletin content uses different models; metadata (timing, size, peer IDs) is visible to peers and relay.

### 2.3 Financial info

| Console sub-type | Collected? | Shared? | With whom | Purpose | Required? | Encrypted in transit? | Deletion |
|------------------|------------|---------|-----------|---------|-----------|------------------------|----------|
| **Purchase history** / **Other financial** (MeshCoin txs, vouchers) | **Yes** | **Yes** | Other peers (coin gossip scope) | App functionality (experimental utility credits) | **Optional** (user initiates wallet use) | Signed payloads; not bank-grade privacy | Panic wipe |

**Console notes:**

> MeshCoin (MXC) is experimental local utility credit, not legal tender. Transaction metadata may gossip to peers; no bank or payment processor integration.

### 2.4 App activity

| Console sub-type | Collected? | Shared? | Purpose |
|------------------|------------|---------|---------|
| **Other user-generated content** (mini-app local storage) | **Yes** (on device) | **No** to publisher | App functionality |
| **Installed apps** (mini-apps user installs) | **Yes** (local list) | **No** to publisher | App functionality |

### 2.5 Device or other IDs

| Console sub-type | Collected? | Shared? | With whom | Notes |
|------------------|------------|---------|-----------|-------|
| **Device or other IDs** | **Avoid “advertising ID”** — we don’t use GAID | If Console forces “other IDs”: libp2p PeerId | Peers + relay | Not for ads; mesh identity |

---

## 3. Third-party sharing summary

| Third party | Data exposed | Publisher control |
|-------------|--------------|-------------------|
| **Other Mycena users (P2P)** | Messages, PeerIds, display name, keys, coin gossip | User chooses contacts / installs |
| **Relay operator** (operated relay on Fly.io, or a community relay) | IP address, circuit peer IDs, timing, volume | User can use mesh-only / LAN; see [THREAT_MODEL.md](./THREAT_MODEL.md) §4.5 |
| **Google Play** | Install/crash per Google policy | Platform |
| **the Mycena project** | **None** from app telemetry in OSS client | — |

**“Do you share data with third parties?”** → **Yes**, with **other users** and **relay operators** when Internet path is used. **No** sale of data. **No** ads.

---

## 4. Security practices (Console checkboxes)

| Question | Answer | Evidence |
|----------|--------|----------|
| Data encrypted in transit | **Select per type**; chat E2E when active | [privacy_policy.html](../android/app/src/main/assets/privacy_policy.html) § E2E limits |
| Users can request deletion | **Yes** | Panic wipe |
| Committed to Play Families Policy | Only if targeting children — **No** (not directed at under-13) | Privacy policy § Children |
| Independent security review | **No** (pending) | [THREAT_MODEL.md](./THREAT_MODEL.md) §8 |

---

## 5. Step-by-step Console workflow

1. **App content → Privacy policy** — paste public HTTPS URL (must match in-app policy substance).
2. **Data safety → Start** — “Yes, app collects or shares data.”
3. For each type in **§2**, tick **Collected** and/or **Shared** as table indicates.
4. For each ticked type, set:
   - **Purpose:** App functionality (primary); not Advertising or Analytics.
   - **Ephemeral:** Generally **No** (messages stored locally until TTL / wipe).
   - **Required:** Core mesh IDs **Yes**; display name / wallet **No**.
5. **Data safety → Preview** — verify JSON matches [privacy_policy.html](../android/app/src/main/assets/privacy_policy.html).
6. **Store listing → App category** — Communication / Social (confirm with product).

---

## 6. Copy-paste blocks for Console “notes” fields

**Deletion:**

> Users delete local data via Settings → Panic wipe (keys, messages, wallet, mini-app data). Mycena has no central server; copies on other users’ devices cannot be deleted by the developer.

**Encryption:**

> Direct messages use end-to-end encryption after keys are exchanged. Metadata (peer IDs, timing, message sizes, subscribed scopes) is not encrypted against network observers. See in-app privacy policy.

**Children:**

> App not directed at children under 13.

---

## 7. Checklist before submit

- [ ] Public privacy policy URL live and matches `privacy_policy.html`
- [ ] In-app Settings → Privacy policy opens same content
- [ ] `android:allowBackup="false"` confirmed (SD-171) — aligns with “data on device” story
- [ ] No analytics SDKs in `build.gradle` / dependency tree
- [ ] MeshCoin experimental disclaimer visible in app (Play “financial” expectations)
- [ ] Legal review of “P2P = shared with other users” wording
- [ ] Relay operator logging policy documented (Phase A ops — [PRIVACY_ROADMAP.md](./PRIVACY_ROADMAP.md))

---

## 8. Related documents

| Doc | Use |
|-----|-----|
| [privacy_policy.html](../android/app/src/main/assets/privacy_policy.html) | User-facing policy |
| [PRIVACY_ROADMAP.md](./PRIVACY_ROADMAP.md) | Phase A compliance tasks |
| [THREAT_MODEL.md](./THREAT_MODEL.md) | Reviewer context; not pasted into Play form |
| [BETA_SECURITY_ASSESSMENT.md](./BETA_SECURITY_ASSESSMENT.md) | Internal release gate |

---

## Change log

| Date | Change |
|------|--------|
| Jul 2026 | Initial Play Console mapping for pre–public beta |
