# Architecture

This document describes how the matching engine is structured, what each component owns, and the invariants that keep matching correct under concurrent ingress.

## Goals

1. **Scale order entry** across producer threads without locking the book.
2. **Keep matching deterministic** — one writer, predictable priority rules.
3. **Stay observable** — trades, execution reports, counters, latency samples.
4. **Stay honest about scope** — in-process core, not a networked exchange.

## High-level data flow

```text
Producers (×4 in benchmark)
    │  borrow Order from OrderPool
    │  resetLimit / resetMarket / resetCancel
    ▼
RingBuffer (MPSC, capacity 8192, power of 2)
    │  atomic enqueue sequence claim (CAS)
    │  per-slot sequence for publish/consume handoff
    ▼
MatchingWorker (single thread)
    │  consume → nanoTime → MatchingEngine.process → recordLatency
    │  on stop: continue until buffer.isEmpty()
    ▼
MatchingEngine
    ├── validate command
    ├── match against OrderBook (passive price)
    ├── emit Trade / ExecutionReport via EventLog
    ├── rest residual LIMIT on book  OR  reject MARKET residual
    └── release finished Orders back to OrderPool
```

## Component responsibilities

### `RingBuffer` (`queue/RingBuffer.java`)

Bounded **multi-producer, single-consumer** queue.

- Capacity must be a power of two (mask indexing).
- Producers claim an enqueue sequence with `compareAndSet`.
- Per-slot sequence numbers distinguish: free for this cycle / published / still owned by a previous cycle.
- `publish` returns `false` when full (caller decides: spin or give up).
- `consume` returns `null` when empty.
- Used by `Main` benchmark path; this is the production ingress.

### `OrderQueue` (`queue/OrderQueue.java`)

Thin wrapper over `ArrayBlockingQueue` with blocking `put`/`take`. **Not wired into `Main`.** Useful as a contrast: lock-based backpressure vs lock-free ring claiming.

### `MatchingWorker` (`engine/MatchingWorker.java`)

Sole consumer of the ring buffer.

- Loop condition: `running || !buffer.isEmpty()` → **drain on shutdown**.
- Times each successful consume → `engine.process` window with `System.nanoTime()`.
- Spins with `Thread.onSpinWait()` when the buffer is empty while still running.

### `MatchingEngine` (`engine/MatchingEngine.java`)

Command dispatcher and match loop. Not thread-safe by design — call only from the worker (or single-threaded demo).

| Command | Behavior |
|---|---|
| `LIMIT` | Reject if qty ≤ 0 or priceTicks ≤ 0. Else accept, match, rest residual, or release if filled. |
| `MARKET` | Reject if qty ≤ 0. Else accept, match; any residual → reject + release. |
| `CANCEL` | Cancel by `targetOrderId`; miss → reject; always release the cancel command object. |

**Match loop**

1. While aggressive not filled, take `bestAsk` (buy) or `bestBid` (sell).
2. Cross check: market always crosses if liquidity exists; limit crosses when buy ≥ ask or sell ≤ bid.
3. Trade qty = `min(aggressive.remaining, passive.remaining)`.
4. Trade price = **passive** `priceTicks`.
5. Reduce both; record `Trade` + metrics; report both sides.
6. If passive filled → remove best from book and release to pool.

### `OrderBook` (`orderbook/OrderBook.java`)

In-memory price-time book:

| Structure | Role |
|---|---|
| `TreeMap<Long, LinkedHashMap<Long, Order>>` bids | Descending prices; FIFO within level |
| `TreeMap<Long, LinkedHashMap<Long, Order>>` asks | Ascending prices; FIFO within level |
| `HashMap<Long, Order> orderIndex` | Cancel / contains by order ID |

`best` / `removeBest` skip empty levels. Snapshots format prices via `PriceUtil` and aggregate quantity per level.

### `Order` (`model/Order.java`)

Mutable command + resting-order state machine:

- Types: `LIMIT`, `MARKET`, `CANCEL`
- Statuses: `NEW`, `PARTIALLY_FILLED`, `FILLED`, `CANCELLED`, `REJECTED`, `RESTING`
- Factory helpers: `Order.limit`, `Order.market`, `Order.cancel`
- Reset methods support pooling without reallocating wrappers

### `OrderPool` (`pool/OrderPool.java`)

Pre-sized `ArrayBlockingQueue<Order>`. `borrow()` polls or allocates; `release()` offers back. Reduces allocation churn under benchmark load.

### Events & metrics

- **`Trade`:** aggressive/passive IDs, price ticks, qty, timestamp, aggressive-buy flag.
- **`ExecutionReport`:** order ID, status, remaining qty, detail string, timestamp.
- **`EventLog`:** in-memory lists (unmodifiable views outward).
- **`EngineMetrics`:** atomic counters + `LatencyRecorder` (stride-64 sampling, capped samples, percentile summary in µs).

### `SnapshotWriter`

Writes a human-readable text report (top of book, counters, recent trades) under `build/`.

### `Main`

| Mode | Wiring |
|---|---|
| `demo` | No ring buffer — calls `engine.process` directly with a fixed sequence |
| `benchmark` | Ring `8192`, pool `65536`, 4 producers, worker thread, optional seconds arg |

## Invariants

1. **Single writer:** only one thread may call `MatchingEngine.process` / mutate `OrderBook` for a given engine instance.
2. **Price-time priority:** better price first; within a level, insertion order (FIFO).
3. **Passive pricing:** trades execute at the resting order’s price.
4. **Market never rests:** unfilled residual is rejected and pooled.
5. **Limit may rest:** residual after matching is `add`ed to the book.
6. **Integer ticks only** on the match path (`long` comparisons).
7. **Drain on stop:** stopping the worker does not abandon published commands still in the ring.

## Tunables (code constants)

| Constant | Location | Default |
|---|---|---|
| `RING_BUFFER_SIZE` | `Main` | `8192` |
| `ORDER_POOL_SIZE` | `Main` | `65536` |
| `PRODUCER_COUNT` | `Main` | `4` |
| Latency max samples / stride | `LatencyRecorder` via `EngineMetrics` | `200_000` / `64` |
| Price scale | `PriceUtil.SCALE` | `2` |

## Out of scope (by design)

- Network protocols (FIX/ITCH/WebSocket)
- Persistence / recovery / WAL
- Multi-symbol routing
- Risk / margin / self-trade prevention
- Disruptor / Agrona / native libs — kept to JDK primitives for clarity

Those omissions keep the repository readable as a matching-core reference rather than a mini-exchange.
