# Mycelium – Fortsetzung für Agents

Kurzüberblick, was **fertig** ist, was **optional** offen bleibt, und wie man **Tests ohne lokale Android-SDK-Installation** fahren kann.

## Lizenz

- **`LICENSE`**: proprietär (Verkauf / exklusive Nutzung durch den Rechteinhaber; keine automatischen Rechte für Dritte ohne Vertrag).
- **`RELICENSE-INTENT.md`**: nicht verbindliche Notiz, dass später ein **OSI**-Open-Source-Lizenzmodell (z. B. Apache-2.0/MIT) möglich ist — vor externen Beiträgen **CLA** o. Ä. planen.
- **Cargo**: `workspace.package.license-file = "LICENSE"` (kein crates.io-Publish vorgesehen).

## Tests in Docker (ohne lokales Android-SDK)

### Nur Rust (Workspace)

Voraussetzung: Docker (und auf Apple Silicon ggf. funktionierender Linux-Container).

```bash
cd Mycelium
docker compose --profile rust run --rm rust-tests
```

- Führt `cargo test --workspace` und `cargo clippy --workspace --all-targets -- -D warnings` aus.
- Der Repo-Tree wird **gemountet**; Cargo-Registry/Git liegen in **Volumes** (schnellere Wiederholungen).

### Vollständiger Android-Build (Rust NDK + UniFFI + Gradle)

Toolchain-Image (schlank, ohne Repo-COPY):

```bash
cd Mycelium
make docker-android-ci
./scripts/docker-android-build.sh
```

Siehe `docker/android-ci/Dockerfile` und `scripts/docker-android-build.sh`.

### Lokal ohne Docker

- Rust: `cargo test --workspace`, `cargo clippy --workspace --all-targets -- -D warnings`
- Android: `ANDROID_HOME` oder `android/local.properties` mit `sdk.dir=…`

---

## Discovery & contacts (Phase A–C)

- **Transport:** mDNS (LAN), relay circuit, rendezvous (opt-in), BLE bootstrap, manual multiaddr / `mycelium://invite/v1#…` QR.
- **Contacts:** `contacts:data` in app storage; chat only with **Accepted**; unknown → **Pending** + `on_contact_request` / `contacts-updated`.
- **Relay API:** `GET /rendezvous`, `POST /rendezvous/register`, `POST /rendezvous/unregister` (see [DISCOVERY.md](./DISCOVERY.md)).
- **Android i18n:** `values-de`, `values-ar`, `values-ru`, `values-fa`, `values-zh-rCN` synced via `scripts/sync-android-i18n.py`.

## Implementiert (Stand MVP, E2E / Gruppen / Multi-Hop)

- Payload-E2E: X25519 + ChaCha20-Poly1305 + HKDF (`mycelium-core::crypto`), Inner-Wrap für **Autor über mehrere Hops** (`mycelium-core::e2e_direct_wrap`).
- `WireMessage::{EncryptedDirect, EncryptedGroup, PeerInfo}`; Node: `PeerInfo` / `EncryptedDirect`, Gruppen-Gossip, Standard-Scope `mycelium/group/*`.
- Signatur-Check bei `WireMessage::Data`: **gegen `envelope.from_peer`** (Autor), nicht nur Transport-Peer.
- Gruppen: PSK, Storage, FFI, Desktop-`Groups.svelte`, Android-Gruppen-Flow (Screens + ViewModel).
- `send_chat` nutzt Verschlüsselung, wenn ein Enc-Key bekannt ist; Fallback ohne Rekursion (`send_chat_plain_direct`).

---

## Backlog / später wieder aufrollen (Priorität grob absteigend)

| Thema | Kurzbeschreibung | Hinweise / Dateien |
|--------|------------------|---------------------|
| **E2E-Envelope nach Decrypt** | Nach `EncryptedDirect`-Decrypt signiert der Node das `Envelope` mit dem **lokalen** Keypair, während `from_peer` der Autor ist – semantisch inkonsistent, falls später jemand Signaturen prüft. | `mycelium-node/src/node.rs` (`EncryptedDirect` → `Envelope::sign`) |
| **Forward Secrecy / Double Ratchet** | Bewusst nicht MVP; nächste Phase. | — |
| **Relay-Repo-Parität** | `mycelium-relay` ggf. an gleiche Wire-/Scope-Semantik anbinden. | separates Deployment |
| **Integrationstests Multi-Hop** | Zwei–drei simulierte Knoten: signierte `Data` + `EncryptedDirect` über Relay-Pfad, erwarteter `from_peer` / Storage. | `mycelium-sim`, ggf. neues `tests/` |
| **Android UI** | `sendChatDirect` vs. `sendChatEncrypted` vereinheitlichen (Verhalten ist durch Rust bereits ähnlich). | `ChatViewModel.kt` |
| **Doku Endnutzer** | Kurz „Mesh / E2E / Gruppen / öffentliche Kanäle“ im README oder Wiki. | optional |

---

## Nützliche Befehle für den nächsten Agenten

```bash
cd Mycelium
docker compose --profile rust run --rm rust-tests
cargo test --workspace
cargo clippy --workspace --all-targets -- -D warnings
```

UniFFI Kotlin neu erzeugen (wenn UDL geändert):

```bash
cargo run -p mycelium-ffi --bin uniffi-bindgen -- generate \
  crates/mycelium-ffi/src/mycelium.udl --language kotlin \
  --out-dir android/uniffi-bindings/src/main/kotlin/
perl -0777 -pi -e 's/\}\s+fun `addBootstrapPeer`/}\n\nfun `addBootstrapPeer`/' \
  android/uniffi-bindings/src/main/kotlin/uniffi/mycelium/mycelium.kt
```

---

## Kontext-Dateien (Einstieg)

| Bereich | Pfade |
|---------|--------|
| E2E Crypto + Wrap | `crates/mycelium-core/src/crypto.rs`, `e2e_direct_wrap.rs` |
| Wire / Enums | `crates/mycelium-core/src/transport.rs` |
| Node / Relay / Signatur | `crates/mycelium-node/src/node.rs`, `forwarding.rs` |
| App / Chat / Gruppen | `crates/mycelium-app/src/node.rs`, `groups.rs`, `storage.rs` |
| FFI / UDL | `crates/mycelium-ffi/src/mycelium.udl`, `lib.rs` |
| Desktop | `crates/mycelium-desktop/src/lib.rs`, `ui/src/views/Groups.svelte`, `Chat.svelte` |
| Android UI | `android/app/src/main/java/.../ui/`, `viewmodel/` |
| Docker | `docker/rust-tests/Dockerfile`, `docker/android-ci/Dockerfile`, `docker-compose.yml` |

Diese Datei absichtlich **knapp** gehalten; Details stehen in Code und Commit-Historie.
