# mycena-project Mycena family

Two repositories make up **Mycena** (client and relay). A project website will follow with the next public beta. There is **no central server stack** to deploy for the app itself — users run clients on Android/desktop; the relay is optional infrastructure.

**You are here:** [Mycena](https://github.com/mycena-project/Mycena) — mesh client (Android primary, desktop experimental).

## Repositories

| Repository | Role | Deploy? |
|------------|------|---------|
| **Mycena** | libp2p mesh client + CLI | Client build only (`./android/build.sh`, `make desktop-dev`) |
| [mycena-relay](https://github.com/mycena-project/mycena-relay) | Public bootstrap relay (Fly.io) | Ops: `scripts/deploy-relay-fly.sh` in that repo |

## Lifecycle (by repo type)

| Goal | Mycena (client) | mycena-relay |
|------|-------------------|----------------|
| **Develop / run** | `make desktop-dev` or `./android/build.sh` | `cargo run -p mycena-relay` |
| **Stop** | Quit app / `make clean` (repo cleanup) | Ctrl+C / container stop |
| **Production deploy** | App store / sideload APK | Fly.io script |

## Documentation map

| Topic | Document |
|-------|----------|
| Android build | [android/README.md](../android/README.md) |
| Security | [SECURITY.md](SECURITY.md) |
| Relay deploy | [mycena-relay deploy/relay/README.md](https://github.com/mycena-project/mycena-relay/blob/main/deploy/relay/README.md) |
