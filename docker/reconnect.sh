#!/bin/sh

devices=$(adb -P "$ADB_SERVER_PORT" devices) || exit 1
online=$(printf '%s\n' "$devices" | awk -F '\t' '$2 == "device" { print $1 }')

# Read ADB's own discovery snapshot. Only retry hosts known from native pairing.
timeout 2 adb -P "$ADB_SERVER_PORT" mdns track-services --proto-text |
    awk '
        /^tls \{/ { in_tls = 1; instance = ""; known = 0; next }
        !in_tls { next }
        $1 == "instance:" {
            instance = $0
            sub(/^[[:space:]]*instance: "/, "", instance)
            sub(/"$/, "", instance)
        }
        $1 == "known_device:" && $2 == "true" { known = 1 }
        /^}/ {
            if (known && instance != "" && !seen[instance]++) {
                print instance "._adb-tls-connect._tcp"
            }
            in_tls = 0
        }
    ' |
    while IFS= read -r target; do
        if ! printf '%s\n' "$online" | grep -Fxq "$target"; then
            adb -P "$ADB_SERVER_PORT" connect "$target" >/dev/null || :
        fi
    done
