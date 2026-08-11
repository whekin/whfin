#!/usr/bin/env bash

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

fixture_dir="$(mktemp -d)"
trap 'rm -rf "$fixture_dir"' EXIT

emulator_only="$fixture_dir/emulator.txt"
physical_only="$fixture_dir/physical.txt"
mixed="$fixture_dir/mixed.txt"
offline_physical="$fixture_dir/offline-physical.txt"

printf '%s\n' \
    'List of devices attached' \
    'emulator-5554 device product:sdk_gphone model:sdk_gphone device:emu64a' \
    > "$emulator_only"
printf '%s\n' \
    'List of devices attached' \
    'physical-serial device product:phone model:phone device:phone' \
    > "$physical_only"
printf '%s\n' \
    'List of devices attached' \
    'emulator-5554 device product:sdk_gphone model:sdk_gphone device:emu64a' \
    'physical-serial device product:phone model:phone device:phone' \
    > "$mixed"
printf '%s\n' \
    'List of devices attached' \
    'emulator-5554 device product:sdk_gphone model:sdk_gphone device:emu64a' \
    'physical-serial offline product:phone model:phone device:phone' \
    > "$offline_physical"

WHFIN_ADB_DEVICES_FILE="$emulator_only" scripts/assert-android-test-devices.sh

if WHFIN_ADB_DEVICES_FILE="$physical_only" scripts/assert-android-test-devices.sh; then
    echo 'Expected a physical-only target set to be rejected.' >&2
    exit 1
fi
if WHFIN_ADB_DEVICES_FILE="$mixed" scripts/assert-android-test-devices.sh; then
    echo 'Expected a mixed emulator/physical target set to be rejected.' >&2
    exit 1
fi

if WHFIN_ADB_DEVICES_FILE="$offline_physical" scripts/assert-android-test-devices.sh; then
    echo 'Expected an offline physical device to be rejected fail-closed.' >&2
    exit 1
fi

echo 'Android test device guard checks passed.'
