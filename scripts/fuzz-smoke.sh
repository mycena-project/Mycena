#!/usr/bin/env bash
# Local fuzz smoke — mirrors the PR CI job (5 minutes per target).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/fuzz"

SECONDS_PER_TARGET="${FUZZ_SECONDS:-300}"
TARGETS=(
  fuzz_envelope
  fuzz_ble_frame
  fuzz_ble_stream
  fuzz_ble_multiaddr
  fuzz_gossipsub_wire
  fuzz_sync_bloom
  fuzz_invite
  fuzz_coin_payload
  fuzz_miniapp_bundle
)

if ! command -v cargo-fuzz >/dev/null 2>&1; then
  echo "Installing cargo-fuzz…"
  cargo install cargo-fuzz --locked
fi

for target in "${TARGETS[@]}"; do
  echo "==> $target (${SECONDS_PER_TARGET}s)"
  cargo fuzz run "$target" -- \
    -max_total_time="$SECONDS_PER_TARGET" \
    -rss_limit_mb=4096 \
    -timeout=30
done

echo "All fuzz smoke targets finished."
