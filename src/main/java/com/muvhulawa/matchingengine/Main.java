package com.muvhulawa.matchingengine;

import com.muvhulawa.matchingengine.engine.MatchingEngine;
import com.muvhulawa.matchingengine.engine.MatchingWorker;
import com.muvhulawa.matchingengine.events.EventLog;
import com.muvhulawa.matchingengine.metrics.EngineMetrics;
import com.muvhulawa.matchingengine.model.Order;
import com.muvhulawa.matchingengine.pool.OrderPool;
import com.muvhulawa.matchingengine.queue.RingBuffer;
import com.muvhulawa.matchingengine.snapshot.SnapshotWriter;

import java.nio.file.Path;
import java.util.List;

public class Main {

    private static final int RING_BUFFER_SIZE = 8192;
    private static final int ORDER_POOL_SIZE = 65536;
    private static final int PRODUCER_COUNT = 4;
    private static volatile boolean running = true;

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "benchmark";
        switch (mode) {
            case "demo" -> runDeterministicDemo();
            case "benchmark" -> runBenchmark(args.length > 1 ? Long.parseLong(args[1]) : 15L);
            default -> {
                System.out.println("Usage: java -jar matching-engine.jar [benchmark <seconds>|demo]");
                System.exit(1);
            }
        }
    }

    private static void runBenchmark(long runSeconds) throws Exception {
        RingBuffer buffer = new RingBuffer(RING_BUFFER_SIZE);
        OrderPool pool = new OrderPool(ORDER_POOL_SIZE);
        EngineMetrics metrics = new EngineMetrics();
        EventLog eventLog = new EventLog();
        MatchingEngine engine = new MatchingEngine(pool, metrics, eventLog);

        MatchingWorker worker = new MatchingWorker(buffer, engine, metrics);
        Thread workerThread = new Thread(worker, "matching-worker");
        workerThread.start();

        Thread[] producers = new Thread[PRODUCER_COUNT];
        running = true;

        for (int i = 0; i < PRODUCER_COUNT; i++) {
            final int threadId = i;
            producers[i] = new Thread(() -> produceCommands(threadId, buffer, pool), "producer-" + i);
            producers[i].start();
        }

        Thread.sleep(runSeconds * 1000L);
        running = false;

        for (Thread producer : producers) {
            producer.join();
        }

        worker.stop();
        workerThread.join();

        double seconds = runSeconds;
        System.out.println("Run time (s): " + seconds);
        System.out.println("Processed commands: " + metrics.getProcessedCommands());
        System.out.println("Accepted orders: " + metrics.getAcceptedOrders());
        System.out.println("Trades: " + metrics.getTradeCount());
        System.out.println("Traded quantity: " + metrics.getTradedQuantity());
        System.out.println("Cancelled orders: " + metrics.getCancelledOrders());
        System.out.println("Rejected commands: " + metrics.getRejectedCommands());
        System.out.println("Commands/sec: " + (long) (metrics.getProcessedCommands() / seconds));
        System.out.println("Latency summary: " + metrics.latencySummary());
        System.out.println("Top of book: " + engine.snapshot(5));

        Path snapshotFile = SnapshotWriter.write(
                Path.of("build", "latest-snapshot.txt"),
                engine.snapshot(5),
                metrics,
                eventLog.getTrades().stream().skip(Math.max(0, eventLog.getTrades().size() - 10L)).toList()
        );
        System.out.println("Snapshot written to: " + snapshotFile.toAbsolutePath());
    }

    private static void produceCommands(int threadId, RingBuffer buffer, OrderPool pool) {
        long commandId = threadId * 10_000_000L + 1L;
        long seed = System.nanoTime() + threadId * 17L;
        long timestamp = threadId * 1_000_000_000L;

        while (running) {
            seed = xorShift(seed);
            long selector = seed & 127L;
            Order order = pool.borrow();

            if (selector < 8) {
                long targetId = Math.max(1L, commandId - ((seed & 1023L) + 1L));
                order.resetCancel(commandId++, targetId, timestamp++);
            } else if (selector < 32) {
                Order.Side side = (selector & 1L) == 0L ? Order.Side.BUY : Order.Side.SELL;
                long quantity = ((seed >>> 4) & 15L) + 1L;
                order.resetMarket(commandId++, side, quantity, timestamp++);
            } else {
                Order.Side side = (selector & 1L) == 0L ? Order.Side.BUY : Order.Side.SELL;
                long priceTicks = 9_900L + (seed & 255L);
                long quantity = ((seed >>> 8) & 31L) + 1L;
                order.resetLimit(commandId++, side, priceTicks, quantity, timestamp++);
            }

            while (!buffer.publish(order)) {
                if (!running) {
                    pool.release(order);
                    return;
                }
                Thread.onSpinWait();
            }
        }
    }

    private static long xorShift(long x) {
        x ^= (x << 13);
        x ^= (x >>> 7);
        x ^= (x << 17);
        return x;
    }

    private static void runDeterministicDemo() {
        OrderPool pool = new OrderPool(64);
        EngineMetrics metrics = new EngineMetrics();
        EventLog eventLog = new EventLog();
        MatchingEngine engine = new MatchingEngine(pool, metrics, eventLog);

        engine.process(Order.limit(1, Order.Side.SELL, 10_000L, 5L, 1L));
        engine.process(Order.limit(2, Order.Side.SELL, 10_000L, 7L, 2L));
        engine.process(Order.market(3, Order.Side.BUY, 6L, 3L));
        engine.process(Order.limit(4, Order.Side.BUY, 9_950L, 3L, 4L));
        engine.process(Order.cancel(5, 4L, 5L));

        System.out.println("Trades: " + eventLog.getTrades().size());
        System.out.println("Reports: " + eventLog.getReports().size());
        System.out.println("Snapshot: " + engine.snapshot(5));
        System.out.println("Latency summary: " + metrics.latencySummary());
        List<String> reportLines = eventLog.getReports().stream()
                .map(r -> r.orderId() + ":" + r.status() + ":" + r.detail())
                .toList();
        reportLines.forEach(System.out::println);
    }
}
