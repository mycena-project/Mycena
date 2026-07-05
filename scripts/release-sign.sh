#!/usr/bin/env bash
# Sign a release integrity manifest (M-of-N release signers).
#
#   ./scripts/release-sign.sh release-integrity.json release-signer-eu
#
# Uses deterministic dev keys from mycelium_core::release_integrity (production:
# offline HSM / air-gapped keys held by independent signers).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="${1:?usage: release-sign.sh MANIFEST.json SIGNER_ID}"
SIGNER_ID="${2:?usage: release-sign.sh MANIFEST.json SIGNER_ID}"

cargo run -q -p mycelium-cli -- release sign "$MANIFEST" --signer-id "$SIGNER_ID"
echo "Updated $MANIFEST with signature from $SIGNER_ID"
