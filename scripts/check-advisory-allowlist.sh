#!/usr/bin/env bash
# Ensure docs/DEPENDENCY_ADVISORY_ALLOWLIST.tsv rows are current (review_by not expired).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ALLOW="${ROOT}/docs/DEPENDENCY_ADVISORY_ALLOWLIST.tsv"
TODAY="$(date +%Y-%m-%d)"

if [[ ! -f "$ALLOW" ]]; then
  echo "Missing $ALLOW" >&2
  exit 1
fi

violations=0
while IFS=$'\t' read -r id crate version review_by notes; do
  [[ "$id" == "id" || -z "$id" || "$id" =~ ^# ]] && continue
  if [[ "$review_by" < "$TODAY" ]]; then
    echo "Advisory allowlist EXPIRED: $id ($crate $version) review_by=$review_by" >&2
    violations=1
  fi
done < "$ALLOW"

if [[ "$violations" -ne 0 ]]; then
  echo "Update docs/DEPENDENCY_ADVISORY_ALLOWLIST.tsv or remediate advisories." >&2
  exit 1
fi

echo "Advisory allowlist OK (review dates current)"
