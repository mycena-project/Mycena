#!/usr/bin/env bash
# Generate CycloneDX SBOM for the workspace (release artifact).
#
# Primary SBOM: mycelium-node (mesh runtime + transitive Rust deps for mobile/desktop).
#
#   ./scripts/generate-sbom.sh [TAG] [OUT_DIR]
#   -> OUT_DIR/mycelium-sbom-TAG.cdx.json
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

TAG="${1:-$(git describe --tags --always 2>/dev/null || echo dev)}"
OUT_DIR="${2:-target/sbom}"
CYCLONEDX_VERSION="${CARGO_CYCLONEDX_VERSION:-0.5.7}"
MANIFEST="crates/mycelium-node/Cargo.toml"

export CARGO_HOME="${CARGO_HOME:-$ROOT/.cargo-home}"
export PATH="$CARGO_HOME/bin:$PATH"

if ! cargo cyclonedx --version 2>/dev/null | grep -q "$CYCLONEDX_VERSION"; then
  echo "Installing cargo-cyclonedx ${CYCLONEDX_VERSION}..." >&2
  cargo install cargo-cyclonedx --locked --version "$CYCLONEDX_VERSION" --force
fi

mkdir -p "$OUT_DIR"
OUT_FILE="$OUT_DIR/mycelium-sbom-${TAG}.cdx.json"
STAGING="crates/mycelium-node/mycelium-sbom-${TAG}.json"

cargo cyclonedx \
  --manifest-path "$MANIFEST" \
  --format json \
  -a \
  --all-features \
  --override-filename "mycelium-sbom-${TAG}" \
  --spec-version 1.5

if [[ ! -f "$STAGING" ]]; then
  echo "SBOM generation failed: expected $STAGING" >&2
  exit 1
fi

mv "$STAGING" "$OUT_FILE"

cat >"$OUT_DIR/sbom-manifest.json" <<EOF
{
  "format": "CycloneDX",
  "spec_version": "1.5",
  "release_tag": "$TAG",
  "sbom_file": "$(basename "$OUT_FILE")",
  "root_crate": "mycelium-node",
  "generated_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "generator": "cargo-cyclonedx ${CYCLONEDX_VERSION}"
}
EOF

echo "SBOM: $OUT_FILE"
echo "Manifest: $OUT_DIR/sbom-manifest.json"
