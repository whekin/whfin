#!/bin/sh
set -eu

properties_file=${1:?"usage: verify-release-signing.sh /path/to/release.properties"}

if [ ! -f "$properties_file" ]; then
    echo "Missing release signing properties: $properties_file. See docs/release-signing.md." >&2
    exit 1
fi

for property_name in storeFile storePassword keyAlias keyPassword; do
    property_value=$(sed -n "s/^${property_name}=//p" "$properties_file")
    if [ -z "$property_value" ]; then
        echo "Missing release signing property: $property_name. See docs/release-signing.md." >&2
        exit 1
    fi
done

store_file=$(sed -n 's/^storeFile=//p' "$properties_file")
if [ ! -f "$store_file" ]; then
    echo "Release keystore does not exist. See docs/release-signing.md." >&2
    exit 1
fi
