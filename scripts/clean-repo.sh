#!/usr/bin/env bash
# Remove regenerable build artifacts from the Mycelium workspace.
#
# Usage:
#   ./scripts/clean-repo.sh           # Rust + Android + APK junk (~13+ GB typical)
#   ./scripts/clean-repo.sh --deep    # also node_modules (desktop UI)
#   ./scripts/clean-repo.sh --docker  # also prune named Docker build volumes (outside repo)
#   ./scripts/clean-repo.sh --dry-run # print what would be removed
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

DEEP=0
DOCKER=0
DRY=0
for arg in "$@"; do
  case "$arg" in
    --deep) DEEP=1 ;;
    --docker) DOCKER=1 ;;
    --dry-run) DRY=1 ;;
    -h|--help)
      sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

before_kb="$(du -sk "$ROOT" | awk '{print $1}')"

rm_path() {
  local p="$1"
  [[ -e "$p" ]] || return 0
  if [[ "$DRY" -eq 1 ]]; then
    du -sh "$p" 2>/dev/null || echo "(missing) $p"
    return 0
  fi
  rm -rf "$p"
}

echo "Cleaning Mycelium workspace at $ROOT …"

# Local debug captures (LAN test logs)
rm_path "$ROOT/.debug-logs"

# Rust
rm_path "$ROOT/target"
rm_path "$ROOT/target-docker"
rm_path "$ROOT/.cargo-home"

# Android / Gradle
rm_path "$ROOT/android/.gradle"
rm_path "$ROOT/android/rust-build"
rm_path "$ROOT/android/app/build"
rm_path "$ROOT/android/uniffi-bindings/build"
if [[ "$DRY" -eq 1 ]]; then
  find "$ROOT/android" -type d -name build 2>/dev/null | while read -r d; do du -sh "$d"; done
else
  find "$ROOT/android" -type d -name build -prune -exec rm -rf {} + 2>/dev/null || true
fi

# Accidental APK unzip at repo root (safe to delete; listed in .gitignore)
for item in \
  AndroidManifest.xml resources.arsc DebugProbesKt.bin \
  META-INF assets res kotlin lib okhttp3 com \
  mycelium-serve.sh; do
  rm_path "$ROOT/$item"
done
for dex in "$ROOT"/classes*.dex; do
  [[ -e "$dex" ]] || continue
  rm_path "$dex"
done

# Tauri / desktop UI caches (regenerated on dev/build)
rm_path "$ROOT/crates/mycelium-desktop/ui/dist"
rm_path "$ROOT/crates/mycelium-desktop/ui/.svelte-kit"
rm_path "$ROOT/crates/mycelium-desktop/gen/schemas"
rm_path "$ROOT/crates/mycelium-desktop/gen"

# Stale duplicate UniFFI output
rm_path "$ROOT/android/uniffi-bindings/src/main/kotlin/uniffi/mycelium/uniffi"

# Android cross-compile artifacts (also removed with target/, but explicit if target partial)
for triple in aarch64-linux-android armv7-linux-androideabi x86_64-linux-android; do
  rm_path "$ROOT/target/$triple"
done

if [[ "$DEEP" -eq 1 ]]; then
  rm_path "$ROOT/crates/mycelium-desktop/ui/node_modules"
fi

if [[ "$DOCKER" -eq 1 ]] && command -v docker >/dev/null 2>&1; then
  for vol in \
    mycelium-android-cargo-registry \
    mycelium-android-cargo-git \
    mycelium-android-target \
    mycelium-android-gradle \
    mycelium-mingc-cargo \
    mycelium-mingc-rustup \
    mycelium-mingc-target; do
    if docker volume inspect "$vol" >/dev/null 2>&1; then
      if [[ "$DRY" -eq 1 ]]; then
        echo "docker volume: $vol"
      else
        docker volume rm "$vol" >/dev/null 2>&1 && echo "Removed docker volume: $vol" || true
      fi
    fi
  done
fi

if [[ "$DRY" -eq 0 ]] && command -v cargo >/dev/null 2>&1; then
  # Ensures any stray per-crate target dirs are gone too.
  cargo clean >/dev/null 2>&1 || true
fi

after_kb="$(du -sk "$ROOT" | awk '{print $1}')"
freed_mb=$(( (before_kb - after_kb) / 1024 ))
echo ""
if [[ "$DRY" -eq 1 ]]; then
  echo "Dry run complete (no files removed)."
else
  echo "Done. Freed ~${freed_mb} MiB."
  echo "Workspace size now: $(du -sh "$ROOT" | awk '{print $1}')"
  echo ""
  echo "Rebuild when needed:"
  echo "  Desktop:  make desktop-dev"
  echo "  Android:  make android-apk   # Docker (mingc/android-build-box)"
  if [[ "$DEEP" -eq 0 ]]; then
    echo "  (Re-run with --deep to also remove desktop UI node_modules.)"
  fi
  if [[ "$DOCKER" -eq 0 ]] && command -v docker >/dev/null 2>&1; then
    echo "  (Re-run with --docker to prune Docker build cache volumes — not in repo size.)"
  fi
fi
