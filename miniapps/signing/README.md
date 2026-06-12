# Official mini-app signing

Development builds use `official.ed25519` in this directory (gitignored).

```bash
# Generate key (once per machine)
cargo run -p miniapp-sdk -- keygen miniapps/signing

# Pack all official apps
./scripts/pack-official-miniapps.sh
```

Production releases should re-sign with the project curator identity and update `official.peer-id` in release notes.
