#!/usr/bin/env bash

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

failed=0

report_files() {
    local label="$1"
    local files="$2"
    if [[ -n "$files" ]]; then
        printf 'ERROR: %s\n%s\n' "$label" "$files" >&2
        failed=1
    fi
}

secret_files="$({
    git grep -Il -E -- '-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{30,}|gh[pousr]_[0-9A-Za-z]{20,}|github_pat_[0-9A-Za-z_]{20,}|sk-[0-9A-Za-z_-]{20,}|xox[baprs]-[0-9A-Za-z-]{10,}' || true
    git grep -Il -E -- '(api[_-]?key|client[_-]?secret|access[_-]?token|refresh[_-]?token|password)[[:space:]]*[:=][[:space:]]*[^$<{[:space:]]' || true
} | sort -u)"
report_files "secret-shaped content is tracked" "$secret_files"

path_files="$(git grep -Il -E -- '/Users/[^/]+|/home/[^/]+|/var/folders/|[A-Z]:\\Users\\' -- ':!scripts/check-public-tree.sh' || true)"
report_files "an absolute user path is tracked" "$path_files"

email_files="$(git grep -Il -E -- '[A-Za-z0-9._%+-]+@(gmail|icloud|outlook|protonmail|yahoo|mail|yandex)\.[A-Za-z]{2,}' || true)"
report_files "a personal email address is tracked" "$email_files"

sensitive_paths="$(git ls-files | grep -Ei '(^|/)(local|private|artifacts|backups|captures)/|\.(xlsx|xls|csv|tsv|ofx|qif|sql|dump|bundle|db|sqlite|sqlite3|db-wal|db-shm|pb|keystore|jks|p12|pem|key|mobileprovision|aab|apk|apks|idsig|hprof|heapdump|log)$' || true)"
report_files "a private or generated artifact is tracked" "$sensitive_paths"

real_ibans="$(git grep -ho -E -- '[A-Z]{2}[0-9]{2}[A-Z]{2}[0-9]{16}' | sort -u | grep -Ev '^[A-Z]{2}00' || true)"
if [[ -n "$real_ibans" ]]; then
    printf 'ERROR: real-looking IBAN literals are tracked; committed fixtures must use checksum 00.\n' >&2
    failed=1
fi

real_cards="$(git grep -ho -E -- '(\*{3,}|•{1,2})[0-9]{4}' | grep -Eo '[0-9]{4}$' | sort -u | grep -Ev '^000[0-9]$' || true)"
if [[ -n "$real_cards" ]]; then
    printf 'ERROR: real-looking masked-card literals are tracked; committed fixtures must use 000x.\n' >&2
    failed=1
fi

if (( failed )); then
    exit 1
fi

echo "Public-tree privacy checks passed."
