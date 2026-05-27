#!/usr/bin/env bash
# Start Mycelium desktop (Tauri) after optional relay checks.
#
# Usage:
#   ./scripts/run-desktop.sh              # check relay, then cargo tauri dev
#   ./scripts/run-desktop.sh --check-only # relay probe only (exit 0/1)
#   ./scripts/run-desktop.sh --skip-relay-check
#   MYCELIUM_START_LOCAL_RELAY=1 ./scripts/run-desktop.sh
#
# Local relay → point bootstrap at 127.0.0.1 (set after relay prints Peer ID):
#   export MYCELIUM_BOOTSTRAP_PEERS="/ip4/127.0.0.1/tcp/4001/p2p/<PEER_ID>"
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DESKTOP_DIR="$ROOT/crates/mycelium-desktop"
UI_DIR="$DESKTOP_DIR/ui"
CHECK_ONLY=0
SKIP_RELAY=0
LOCAL_RELAY_PID=""

usage() {
  sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
}

for arg in "$@"; do
  case "$arg" in
    -h|--help) usage; exit 0 ;;
    --check-only) CHECK_ONLY=1 ;;
    --skip-relay-check) SKIP_RELAY=1 ;;
    *) echo "Unknown option: $arg" >&2; usage; exit 2 ;;
  esac
done

cleanup() {
  if [[ -n "$LOCAL_RELAY_PID" ]] && kill -0 "$LOCAL_RELAY_PID" 2>/dev/null; then
    kill "$LOCAL_RELAY_PID" 2>/dev/null || true
    wait "$LOCAL_RELAY_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

if [[ "$SKIP_RELAY" -eq 0 ]]; then
  if [[ "${MYCELIUM_START_LOCAL_RELAY:-}" == "1" ]]; then
    echo "Starting local mycelium-relay (logs → /tmp/mycelium-relay.log)…"
    export MYCELIUM_RELAY_HOST="127.0.0.1"
    export MYCELIUM_RELAY_STATUS_URL="http://127.0.0.1:8080/"
    export MYCELIUM_RELAY_HEALTH_URL="http://127.0.0.1:8080/health"
    RUST_LOG="${RUST_LOG:-info}" cargo run -p mycelium-relay --manifest-path "$ROOT/Cargo.toml" \
      > /tmp/mycelium-relay.log 2>&1 &
    LOCAL_RELAY_PID=$!
    sleep 2
  fi

  echo "Checking mycelium-relay…"
  if ! "$ROOT/scripts/check-mycelium-relay.sh"; then
    echo "" >&2
    echo "Relay check failed. Desktop may still start but peer discovery via relay can fail." >&2
    echo "  • Use production relay: ensure network access to mycelium-relay.fly.dev" >&2
    echo "  • Or local relay: MYCELIUM_START_LOCAL_RELAY=1 $0" >&2
    echo "  • Or skip: $0 --skip-relay-check" >&2
    if [[ "$CHECK_ONLY" -eq 1 ]]; then
      exit 1
    fi
    if [[ "${MYCELIUM_RELAY_STRICT:-}" == "1" ]]; then
      exit 1
    fi
  else
    echo "Relay check passed."
  fi
fi

if [[ "$CHECK_ONLY" -eq 1 ]]; then
  exit 0
fi

if ! command -v cargo >/dev/null 2>&1; then
  echo "cargo not found — install Rust: https://rustup.rs" >&2
  exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "npm not found — install Node.js for the desktop UI" >&2
  exit 1
fi

if [[ ! -d "$UI_DIR/node_modules" ]]; then
  echo "Installing UI dependencies…"
  (cd "$UI_DIR" && npm install)
fi

if ! cargo tauri --version >/dev/null 2>&1; then
  echo "Installing tauri-cli…"
  cargo install tauri-cli --version "^2" --locked
fi

echo ""
echo "Starting Mycelium desktop (Tauri dev)…"
echo "  • Peer count: status banner / Peers tab in the app"
echo "  • Relay operator status: Peers tab (HTTP poll every 30s)"
echo "  • Data dir (macOS): ~/Library/Application Support/mycelium"
echo ""

cd "$DESKTOP_DIR"
exec cargo tauri dev
