#!/usr/bin/env bash
# Validate production unwrap/expect against docs/UNWRAP_INVARIANT_WHITELIST.tsv.
# - Every prod .unwrap() / .expect() in security crates must be whitelisted + cite INVARIANT.
# - Every whitelist row must match a live call site (line drift ±3).
# - Prod .unwrap() counts must not exceed documented baselines (regression gate).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

WHITELIST="${ROOT}/docs/UNWRAP_INVARIANT_WHITELIST.tsv"
CRATES=(mycena-node mycena-core mycena-coin mycena-miniapp)

# Max production .unwrap( calls per crate (expect uses whitelist rows instead).
unwrap_baseline() {
  case "$1" in
    mycena-node) echo 0 ;;
    mycena-core) echo 0 ;;
    mycena-coin) echo 0 ;;
    mycena-miniapp) echo 0 ;;
    *) echo 0 ;;
  esac
}

violations=0
prod_awk='/#\[cfg\(test\)\]/ || /#!\[cfg\(test\)\]/ { exit } { print NR ":" $0 }'

is_panic_call() {
  [[ "$1" =~ \.(unwrap|expect)\( ]] && [[ ! "$1" =~ unwrap_or ]]
}

has_invariant_near() {
  local text="$1" prev1="$2" prev2="$3" prev3="$4"
  [[ "$text" == *"INVARIANT:"* || "$prev1" == *"INVARIANT:"* || "$prev2" == *"INVARIANT:"* || "$prev3" == *"INVARIANT:"* ]]
}

WHITELIST_KEYS=()

echo "check-invariant-whitelist: scan production unwrap/expect in security crates..."
for crate in "${CRATES[@]}"; do
  unwrap_count=0
  while IFS= read -r rsfile; do
    [[ -f "$rsfile" ]] || continue
    prev3="" prev2="" prev1=""
    while IFS= read -r line; do
      num="${line%%:*}"
      text="${line#*:}"
      if [[ "$text" =~ \.unwrap\( ]] && [[ ! "$text" =~ unwrap_or ]]; then
        ((unwrap_count++)) || true
        if ! has_invariant_near "$text" "$prev1" "$prev2" "$prev3"; then
          echo "prod unwrap without INVARIANT: $rsfile:$num: $text"
          violations=1
        fi
      fi
      if [[ "$text" =~ \.expect\( ]]; then
        if ! has_invariant_near "$text" "$prev1" "$prev2" "$prev3"; then
          echo "prod expect without INVARIANT: $rsfile:$num: $text"
          violations=1
        fi
      fi
      prev3=$prev2
      prev2=$prev1
      prev1=$text
    done < <(awk "$prod_awk" "$rsfile")
  done < <(find "crates/$crate/src" -name '*.rs' -print | sort)

  baseline="$(unwrap_baseline "$crate")"
  if [[ "$unwrap_count" -gt "$baseline" ]]; then
    echo "unwrap regression: $crate prod .unwrap() count $unwrap_count > baseline $baseline"
    violations=1
  else
    echo "  $crate: prod .unwrap()=$unwrap_count (baseline ≤$baseline)"
  fi
done

echo "check-invariant-whitelist: validate whitelist rows..."
while IFS=$'\t' read -r crate file line kind summary; do
  [[ -z "${crate:-}" || "$crate" == \#* || "$crate" == "crate" ]] && continue
  key="$file:$line"
  WHITELIST_KEYS+=("$key")
  if [[ ! -f "$ROOT/$file" ]]; then
    echo "whitelist missing file: $file (crate=$crate)"
    violations=1
    continue
  fi
  matched=0
  for delta in -3 -2 -1 0 1 2 3; do
    target=$((line + delta))
    row="$(awk -F: -v n="$target" '$1 == n { print; exit }' <(awk "$prod_awk" "$ROOT/$file"))"
    [[ -z "$row" ]] && continue
    text="${row#*:}"
    if [[ "$kind" == "unwrap" && "$text" =~ \.unwrap\( ]] && [[ ! "$text" =~ unwrap_or ]]; then
      matched=1
      break
    fi
    if [[ "$kind" == "expect" && "$text" =~ \.expect\( ]]; then
      matched=1
      break
    fi
  done
  if [[ "$matched" -ne 1 ]]; then
    echo "whitelist stale or unmatched: $file:$line ($kind) — $summary"
    violations=1
  fi
done < "$WHITELIST"

echo "check-invariant-whitelist: every prod expect must be whitelisted..."
for crate in mycena-node mycena-core mycena-coin mycena-miniapp; do
  while IFS= read -r rsfile; do
    [[ -f "$rsfile" ]] || continue
    while IFS= read -r line; do
      num="${line%%:*}"
      text="${line#*:}"
      [[ "$text" =~ \.expect\( ]] || continue
      matched=0
      for delta in -3 -2 -1 0 1 2 3; do
        key="${rsfile#"$ROOT/"}:$((num + delta))"
        for wkey in "${WHITELIST_KEYS[@]}"; do
          if [[ "$wkey" == "$key" ]]; then
            matched=1
            break
          fi
        done
        [[ "$matched" -eq 1 ]] && break
      done
      if [[ "$matched" -ne 1 ]]; then
        echo "prod expect not whitelisted: ${rsfile#"$ROOT/"}:$num: $text"
        violations=1
      fi
    done < <(awk "$prod_awk" "$rsfile")
  done < <(find "crates/$crate/src" -name '*.rs' -print | sort)
done

if [[ "$violations" -ne 0 ]]; then
  echo "INVARIANT whitelist check FAILED — see docs/UNWRAP_INVARIANT_WHITELIST.tsv" >&2
  exit 1
fi

whitelist_rows="$(grep -cve '^\s*$' -e '^\s*#' "$WHITELIST" || true)"
echo "INVARIANT whitelist OK ($whitelist_rows whitelisted expect sites, unwrap baselines enforced)"
