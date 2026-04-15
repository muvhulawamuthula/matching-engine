package com.muvhulawa.matchingengine.metrics;

import java.util.concurrent.atomic.AtomicLong;

public class EngineMetrics {

    private final AtomicLong processedCommands = new AtomicLong();
    private final AtomicLong acceptedOrders = new AtomicLong();
    private final AtomicLong rejectedCommands = new AtomicLong();
    private final AtomicLong cancelledOrders = new AtomicLong();
    private final AtomicLong cancelMisses = new AtomicLong();
    private final AtomicLong tradeCount = new AtomicLong();
    private final AtomicLong tradedQuantity = new AtomicLong();
    private final LatencyRecorder latencyRecorder = new LatencyRecorder(200_000, 64);

    public void incrementProcessed() { processedCommands.incrementAndGet(); }
    public void incrementAcceptedOrders() { acceptedOrders.incrementAndGet(); }
    public void incrementRejectedCommands() { rejectedCommands.incrementAndGet(); }
    public void incrementCancelledOrders() { cancelledOrders.incrementAndGet(); }
    public void incrementCancelMisses() { cancelMisses.incrementAndGet(); }

    public void recordTrade(long quantity) {
        tradeCount.incrementAndGet();
        tradedQuantity.addAndGet(quantity);
    }

    public void recordLatency(long nanos) {
        latencyRecorder.record(nanos);
    }

    public long getProcessedCommands() { return processedCommands.get(); }
    public long getAcceptedOrders() { return acceptedOrders.get(); }
    public long getRejectedCommands() { return rejectedCommands.get(); }
    public long getCancelledOrders() { return cancelledOrders.get(); }
    public long getCancelMisses() { return cancelMisses.get(); }
    public long getTradeCount() { return tradeCount.get(); }
    public long getTradedQuantity() { return tradedQuantity.get(); }
    public String latencySummary() { return latencyRecorder.percentileSummaryMicros(); }
}
