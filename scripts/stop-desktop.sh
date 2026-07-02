#!/usr/bin/env bash
# Stop Mycelium desktop dev: Tauri app, cargo tauri dev, Vite (:1420), optional local relay.
#
# Usage:
#   ./scripts/stop-desktop.sh
#   make desktop-stop
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DESKTOP_DIR="$ROOT/crates/mycelium-desktop"

stop_pids() {
  local label="${1:?stop_pids: missing label}"
  shift
  local pids=""
  local pattern pid
  for pattern in "$@"; do
    while IFS= read -r pid; do
      [[ -n "$pid" ]] && pids+="${pid} "
    done < <(pgrep -f "$pattern" 2>/dev/null || true)
  done
  pids="$(echo "$pids" | tr ' ' '\n' | sort -u | { grep -v '^$' || true; } | tr '\n' ' ' | sed 's/ $//')"
  if [[ -z "$pids" ]]; then
    return 0
  fi
  echo "Stopping ${label}..."
  # shellcheck disable=SC2086
  kill $pids 2>/dev/null || true
  sleep 1
  # shellcheck disable=SC2086
  kill -9 $pids 2>/dev/null || true
}

if [[ "$(uname -s)" == "Darwin" ]]; then
  osascript -e 'tell application "Mycelium" to quit' 2>/dev/null || true
  osascript -e 'tell application id "network.mycelium.desktop" to quit' 2>/dev/null || true
fi

stop_pids "Tauri dev (Mycelium)" \
  "${DESKTOP_DIR}.*cargo tauri dev" \
  "crates/mycelium-desktop.*cargo tauri dev" \
  "mycelium-desktop.*tauri dev"

stop_pids "mycelium-desktop binary" \
  "${ROOT}/target/debug/mycelium-desktop" \
  "${ROOT}/target/debug/deps/mycelium_desktop"

if command -v lsof >/dev/null 2>&1; then
  vite_pids="$(lsof -ti tcp:1420 2>/dev/null || true)"
  if [[ -n "$vite_pids" ]]; then
    echo "Stopping Vite dev server on :1420..."
    # shellcheck disable=SC2086
    kill $vite_pids 2>/dev/null || true
    sleep 1
    # shellcheck disable=SC2086
    kill -9 $vite_pids 2>/dev/null || true
  fi
fi

stop_pids "local mycelium-relay" \
  "cargo run -p mycelium-relay" \
  "target/debug/mycelium-relay"

stop_pids "mycelium node (desktop dev)" \
  "target/debug/mycelium-desktop" \
  "mycelium_node::transport"

echo "Desktop stopped."
