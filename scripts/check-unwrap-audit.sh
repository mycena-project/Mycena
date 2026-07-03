#!/usr/bin/env bash
# Enforce unwrap-audit.csv: P0 peer-input modules stay panic-free; P2 expects need INVARIANT.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

violations=0

# Lines before #[cfg(test)] — production code in mixed unit-test modules.
prod_awk='/#\[cfg\(test\)\]/ { exit } { print NR ":" $0 }'

# P0: decode/parse/verify paths — no .unwrap( / .expect( on untrusted input.
P0_STRICT=(
  crates/mycelium-core/src/ble_mesh.rs
  crates/mycelium-core/src/ble_stream.rs
  crates/mycelium-core/src/contact_hello.rs
  crates/mycelium-core/src/invite.rs
  crates/mycelium-core/src/crypto/mod.rs
  crates/mycelium-core/src/data.rs
  crates/mycelium-node/src/security.rs
  crates/mycelium-node/src/ingest_guard.rs
  crates/mycelium-coin/src/node.rs
  crates/mycelium-coin/src/payload.rs
)

is_panic_call() {
  [[ "$1" =~ \.(unwrap|expect)\( ]] && [[ ! "$1" =~ unwrap_or ]]
}

echo "check-unwrap-audit: P0 peer-input modules (no unwrap/expect in prod)..."
for f in "${P0_STRICT[@]}"; do
  if [[ ! -f "$f" ]]; then
    echo "check-unwrap-audit: missing $f" >&2
    violations=1
    continue
  fi
  while IFS= read -r line; do
    num="${line%%:*}"
    text="${line#*:}"
    if is_panic_call "$text"; then
      echo "P0 violation: $f:$num: $text"
      violations=1
    fi
  done < <(awk "$prod_awk" "$f")
done

# P2: local-state expects must cite INVARIANT on same line or up to 3 lines above.
P2_INVARIANT=(
  crates/mycelium-node/src/node.rs
  crates/mycelium-node/src/secrets.rs
  crates/mycelium-node/src/behaviour.rs
  crates/mycelium-core/src/relay_pool.rs
  crates/mycelium-core/src/rendezvous_dial.rs
  crates/mycelium-core/src/ble_mesh.rs
  crates/mycelium-core/src/crypto/ratchet.rs
)

echo "check-unwrap-audit: P2 INVARIANT comments on prod unwrap/expect..."
for f in "${P2_INVARIANT[@]}"; do
  if [[ ! -f "$f" ]]; then
    echo "check-unwrap-audit: missing $f" >&2
    violations=1
    continue
  fi
  prev3="" prev2="" prev1=""
  while IFS= read -r line; do
    num="${line%%:*}"
    text="${line#*:}"
    if is_panic_call "$text"; then
      if [[ "$text" != *"INVARIANT:"* && "$prev1" != *"INVARIANT:"* && "$prev2" != *"INVARIANT:"* && "$prev3" != *"INVARIANT:"* ]]; then
        echo "P2 violation: $f:$num: missing INVARIANT near: $text"
        violations=1
      fi
    fi
    prev3=$prev2
    prev2=$prev1
    prev1=$text
  done < <(awk "$prod_awk" "$f")
done

if [[ "$violations" -ne 0 ]]; then
  echo "Unwrap audit FAILED — see crates/mycelium-node/docs/unwrap-audit.csv" >&2
  exit 1
fi

echo "Unwrap audit OK (P0 strict=${#P0_STRICT[@]} P2 invariant=${#P2_INVARIANT[@]})"
