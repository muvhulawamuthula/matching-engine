# Getting started

Run the matching engine locally with either the included shell scripts (no Maven required) or Maven.

## Prerequisites

- **JDK 17+** (`java -version`, `javac -version`)
- Optional: **Maven 3.8+** for `mvn test` / `mvn package`
- Optional: `jar` tool (ships with the JDK) for the runnable JAR produced by `build.sh`

Clone or download the repository, then work from the repo root.

## Build (no Maven)

```bash
chmod +x build.sh run-demo.sh run-benchmark.sh   # once, if needed
./build.sh
```

This:

1. Compiles `src/main/java/**/*.java` into `out/`
2. Packages `dist/matching-engine.jar` with main class `com.muvhulawa.matchingengine.Main`

Equivalent manual steps:

```bash
mkdir -p out dist
find src/main/java -name "*.java" | sort > sources_main.txt
javac -d out @sources_main.txt
jar --create --file dist/matching-engine.jar \
  --main-class com.muvhulawa.matchingengine.Main -C out .
```

## Run the deterministic demo

```bash
./run-demo.sh
# or
java -jar dist/matching-engine.jar demo
# or
java -cp out com.muvhulawa.matchingengine.Main demo
```

Demo sequence (from `Main.runDeterministicDemo`):

1. Limit sell `id=1` @ `100.00` qty `5`
2. Limit sell `id=2` @ `100.00` qty `7`
3. Market buy `id=3` qty `6` → trades against both sells (passive prices)
4. Limit buy `id=4` @ `99.50` qty `3` → rests
5. Cancel `id=5` targeting order `4` → removes the resting buy

Expect printed trades/reports, a book snapshot, and a latency summary (demo timing may show `no samples` if nothing went through the worker path — demo calls the engine directly).

## Run the benchmark

```bash
./run-benchmark.sh          # 10 seconds (script default)
./run-benchmark.sh 15       # 15 seconds
java -jar dist/matching-engine.jar benchmark 15
```

Note: if you invoke `Main` with only `benchmark` and **no** seconds argument, the Java entrypoint defaults to **15** seconds. The shell helper defaults to **10**.

Benchmark wiring:

- Ring buffer capacity `8192`
- Order pool size `65536`
- `4` producer threads + `1` matching worker
- Workload mix ≈ limit / market / cancel via xor-shift PRNG

On completion you should see counters, commands/sec, latency percentiles, top-of-book, and:

```text
build/latest-snapshot.txt
```

## Maven workflow

```bash
mvn -q test          # JUnit 5: engine, order book, ring buffer, snapshot writer
mvn -q package       # jar under target/ with Main-Class manifest
java -jar target/matching-engine-1.0-SNAPSHOT.jar demo
```

`pom.xml` targets Java 17. The only dependency is `junit-jupiter` (test scope).

## Suggested first hour

1. Run `./run-demo.sh` and read the execution reports.
2. Skim [`ARCHITECTURE.md`](ARCHITECTURE.md) for the component diagram.
3. Open `queue/RingBuffer.java`, then `engine/MatchingWorker.java`, then `orderbook/OrderBook.java`, then `engine/MatchingEngine.java`.
4. Run `./run-benchmark.sh 5` and inspect `build/latest-snapshot.txt`.
5. Follow the narrative in [`PROJECT-WALKTHROUGH.md`](PROJECT-WALKTHROUGH.md).

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `Size must be power of 2` | `RingBuffer` constructed with non–power-of-two capacity |
| Benchmark hangs on exit | Worker should drain; ensure you are on current `MatchingWorker` (`running \|\| !buffer.isEmpty()`) |
| `no samples` latency in demo | Expected — demo bypasses the worker’s timer |
| Maven compiler errors | Confirm JDK 17+ is on `PATH` and `JAVA_HOME` |
| Full ring / producer spin | Normal under overload; buffer is bounded by design |

## Next reading

- [Architecture](ARCHITECTURE.md) — invariants and component ownership
- [Project walkthrough](PROJECT-WALKTHROUGH.md) — guided code tour
- Root [README](../README.md) — portfolio overview and matching rules
