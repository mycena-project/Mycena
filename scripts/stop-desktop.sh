#!/usr/bin/env bash
# Stop Mycena desktop dev: Tauri app, cargo tauri dev, Vite (:1420), optional local relay.
#
# Usage:
#   ./scripts/stop-desktop.sh
#   make desktop-stop
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DESKTOP_DIR="$ROOT/crates/mycena-desktop"

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
  osascript -e 'tell application "Mycena" to quit' 2>/dev/null || true
  osascript -e 'tell application id "io.github.mycena_project.desktop" to quit' 2>/dev/null || true
fi

stop_pids "Tauri dev (Mycena)" \
  "${DESKTOP_DIR}.*cargo tauri dev" \
  "crates/mycena-desktop.*cargo tauri dev" \
  "mycena-desktop.*tauri dev"

stop_pids "mycena-desktop binary" \
  "${ROOT}/target/debug/mycena-desktop" \
  "${ROOT}/target/debug/deps/mycena_desktop"

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

stop_pids "local mycena-relay" \
  "cargo run -p mycena-relay" \
  "target/debug/mycena-relay"

stop_pids "mycena node (desktop dev)" \
  "target/debug/mycena-desktop" \
  "mycena_node::transport"

echo "Desktop stopped."
