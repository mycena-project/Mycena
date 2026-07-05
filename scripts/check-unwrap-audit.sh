#!/usr/bin/env bash
# Enforce unwrap-audit.csv: P0 peer-input modules stay panic-free; P2 expects need INVARIANT.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

violations=0

# Lines before #[cfg(test)] — production code in mixed unit-test modules.
prod_awk='/#\[cfg\(test\)\]/ || /#!\[cfg\(test\)\]/ { exit } { print NR ":" $0 }'

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

# P2: whitelisted expects — see docs/UNWRAP_INVARIANT_WHITELIST.tsv (scripts/check-invariant-whitelist.sh).
echo "check-unwrap-audit: P2 INVARIANT whitelist (delegated)..."
if ! ./scripts/check-invariant-whitelist.sh; then
  violations=1
fi

if [[ "$violations" -ne 0 ]]; then
  echo "Unwrap audit FAILED — see crates/mycelium-node/docs/unwrap-audit.csv and docs/UNWRAP_INVARIANT_WHITELIST.tsv" >&2
  exit 1
fi

echo "Unwrap audit OK (P0 strict=${#P0_STRICT[@]} P2 whitelist=docs/UNWRAP_INVARIANT_WHITELIST.tsv)"
