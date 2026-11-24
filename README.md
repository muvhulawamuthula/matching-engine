# High-Performance Java Order Matching Engine

This project is a concurrent order-ingress and single-threaded matching engine written in Java.
It is designed to demonstrate the kind of engineering that makes a Java portfolio stand out:
- multi-producer ring-buffer ingestion
- deterministic matching on a single worker thread
- price-time priority
- market orders and limit orders
- cancel commands
- trade events and execution reports
- book snapshots
- lightweight latency reporting

## Why this architecture matters
The order book itself is intentionally single-threaded. That keeps matching deterministic and avoids lock contention in the hottest part of the system.

The scalable part is the ingress path:
- multiple producer threads create commands
- commands are published into a bounded MPSC-style ring buffer
- a single worker drains the queue and calls the matching engine

This pattern gives you both concurrency and correctness.

## Project structure
- `model/Order` - command model for limit, market, and cancel instructions
- `queue/RingBuffer` - bounded multi-producer single-consumer ring buffer
- `engine/MatchingEngine` - core matching logic
- `engine/MatchingWorker` - queue consumer and latency timing wrapper
- `orderbook/OrderBook` - in-memory book with price-time priority and cancel support
- `events/` - trade events and execution reports
- `metrics/` - counters and latency percentiles
- `snapshot/` - human-readable state export
- `Main` - benchmark mode and deterministic demo mode

## Matching rules
### Limit order
A limit order trades immediately if it crosses the opposite side of the book. Any remaining quantity rests on the book.

### Market order
A market order trades immediately against the best available prices. Any unfilled remainder is cancelled and never rests on the book.

### Cancel command
A cancel command removes a resting order by target order ID. If the order is already gone, the command is rejected and counted as a cancel miss.

### Price-time priority
Within the same price level, older orders fill before newer orders. Between price levels, better prices match first.

## Implementation highlights
1. Multi-producer-safe ring buffer using atomic sequence claiming.
2. Worker shutdown drains the queue before exiting.
3. Prices are integer ticks (2 decimal places), never doubles.
4. LIMIT, MARKET and CANCEL order types.
5. Trade events and execution reports for every fill.
6. Cancel-by-ID and top-of-book snapshots.
7. Benchmark reports trade stats and p50 / p95 / p99 latency.

## Build and run
Requires JDK 21. Maven is optional: the scripts use plain `javac` and `jar`.

```bash
./build.sh               # compiles to out/ and packages dist/matching-engine.jar
./run-demo.sh            # scripted demo session
./run-benchmark.sh       # multi-producer benchmark
```

Or by hand:

```bash
find src/main/java -name "*.java" | sort > sources_main.txt
javac -d out @sources_main.txt
java -cp out com.muvhulawa.matchingengine.Main demo
java -cp out com.muvhulawa.matchingengine.Main benchmark 10
```

## Tests
```bash
mvn test
```

## Example output
- processed commands
- accepted orders
- cancelled orders
- rejected commands
- trades and traded quantity
- approximate p50 / p95 / p99 processing latency
- top-of-book snapshot

## License

MIT. See [LICENSE](LICENSE).
