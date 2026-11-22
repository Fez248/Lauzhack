#!/usr/bin/env bash
# Runs the local backend on http://localhost:8080 for testing, including from the live GitHub Pages app.
#   ./scripts/run-backend.sh
# - JAVA_HOME: kept if it is JDK 17+ (Gradle 9 needs it), else a JDK 21 found on the machine, else a 17+.
# - RATE_LIMITS=off unless already set.
# - ALLOWED_ORIGINS gets https://stygiansummer.github.io, so the live site can call this backend
#   (its config.json has no API, so it falls back to http://localhost:8080).
# Extra arguments go to Gradle.
set -euo pipefail
cd "$(dirname "$0")/.."
PAGES_ORIGIN="https://stygiansummer.github.io"

jdk_major() { # prints the major version of the JDK at $1, or 0
    local f="${1:-}/release"
    [ -n "${1:-}" ] && [ -f "$f" ] || { echo 0; return; }
    local v
    v="$(sed -n 's/^JAVA_VERSION="\([0-9]*\).*/\1/p' "$f" | head -n 1)"
    echo "${v:-0}"
}

if [ "$(jdk_major "${JAVA_HOME:-}")" -lt 17 ]; then
    best="" best_major=0
    if [ -x /usr/libexec/java_home ]; then # macOS
        best="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17+ 2>/dev/null || true)"
    else
        for d in /usr/lib/jvm/* "$HOME"/.jdks/* "$HOME"/.sdkman/candidates/java/* \
                 "/c/Program Files/Java"/* "/c/Program Files/Eclipse Adoptium"/* "/c/Program Files/Microsoft"/*; do
            [ -d "$d" ] || continue
            m="$(jdk_major "$d")"
            if [ "$m" -eq 21 ]; then best="$d"; break; fi
            if [ "$m" -ge 17 ] && [ "$m" -gt "$best_major" ]; then best="$d" best_major="$m"; fi
        done
    fi
    [ -n "$best" ] || { echo "No JDK 17+ found. Install JDK 21 and set JAVA_HOME to it." >&2; exit 1; }
    export JAVA_HOME="$best"
fi
echo "JAVA_HOME=$JAVA_HOME"

export RATE_LIMITS="${RATE_LIMITS:-off}"
case ",${ALLOWED_ORIGINS:-}," in
    *",$PAGES_ORIGIN,"*) ;;
    ",,") export ALLOWED_ORIGINS="$PAGES_ORIGIN" ;;
    *) export ALLOWED_ORIGINS="$ALLOWED_ORIGINS,$PAGES_ORIGIN" ;;
esac
echo "RATE_LIMITS=$RATE_LIMITS ALLOWED_ORIGINS=$ALLOWED_ORIGINS"

exec ./gradlew :server:run "$@"
