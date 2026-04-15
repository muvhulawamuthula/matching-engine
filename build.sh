
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
mkdir -p out dist
find src/main/java -name "*.java" | sort > sources_main.txt
javac -d out @sources_main.txt
jar --create --file dist/matching-engine.jar --main-class com.muvhulawa.matchingengine.Main -C out .
echo "Built dist/matching-engine.jar"
