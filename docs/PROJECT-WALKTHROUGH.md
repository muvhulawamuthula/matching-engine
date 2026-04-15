# Order Matching Engine Walkthrough

## 1. Big picture
This system accepts order commands from multiple producer threads and feeds them into a single matching worker.

That design gives us two benefits:
- order entry can scale across threads
- book mutation stays deterministic because only one worker mutates the book

## 2. Command model
The `Order` class is used as a command object. It supports:
- `LIMIT`
- `MARKET`
- `CANCEL`

It also stores:
- side
- price in integer ticks
- original quantity
- remaining quantity
- timestamp
- target order ID for cancels
- lifecycle status

## 3. Why integer ticks are better than double
`double` values are risky for financial comparison because binary floating-point cannot represent many decimal values exactly.

This project converts prices into integer ticks. Example:
- `100.25` becomes `10025`

That makes comparisons exact and keeps matching logic clean.

## 4. Ring buffer
The ring buffer is bounded and supports multiple producers and one consumer.

The important fix was atomic slot claiming:
- producer reads current enqueue sequence
- producer uses CAS to claim that sequence
- only the winning producer may write to that slot

Per-slot sequence numbers tell producers and the consumer whether a slot is free, ready, or still owned by another cycle of the ring.

## 5. Worker
The worker loops until:
- shutdown has been requested, and
- the buffer is empty

This ensures a clean drain instead of dropping in-flight work.

The worker also measures per-command processing latency and feeds that into the latency recorder.

## 6. Order book
The order book uses:
- descending bid price map
- ascending ask price map
- FIFO order within each price level using `LinkedHashMap`
- direct order ID index for cancel support

This gives:
- best price lookup
- time priority within a level
- quick cancel by ID

## 7. Matching logic
### Buy order
A buy order matches the best ask while the book has asks it can cross.

### Sell order
A sell order matches the best bid while the book has bids it can cross.

### Trade price
This implementation uses the passive order price as the trade price.

### Partial fills
If the aggressive order still has quantity after a trade, it continues matching.
If the passive order is fully filled, it is removed from the book.
If a limit order still has residual quantity after matching, it rests on the book.
If a market order still has residual quantity, it is rejected and discarded.

## 8. Events
Two event types are recorded:
- `Trade`
- `ExecutionReport`

`Trade` tells you what executed.
`ExecutionReport` tells you how an order/command ended up: filled, partially filled, resting, cancelled, or rejected.

## 9. Metrics
The engine tracks:
- processed commands
- accepted orders
- rejected commands
- cancelled orders
- cancel misses
- trade count
- traded quantity
- sampled latency percentiles

Latency sampling is intentionally lightweight so benchmark mode remains cheap to run.

## 10. Snapshot
At the end of a benchmark run, the system writes a text snapshot that includes:
- best bid / ask
- resting order counts
- top levels on both sides
- counters
- recent trades

This makes the engine easier to inspect after a run.

## 11. Demo mode
`demo` mode runs a fixed sequence of commands so you can see the matching logic in a predictable way.

## 12. Benchmark mode
`benchmark` mode starts multiple producers and one worker. Producers generate a mixture of:
- limit orders
- market orders
- cancel commands



## 13. Why this project is strong for a Java portfolio
This project is much stronger than a CRUD app because it shows:
- concurrency reasoning
- bounded queue design
- deterministic single-threaded matching core
- financial-domain modeling
- lifecycle events and metrics
- benchmarking and observability thinking

## 14. What you should study in the code first
1. `queue/RingBuffer.java`
2. `engine/MatchingWorker.java`
3. `orderbook/OrderBook.java`
4. `engine/MatchingEngine.java`
5. `Main.java`


