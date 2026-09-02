# Matching Engine

**Low-latency order matching engine in Java** — multi-producer ring-buffer ingress, deterministic single-threaded matching, price-time priority, and first-class observability.

Built as a systems-oriented portfolio piece: concurrency at the edge, correctness at the core.

```
┌──────────────┐   publish    ┌─────────────────┐   consume   ┌──────────────────┐
│ Producers ×N │ ───────────► │ MPSC RingBuffer │ ──────────► │ MatchingWorker   │
│ (limit /     │              │ (bounded, CAS)  │             │ → MatchingEngine │
│  market /    │              │                 │             │ → OrderBook      │
│  cancel)     │              │                 │             │ → EventLog       │
└──────────────┘              └─────────────────┘             └──────────────────┘
```

## Value proposition

Most toy matching engines either ignore concurrency or lock the book on every update. This project separates those concerns deliberately:

| Concern | Approach |
|---|---|
| **Ingress scalability** | Bounded MPSC ring buffer with atomic sequence claiming |
| **Matching correctness** | Single worker mutates the book — no locks in the hot path |
| **Determinism** | Price-time priority; trade price = resting (passive) price |
| **Financial hygiene** | Prices as integer ticks, never `double` comparisons |
| **Observability** | Trades, execution reports, counters, sampled latency percentiles |
| **GC pressure** | Reusable `Order` pool on the producer/engine path |

Use it to demonstrate concurrency design, market-microstructure modeling, and production-minded Java — not another CRUD wrapper.

## Features

- **Order types:** `LIMIT`, `MARKET`, `CANCEL`
- **Price-time priority:** better prices first; FIFO within a price level (`TreeMap` + `LinkedHashMap`)
- **MPSC ingress:** power-of-two ring buffer (`8192` slots by default)
- **Clean shutdown:** worker drains remaining commands before exit
- **Events:** `Trade` + `ExecutionReport` lifecycle records
- **Snapshots:** top-of-book export to `build/latest-snapshot.txt`
- **Benchmark mode:** 4 producers generating a mixed workload
- **Demo mode:** fixed sequence for predictable walkthroughs
- **Tests:** JUnit 5 coverage for engine, book, ring buffer, and snapshots
- **Zero runtime deps:** Java 17; Maven optional

## Architecture (short)

1. **Producers** borrow `Order` objects from `OrderPool`, fill LIMIT / MARKET / CANCEL commands, and `publish` into `RingBuffer`.
2. **`MatchingWorker`** is the sole consumer. It times each `engine.process(...)` call and records latency samples.
3. **`MatchingEngine`** validates the command, matches against the opposite side, emits trades/reports, and either rests residual limit quantity or rejects market residuals.
4. **`OrderBook`** keeps bids descending / asks ascending, with an ID index for O(1)-ish cancel lookup.
5. **`SnapshotWriter`** dumps book + metrics + recent trades after a benchmark run.

Deeper design notes: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) · Guided tour: [`docs/PROJECT-WALKTHROUGH.md`](docs/PROJECT-WALKTHROUGH.md)

## Order types & matching rules

### Limit
Crosses the opposite side when prices allow. Residual quantity **rests** on the book.

### Market
Takes liquidity at best available prices. Residual quantity is **never** rested — rejected as `"Unfilled market residual cancelled"`.

### Cancel
Removes a resting order by target ID. Misses increment `cancelMisses` and produce a reject report.

### Priority & trade price
- Across levels: best bid (highest) / best ask (lowest) first
- Within a level: older orders fill before newer (`LinkedHashMap` insertion order)
- Executed price = **passive** (resting) order’s price ticks

### Price ticks
`PriceUtil` uses scale `2` (`TICKS_PER_UNIT = 100`). Example: `100.25` → `10025`. All matching comparisons use `long` ticks.

## Build & run

**Requirements:** JDK 17+

### Fast path (no Maven)

```bash
./build.sh                 # compiles to out/ and packs dist/matching-engine.jar
./run-demo.sh              # deterministic demo
./run-benchmark.sh 10      # 10-second mixed-load benchmark
```

### Manual compile

```bash
find src/main/java -name "*.java" | sort > sources_main.txt
javac -d out @sources_main.txt
java -cp out com.muvhulawa.matchingengine.Main demo
java -cp out com.muvhulawa.matchingengine.Main benchmark 10
```

### Maven (optional)

```bash
mvn -q test
mvn -q package
java -jar target/matching-engine-1.0-SNAPSHOT.jar demo
```

Full setup notes: [`docs/GETTING-STARTED.md`](docs/GETTING-STARTED.md)

## CLI

```text
java -jar dist/matching-engine.jar demo
java -jar dist/matching-engine.jar benchmark <seconds>
```

| Mode | Behavior |
|---|---|
| `demo` | Fixed sequence: two sells @ 100.00, market buy 6, resting buy @ 99.50, cancel that buy |
| `benchmark [N]` | 4 producers + 1 worker for `N` seconds (default **15** if omitted; `run-benchmark.sh` defaults to **10**) |

Benchmark mix (approx.): ~75% limit, ~19% market, ~6% cancel — xor-shift PRNG per producer.

### What you get from a benchmark

- Processed / accepted / cancelled / rejected counts
- Trade count and traded quantity
- Commands/sec
- Sampled latency `p50` / `p95` / `p99` (µs)
- Top-of-book snapshot
- File write: `build/latest-snapshot.txt`

## Project layout

```text
src/main/java/com/muvhulawa/matchingengine/
├── Main.java                 # demo + benchmark entry
├── engine/
│   ├── MatchingEngine.java   # limit / market / cancel + match loop
│   └── MatchingWorker.java   # ring consumer, latency timing, drain-on-stop
├── orderbook/
│   ├── OrderBook.java        # price-time book + cancel index
│   └── OrderBookSnapshot.java
├── queue/
│   ├── RingBuffer.java       # bounded MPSC (used in production path)
│   └── OrderQueue.java       # ArrayBlockingQueue alternative (unused by Main)
├── model/Order.java          # command object + lifecycle status
├── pool/OrderPool.java       # reusable Order instances
├── events/                   # Trade, ExecutionReport, EventLog
├── metrics/                  # counters + LatencyRecorder
├── snapshot/SnapshotWriter.java
└── util/PriceUtil.java       # tick conversion / formatting
```

## Design notes (accurate to this codebase)

- **Single-writer book:** only `MatchingWorker` → `MatchingEngine` mutates state. Producers never touch the book.
- **Backpressure:** `RingBuffer.publish` returns `false` when full; producers spin (`Thread.onSpinWait`) or release the pooled order on shutdown.
- **Object reuse:** `OrderPool` is an `ArrayBlockingQueue` of pre-allocated `Order`s; overflow allocates a fresh one.
- **Latency sampling:** records every 64th sample into a capped array (`200_000`) to keep benchmarks cheap.
- **`OrderQueue`:** present as a blocking-queue alternative; the live path uses `RingBuffer`.
- **Not a full exchange:** no persistence, networking, risk checks, or multi-instrument routing — intentional scope for a clear matching-core demo.

## Documentation

| Doc | Purpose |
|---|---|
| [Getting started](docs/GETTING-STARTED.md) | Prerequisites, build, run, test |
| [Architecture](docs/ARCHITECTURE.md) | Components, data flow, invariants |
| [Project walkthrough](docs/PROJECT-WALKTHROUGH.md) | Guided study path through the code |

## License / authorship

Portfolio project by [muvhulawamuthula](https://github.com/muvhulawamuthula). Java 17 · Maven optional · no third-party runtime libraries.
