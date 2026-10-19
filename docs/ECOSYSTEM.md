# the maintainer Mycelium family

Three repositories support the **Mycelium** mesh messenger (client, relay, marketing site). There is **no central server stack** to deploy for the app itself — users run clients on Android/desktop; the relay is optional infrastructure.

**You are here:** [Mycelium](https://github.com/mycena-project/Mycelium) — mesh client (Android primary, desktop experimental).

## Repositories

| Repository | Role | Deploy? |
|------------|------|---------|
| **Mycelium** | libp2p mesh client + CLI | Client build only (`./android/build.sh`, `make desktop-dev`) |
| [mycelium-relay](https://github.com/mycena-project/mycelium-relay) | Public bootstrap relay (VPS) | Ops: `scripts/deploy-relay-vps.sh` in that repo |
| [mycelium-web](https://github.com/mycena-project/mycelium-web) | Static beta landing site | Vercel / `npm run build` |

## Lifecycle (by repo type)

| Goal | Mycelium (client) | mycelium-relay | mycelium-web |
|------|-------------------|----------------|--------------|
| **Develop / run** | `make desktop-dev` or `./android/build.sh` | `cargo run -p mycelium-relay` | `npm run preview` |
| **Stop** | Quit app / `make clean` (repo cleanup) | Ctrl+C / container stop | N/A (static) |
| **Production deploy** | App store / sideload APK | VPS script | Vercel |

the maintainer parent brand: [example.invalid](https://example.invalid) · legal baseline aligned with the maintainer-website.

## Documentation map

| Topic | Document |
|-------|----------|
| Android build | [android/README.md](android/README.md) |
| Security | [SECURITY.md](SECURITY.md) |
| Relay deploy | [mycelium-relay deploy/relay/README.md](https://github.com/mycena-project/mycelium-relay/blob/main/deploy/relay/README.md) |
| Web deploy | [mycelium-web docs/DEPLOYMENT-VERCEL.md](https://github.com/mycena-project/mycelium-web/blob/main/docs/DEPLOYMENT-VERCEL.md) |
