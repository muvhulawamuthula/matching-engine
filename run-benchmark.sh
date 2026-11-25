#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
SECONDS_TO_RUN="${1:-10}"
"$DIR/build.sh" >/dev/null
java -jar "$DIR/dist/matching-engine.jar" benchmark "$SECONDS_TO_RUN"
