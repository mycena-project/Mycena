#!/usr/bin/env bash
# Dependency supply-chain gate: cargo-deny (licenses + advisories) and cargo-audit
# (fail on critical/high CVEs in the RustSec advisory DB).
#
#   ./scripts/check-dependencies.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export CARGO_HOME="${CARGO_HOME:-$ROOT/.cargo-home}"
export PATH="$CARGO_HOME/bin:$PATH"

DENY_VERSION="${CARGO_DENY_VERSION:-0.19.9}"
AUDIT_VERSION="${CARGO_AUDIT_VERSION:-0.22.2}"

ensure_tool() {
  local crate="$1" version="$2"
  if ! cargo "$crate" --version 2>/dev/null | grep -q "$version"; then
    echo "Installing cargo-$crate ${version}..." >&2
    cargo install "cargo-$crate" --locked --version "$version" --force
  fi
}

ensure_tool deny "$DENY_VERSION"
ensure_tool audit "$AUDIT_VERSION"

echo "check-dependencies: cargo deny (licenses + RustSec advisories)..."
cargo deny --all-features check advisories licenses bans sources

echo "check-dependencies: cargo audit (deny critical/high)..."
AUDIT_JSON="$(mktemp)"
trap 'rm -f "$AUDIT_JSON"' EXIT

set +e
cargo audit --json >"$AUDIT_JSON" 2>/dev/null
audit_status=$?
set -e

if [[ "$audit_status" -ne 0 ]]; then
  echo "cargo audit failed (exit $audit_status)" >&2
  cargo audit
  exit "$audit_status"
fi

python3 <<'PY' "$AUDIT_JSON" "$ROOT/docs/DEPENDENCY_ADVISORY_ALLOWLIST.tsv"
import json
import sys
from datetime import date

path, allowlist_path = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as f:
    data = json.load(f)

allowed_ids = set()
with open(allowlist_path, encoding="utf-8") as f:
    for line in f:
        line = line.strip()
        if not line or line.startswith("#") or line.startswith("id\t"):
            continue
        allowed_ids.add(line.split("\t", 1)[0])

fail = []
for item in data.get("vulnerabilities", {}).get("list", []):
    adv = item.get("advisory") or {}
    rid = adv.get("id", "?")
    if rid in allowed_ids:
        continue
    sev = (adv.get("severity") or "").lower()
    categories = {c.lower() for c in (adv.get("categories") or [])}
    cvss = adv.get("cvss")
    score = None
    if isinstance(cvss, dict):
        score = cvss.get("score")

    pkg = item.get("package") or {}
    name = pkg.get("name", "?")
    version = pkg.get("version", "?")
    rid = adv.get("id", "?")
    title = adv.get("title", "")

    high_categories = {
        "denial-of-service",
        "memory-corruption",
        "remote-code-execution",
        "code-execution",
        "format-injection",
    }

    is_high = sev in ("critical", "high")
    if not is_high and categories & high_categories:
        is_high = True
    if not is_high and score is not None:
        try:
            is_high = float(score) >= 7.0
        except (TypeError, ValueError):
            pass

    if is_high:
        tag = sev or ("category:" + ",".join(sorted(categories & high_categories)) if categories else "high")
        fail.append(f"  {rid} [{tag}] {name} {version}: {title}")

if fail:
    print("Dependency audit FAILED — unallowlisted critical/high advisories:", file=sys.stderr)
    print("\n".join(fail), file=sys.stderr)
    sys.exit(1)

print("Dependency audit OK (no unallowlisted critical/high advisories)")
PY

./scripts/check-advisory-allowlist.sh

echo "check-dependencies: OK"
