#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
"$DIR/build.sh" >/dev/null
java -jar "$DIR/dist/matching-engine.jar" demo
