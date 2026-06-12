#!/usr/bin/env bash
# Pack and sign official mini-apps → miniapps/dist/*.mxa
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# shellcheck source=ensure-rust-env.sh
source "$ROOT/scripts/ensure-rust-env.sh"
require_cargo

KEY_DIR="$ROOT/miniapps/signing"
KEY_FILE="$KEY_DIR/official.ed25519"

mkdir -p "$KEY_DIR" miniapps/dist

cargo build -q -p miniapp-sdk

if [[ ! -f "$KEY_FILE" ]]; then
  echo "Generating signing key: $KEY_FILE"
  cargo run -q -p miniapp-sdk -- keygen "$KEY_DIR"
fi

for app in meshaid meshmarket proximity; do
  out="miniapps/dist/${app}.mxa"
  echo "==> $app → $out"
  cargo run -q -p miniapp-sdk -- pack-sign \
    "miniapps/$app" \
    -o "$out" \
    --identity "$KEY_FILE"
  cargo run -q -p miniapp-sdk -- lint "$out"
done

ASSETS="$ROOT/crates/mycelium-app/assets/miniapp"
mkdir -p "$ASSETS/bundles"
cp miniapps/dist/*.mxa "$ASSETS/bundles/"
cargo run -q -p miniapp-sdk -- listings miniapps/dist \
  --identity "$KEY_FILE" \
  -o "$ASSETS/bundled_listings.json"

echo "Done."
