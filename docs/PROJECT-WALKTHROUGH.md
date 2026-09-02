# Project walkthrough

A guided tour of the matching engine for readers who want to understand *why* the code is shaped this way — not only *what* each class does.

For setup commands, see [GETTING-STARTED.md](GETTING-STARTED.md).  
For component diagrams and invariants, see [ARCHITECTURE.md](ARCHITECTURE.md).

---

## 1. Big picture

Order entry can be concurrent. Book mutation must not be.

```text
N producers  →  MPSC RingBuffer  →  1 MatchingWorker  →  MatchingEngine / OrderBook
```

That split gives:

- **Scalable ingress** — many threads publish commands
- **Deterministic matching** — one worker owns all book state
- **Simple reasoning** — no lock around price-time priority logic

`Main` exposes two modes: `demo` (direct engine calls, fixed scenario) and `benchmark` (full producer → ring → worker path).

---

## 2. Command model (`model/Order.java`)

`Order` is both a **command** and a **resting order** record. Types:

| Type | Meaning |
|---|---|
| `LIMIT` | Match then rest residual |
| `MARKET` | Match only; residual rejected |
| `CANCEL` | Remove resting order by `targetOrderId` |

Also tracked: side, price ticks, original/remaining quantity, timestamp, lifecycle `Status`.

Factory methods (`limit` / `market` / `cancel`) and `reset*` methods exist so producers can reuse pooled instances without allocating new wrappers every time.

---

## 3. Why integer ticks beat `double`

Binary floating-point cannot represent many decimal prices exactly. Comparing `double`s in a matching loop invites subtle bugs.

`PriceUtil` converts with scale `2`:

- `100.25` → `10025` ticks
- Formatting for snapshots uses `BigDecimal` plain strings

All hot-path comparisons use `long` ticks.

---

## 4. Ring buffer (`queue/RingBuffer.java`)

Bounded MPSC. Study this file first if you care about concurrency.

Publish path (simplified):

1. Read current enqueue sequence
2. Check per-slot sequence → is this slot free for this cycle?
3. CAS to claim the sequence
4. Only the winning producer writes the slot and advances the slot sequence

Consume mirrors that for the single consumer. Full buffer → `publish` returns `false`. Empty → `consume` returns `null`.

**Contrast:** `OrderQueue` wraps `ArrayBlockingQueue` (blocking put/take). It is in the tree for comparison but **not** used by `Main`.

---

## 5. Worker (`engine/MatchingWorker.java`)

Shutdown contract:

```text
while (running || !buffer.isEmpty()) { ... }
```

Producers stop → worker keeps draining → then exits. Per-command latency is measured around `engine.process(order)` and fed to `LatencyRecorder` (sampled, not every call).

---

## 6. Order book (`orderbook/OrderBook.java`)

| Piece | Role |
|---|---|
| Bids `TreeMap` (reverse order) | Best bid = highest price |
| Asks `TreeMap` (natural order) | Best ask = lowest price |
| Level `LinkedHashMap<orderId, Order>` | FIFO time priority within price |
| `orderIndex` | Fast cancel / contains by ID |

`snapshot(depth)` returns best bid/ask (formatted), resting counts, and top levels.

---

## 7. Matching logic (`engine/MatchingEngine.java`)

### Crossing

- **Buy limit** crosses while best ask exists and `buy.priceTicks >= ask.priceTicks`
- **Sell limit** crosses while best bid exists and `sell.priceTicks <= bid.priceTicks`
- **Market** crosses any available opposite liquidity

### Trade price

Always the **passive** (resting) order’s price — standard maker/taker pricing for this style of book.

### Residuals

- Limit residual → `orderBook.add` → status resting + report
- Market residual → reject detail `"Unfilled market residual cancelled"` → pool release
- Filled aggressive/passive → reports + pool release as appropriate

### Cancel

`orderBook.cancel(targetId)` updates the level and index. Misses increment `cancelMisses` and emit a reject report. The cancel **command** object is always released to the pool.

---

## 8. Events

| Type | Answers |
|---|---|
| `Trade` | What executed? (IDs, price ticks, qty, side of aggressor) |
| `ExecutionReport` | How did this command/order end up? (filled / partial / resting / cancelled / rejected + detail) |

Stored in-memory via `EventLog` for demo/benchmark inspection.

---

## 9. Metrics

`EngineMetrics` tracks:

- processed commands, accepted orders, rejected commands
- cancelled orders, cancel misses
- trade count, traded quantity
- sampled latency percentiles (`p50` / `p95` / `p99` in µs)

Sampling stride keeps benchmark overhead low (`stride=64`, capped sample buffer).

---

## 10. Snapshots

After benchmark, `SnapshotWriter` writes `build/latest-snapshot.txt` with top of book, counters, latency summary, and recent trades. Handy when stdout scrolls past.

---

## 11. Demo mode

Fixed sequence in `Main.runDeterministicDemo` — ideal for stepping through fills and a cancel without producer noise. Does **not** use the ring/worker path (so worker latency samples are empty).

---

## 12. Benchmark mode

Four producers publish a xor-shift mix of roughly:

- ~6% cancel (`selector < 8`)
- ~19% market (`8 ≤ selector < 32`)
- ~75% limit (otherwise)

Prices cluster around `9900–10155` ticks (`99.00–101.55`). Quantities are small positive ranges. Producers spin on a full ring; on shutdown they release unsent pooled orders.

Defaults:

- Java `benchmark` with no duration → **15s**
- `./run-benchmark.sh` with no arg → **10s**

---

## 13. Why this works as a Java portfolio piece

It shows more than CRUD:

- Concurrency design (MPSC ingress vs single-writer core)
- Bounded queue / backpressure thinking
- Market microstructure (price-time, passive pricing, market residuals)
- Lifecycle events and lightweight telemetry
- Build scripts that run without Maven, plus optional Maven tests

---

## 14. Suggested study order

1. `queue/RingBuffer.java` — claim/publish/consume
2. `engine/MatchingWorker.java` — drain-on-stop + timing
3. `orderbook/OrderBook.java` — priority structures
4. `engine/MatchingEngine.java` — match / rest / reject
5. `Main.java` — demo vs benchmark wiring
6. `model/Order.java` + `pool/OrderPool.java` — command reuse
7. Tests under `src/test/java/...` — expected behaviors as executable docs

Then re-read [ARCHITECTURE.md](ARCHITECTURE.md) to lock in the invariants.
