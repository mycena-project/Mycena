#!/bin/bash -eu
# OSS-Fuzz build script — copied to projects/mycena/build.sh in google/oss-fuzz.
# Builds libFuzzer binaries from the in-repo cargo-fuzz workspace.

cd "$SRC/mycena/fuzz"

export RUSTFLAGS="${RUSTFLAGS:-} --cfg fuzzing"

TARGETS=(
  fuzz_envelope
  fuzz_ble_frame
  fuzz_coin_payload
  fuzz_custody_envelope
)

for target in "${TARGETS[@]}"; do
  cargo fuzz build --release "$target" --target x86_64-unknown-linux-gnu
  cp "target/x86_64-unknown-linux-gnu/release/${target}" "$OUT/"
done

# Seed corpora when present (speeds up cluster warm-up).
for target in "${TARGETS[@]}"; do
  if [ -d "corpus/${target}" ]; then
    zip -j "$OUT/${target}_seed_corpus.zip" "corpus/${target}/"* || true
  fi
done
