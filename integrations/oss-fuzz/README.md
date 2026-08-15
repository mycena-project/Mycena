# OSS-Fuzz integration (prepared — do not submit until repo is public)

These files are the **source of truth** for a future pull request to [google/oss-fuzz](https://github.com/google/oss-fuzz) under `projects/mycelium/`.

OSS-Fuzz requires a **public** upstream repository. Until the Mycelium pilot goes public, keep this directory in sync with `fuzz/` but **do not open the oss-fuzz PR**.

## Before submitting

1. Confirm `project.yaml` / `Dockerfile` still point at `the maintainer/Mycelium` and `security@example.invalid`.
2. Copy `project.yaml`, `Dockerfile`, and `build.sh` into `projects/mycelium/` in a fork of google/oss-fuzz.
3. Follow [OSS-Fuzz new project guide](https://google.github.io/oss-fuzz/getting-started/new-project-guide/rust-lang/).
4. Do **not** open the google/oss-fuzz PR until this repository is public.

## Local dry-run (optional)

```bash
git clone https://github.com/google/oss-fuzz.git
cp -R integrations/oss-fuzz/* oss-fuzz/projects/mycelium/
cd oss-fuzz
python infra/helper.py build_image mycelium
python infra/helper.py build_fuzzers mycelium
python infra/helper.py run_fuzzer mycelium fuzz_envelope
```

## Targets included in initial submission

| Fuzzer | Crate / surface |
|--------|-----------------|
| `fuzz_envelope` | `mycelium-core::data::Envelope` |
| `fuzz_ble_frame` | BLE MYBL wire frames |
| `fuzz_coin_payload` | MeshCoin gossip payloads |
| `fuzz_custody_envelope` | `DirectMessage` / custody DTN |

Additional targets from `fuzz/fuzz_targets/` can be added in follow-up oss-fuzz PRs.

See [docs/CI_PUBLIC_TRANSITION.md](../docs/CI_PUBLIC_TRANSITION.md) checklist item 1.
