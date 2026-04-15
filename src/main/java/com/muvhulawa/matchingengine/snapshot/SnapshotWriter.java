package com.muvhulawa.matchingengine.snapshot;

import com.muvhulawa.matchingengine.events.Trade;
import com.muvhulawa.matchingengine.metrics.EngineMetrics;
import com.muvhulawa.matchingengine.orderbook.OrderBookSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SnapshotWriter {
    private SnapshotWriter() {
    }

    public static Path write(Path outputFile, OrderBookSnapshot snapshot, EngineMetrics metrics, List<Trade> recentTrades) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("ORDER MATCHING ENGINE SNAPSHOT\n");
        sb.append("Best bid: ").append(snapshot.bestBid()).append(" qty=").append(snapshot.bestBidQuantity()).append('\n');
        sb.append("Best ask: ").append(snapshot.bestAsk()).append(" qty=").append(snapshot.bestAskQuantity()).append('\n');
        sb.append("Resting buys: ").append(snapshot.restingBuyOrders()).append('\n');
        sb.append("Resting sells: ").append(snapshot.restingSellOrders()).append('\n');
        sb.append("Top bids: ").append(snapshot.topBids()).append('\n');
        sb.append("Top asks: ").append(snapshot.topAsks()).append('\n');
        sb.append("Processed commands: ").append(metrics.getProcessedCommands()).append('\n');
        sb.append("Accepted orders: ").append(metrics.getAcceptedOrders()).append('\n');
        sb.append("Rejected commands: ").append(metrics.getRejectedCommands()).append('\n');
        sb.append("Cancelled orders: ").append(metrics.getCancelledOrders()).append('\n');
        sb.append("Cancel misses: ").append(metrics.getCancelMisses()).append('\n');
        sb.append("Trades: ").append(metrics.getTradeCount()).append('\n');
        sb.append("Traded quantity: ").append(metrics.getTradedQuantity()).append('\n');
        sb.append("Latency: ").append(metrics.latencySummary()).append('\n');
        sb.append("Recent trades:\n");
        for (Trade trade : recentTrades) {
            sb.append("- aggressive=").append(trade.aggressiveOrderId())
                    .append(", passive=").append(trade.passiveOrderId())
                    .append(", priceTicks=").append(trade.priceTicks())
                    .append(", qty=").append(trade.quantity())
                    .append('\n');
        }
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, sb.toString());
        return outputFile;
    }
}
