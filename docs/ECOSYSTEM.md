# mycena-project Mycena family

Three repositories support the **Mycena** mesh messenger (client, relay, marketing site). There is **no central server stack** to deploy for the app itself — users run clients on Android/desktop; the relay is optional infrastructure.

**You are here:** [Mycena](https://github.com/mycena-project/Mycena) — mesh client (Android primary, desktop experimental).

## Repositories

| Repository | Role | Deploy? |
|------------|------|---------|
| **Mycena** | libp2p mesh client + CLI | Client build only (`./android/build.sh`, `make desktop-dev`) |
| [mycena-relay](https://github.com/mycena-project/mycena-relay) | Public bootstrap relay (Fly.io) | Ops: `scripts/deploy-relay-fly.sh` in that repo |
| [mycena-web](https://github.com/mycena-project/mycena-web) | Static beta landing site | Vercel / `npm run build` |

## Lifecycle (by repo type)

| Goal | Mycena (client) | mycena-relay | mycena-web |
|------|-------------------|----------------|--------------|
| **Develop / run** | `make desktop-dev` or `./android/build.sh` | `cargo run -p mycena-relay` | `npm run preview` |
| **Stop** | Quit app / `make clean` (repo cleanup) | Ctrl+C / container stop | N/A (static) |
| **Production deploy** | App store / sideload APK | Fly.io script | Vercel |


## Documentation map

| Topic | Document |
|-------|----------|
| Android build | [android/README.md](../android/README.md) |
| Security | [SECURITY.md](SECURITY.md) |
| Relay deploy | [mycena-relay deploy/relay/README.md](https://github.com/mycena-project/mycena-relay/blob/main/deploy/relay/README.md) |
| Web deploy | [mycena-web docs/DEPLOYMENT-VERCEL.md](https://github.com/mycena-project/mycena-web/blob/main/docs/DEPLOYMENT-VERCEL.md) |
