#!/usr/bin/env bash
# Probe mycelium-relay reachability (HTTP status API + TCP dial on :4001).
set -euo pipefail

RELAY_HOST="${MYCELIUM_RELAY_HOST:-mycelium-relay.fly.dev}"
RELAY_STATUS_URL="${MYCELIUM_RELAY_STATUS_URL:-https://${RELAY_HOST}/}"
RELAY_TCP_PORT="${MYCELIUM_RELAY_TCP_PORT:-4001}"
RELAY_HEALTH_URL="${MYCELIUM_RELAY_HEALTH_URL:-https://${RELAY_HOST}/health}"

http_ok=0
tcp_ok=0

if command -v curl >/dev/null 2>&1; then
  if status_json="$(curl -fsS --max-time 8 "$RELAY_STATUS_URL" 2>/dev/null)"; then
    if echo "$status_json" | grep -q '"status"[[:space:]]*:[[:space:]]*"ok"'; then
      http_ok=1
      peer_id="$(echo "$status_json" | sed -n 's/.*"peer_id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)"
      conns="$(echo "$status_json" | sed -n 's/.*"connections"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -1)"
      echo "relay: HTTP ok (${RELAY_STATUS_URL}) connections=${conns:-?} peer_id=${peer_id:-?}"
    else
      echo "relay: HTTP reachable but status != ok (${RELAY_STATUS_URL})" >&2
    fi
  else
    echo "relay: HTTP unreachable (${RELAY_STATUS_URL})" >&2
  fi
  if health_json="$(curl -fsS --max-time 5 "$RELAY_HEALTH_URL" 2>/dev/null)"; then
    if echo "$health_json" | grep -qE '"ok"[[:space:]]*:[[:space:]]*true|^ok$'; then
      echo "relay: health ok (${RELAY_HEALTH_URL})"
    fi
  fi
else
  echo "relay: curl not found — skipping HTTP checks" >&2
fi

if command -v nc >/dev/null 2>&1; then
  if nc -z -w 5 "$RELAY_HOST" "$RELAY_TCP_PORT" 2>/dev/null; then
    tcp_ok=1
    echo "relay: TCP ok (${RELAY_HOST}:${RELAY_TCP_PORT})"
  else
    echo "relay: TCP unreachable (${RELAY_HOST}:${RELAY_TCP_PORT})" >&2
  fi
elif python3 -c "import socket" 2>/dev/null; then
  if python3 - "$RELAY_HOST" "$RELAY_TCP_PORT" <<'PY'
import socket, sys
host, port = sys.argv[1], int(sys.argv[2])
s = socket.socket()
s.settimeout(5)
try:
    s.connect((host, port))
except OSError:
    raise SystemExit(1)
finally:
    s.close()
PY
  then
    tcp_ok=1
    echo "relay: TCP ok (${RELAY_HOST}:${RELAY_TCP_PORT})"
  else
    echo "relay: TCP unreachable (${RELAY_HOST}:${RELAY_TCP_PORT})" >&2
  fi
else
  echo "relay: neither nc nor python3 available — skipping TCP probe" >&2
fi

if [[ "$http_ok" -eq 1 && "$tcp_ok" -eq 1 ]]; then
  exit 0
fi
exit 1
