#!/usr/bin/env bash
# Scorched-earth wipe of all Mycena Android app data on a USB-connected device.
# Use when `adb install -r` preserved old contacts/identity across reinstall.
#
#   make android-nuke
#   ./scripts/nuke-android-data.sh
set -euo pipefail

PKG="${MYCENA_ANDROID_PACKAGE:-io.github.mycena_project.app}"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found — install Android platform-tools" >&2
  exit 1
fi

devices="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
count="$(echo "$devices" | grep -c . || true)"
if [[ "$count" -eq 0 ]]; then
  echo "No adb device in 'device' state." >&2
  adb devices
  exit 1
fi
if [[ "$count" -gt 1 ]]; then
  echo "Multiple devices — using first: $(echo "$devices" | head -1)" >&2
fi

echo "Stopping $PKG …"
adb shell am force-stop "$PKG" 2>/dev/null || true

echo "Clearing all app data for $PKG …"
adb shell pm clear "$PKG"

echo "Android data destroyed. Next: make android-deploy (or open app and complete onboarding)."
