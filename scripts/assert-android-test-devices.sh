#!/usr/bin/env bash

set -euo pipefail

if [[ -n "${WHFIN_ADB_DEVICES_FILE:-}" ]]; then
    devices="$(<"$WHFIN_ADB_DEVICES_FILE")"
else
    devices="$(adb devices -l)"
fi

listed_serials="$(printf '%s\n' "$devices" | awk 'NR > 1 && NF >= 2 { print $1 }')"
physical_serials="$(printf '%s\n' "$listed_serials" | awk '$0 !~ /^emulator-[0-9]+$/')"
if [[ -n "$physical_serials" ]]; then
    echo 'ERROR: refusing connected Android tests while a physical device is attached or known to ADB.' >&2
    echo 'Physical serial(s):' >&2
    printf '  %s\n' $physical_serials >&2
    echo 'Disconnect the phone and retry with a disposable emulator as the only online device.' >&2
    exit 1
fi

online_serials="$(printf '%s\n' "$devices" | awk 'NR > 1 && $2 == "device" { print $1 }')"
if [[ -z "$online_serials" ]]; then
    echo 'ERROR: connected Android tests require a disposable emulator, but none is online.' >&2
    exit 1
fi

echo "Android test target guard passed: $online_serials"
