package com.muvhulawa.matchingengine.engine;

import com.muvhulawa.matchingengine.events.EventLog;
import com.muvhulawa.matchingengine.events.ExecutionReport;
import com.muvhulawa.matchingengine.events.Trade;
import com.muvhulawa.matchingengine.metrics.EngineMetrics;
import com.muvhulawa.matchingengine.model.Order;
import com.muvhulawa.matchingengine.orderbook.OrderBook;
import com.muvhulawa.matchingengine.orderbook.OrderBookSnapshot;
import com.muvhulawa.matchingengine.pool.OrderPool;

public class MatchingEngine {

    private final OrderBook orderBook = new OrderBook();
    private final OrderPool orderPool;
    private final EngineMetrics metrics;
    private final EventLog eventLog;

    public MatchingEngine(OrderPool orderPool, EngineMetrics metrics) {
        this(orderPool, metrics, new EventLog());
    }

    public MatchingEngine(OrderPool orderPool, EngineMetrics metrics, EventLog eventLog) {
        this.orderPool = orderPool;
        this.metrics = metrics;
        this.eventLog = eventLog;
    }

    public void process(Order command) {
        metrics.incrementProcessed();
        switch (command.getType()) {
            case LIMIT -> processLimit(command);
            case MARKET -> processMarket(command);
            case CANCEL -> processCancel(command);
            default -> reject(command, "Unsupported command type");
        }
    }

    public OrderBookSnapshot snapshot(int depth) {
        return orderBook.snapshot(depth);
    }

    public EventLog getEventLog() {
        return eventLog;
    }

    public boolean containsRestingOrder(long orderId) {
        return orderBook.contains(orderId);
    }

    private void processLimit(Order order) {
        if (order.getRemainingQuantity() <= 0 || order.getPriceTicks() <= 0) {
            reject(order, "Invalid limit order");
            return;
        }
        metrics.incrementAcceptedOrders();
        match(order, false);
        if (!order.isFilled()) {
            orderBook.add(order);
            report(order, order.getStatus(), "Resting on book");
        } else {
            orderPool.release(order);
        }
    }

    private void processMarket(Order order) {
        if (order.getRemainingQuantity() <= 0) {
            reject(order, "Invalid market order");
            return;
        }
        metrics.incrementAcceptedOrders();
        match(order, true);
        if (!order.isFilled()) {
            reject(order, "Unfilled market residual cancelled");
            orderPool.release(order);
        } else {
            orderPool.release(order);
        }
    }

    private void processCancel(Order command) {
        if (orderBook.cancel(command.getTargetOrderId())) {
            metrics.incrementCancelledOrders();
            report(command, Order.Status.CANCELLED, "Cancelled order " + command.getTargetOrderId());
        } else {
            metrics.incrementCancelMisses();
            reject(command, "Order not found for cancel: " + command.getTargetOrderId());
        }
        orderPool.release(command);
    }

    private void match(Order aggressive, boolean marketOrder) {
        while (!aggressive.isFilled()) {
            Order passive = aggressive.isBuy() ? orderBook.bestAsk() : orderBook.bestBid();
            if (passive == null) {
                break;
            }

            boolean crosses = marketOrder ||
                    (aggressive.isBuy() && aggressive.getPriceTicks() >= passive.getPriceTicks()) ||
                    (!aggressive.isBuy() && aggressive.getPriceTicks() <= passive.getPriceTicks());

            if (!crosses) {
                break;
            }

            long tradedQuantity = Math.min(aggressive.getRemainingQuantity(), passive.getRemainingQuantity());
            long tradePrice = passive.getPriceTicks();

            aggressive.reduceRemaining(tradedQuantity);
            passive.reduceRemaining(tradedQuantity);
            metrics.recordTrade(tradedQuantity);
            eventLog.recordTrade(new Trade(
                    aggressive.getId(),
                    passive.getId(),
                    tradePrice,
                    tradedQuantity,
                    Math.max(aggressive.getTimestamp(), passive.getTimestamp()),
                    aggressive.isBuy()
            ));

            if (passive.isFilled()) {
                if (passive.isBuy()) {
                    orderBook.removeBestBid();
                } else {
                    orderBook.removeBestAsk();
                }
                report(passive, Order.Status.FILLED, "Fully filled at passive price");
                orderPool.release(passive);
            } else {
                report(passive, Order.Status.PARTIALLY_FILLED, "Partially filled and remains on book");
            }

            report(aggressive, aggressive.getStatus(), aggressive.isFilled() ? "Aggressive order fully filled" : "Aggressive order partially filled");
        }
    }

    private void reject(Order order, String detail) {
        order.markRejected();
        metrics.incrementRejectedCommands();
        report(order, Order.Status.REJECTED, detail);
    }

    private void report(Order order, Order.Status status, String detail) {
        eventLog.recordReport(new ExecutionReport(
                order.getId(),
                status,
                order.getRemainingQuantity(),
                detail,
                order.getTimestamp()
        ));
    }
}
