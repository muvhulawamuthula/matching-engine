package com.muvhulawa.matchingengine.engine;

import com.muvhulawa.matchingengine.metrics.EngineMetrics;
import com.muvhulawa.matchingengine.model.Order;
import com.muvhulawa.matchingengine.queue.RingBuffer;

public class MatchingWorker implements Runnable {

    private final RingBuffer buffer;
    private final MatchingEngine engine;
    private final EngineMetrics metrics;
    private volatile boolean running = true;

    public MatchingWorker(RingBuffer buffer, MatchingEngine engine, EngineMetrics metrics) {
        this.buffer = buffer;
        this.engine = engine;
        this.metrics = metrics;
    }

    @Override
    public void run() {
        while (running || !buffer.isEmpty()) {
            Order order = buffer.consume();

            if (order != null) {
                long started = System.nanoTime();
                engine.process(order);
                metrics.recordLatency(System.nanoTime() - started);
            } else {
                Thread.onSpinWait();
            }
        }
    }

    public void stop() {
        running = false;
    }
}
