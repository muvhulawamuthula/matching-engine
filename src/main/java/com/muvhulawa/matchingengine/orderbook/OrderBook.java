package com.muvhulawa.matchingengine.orderbook;

import com.muvhulawa.matchingengine.model.Order;
import com.muvhulawa.matchingengine.util.PriceUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class OrderBook {

    private final NavigableMap<Long, LinkedHashMap<Long, Order>> bids = new TreeMap<>(Collections.reverseOrder());
    private final NavigableMap<Long, LinkedHashMap<Long, Order>> asks = new TreeMap<>();
    private final Map<Long, Order> orderIndex = new java.util.HashMap<>();

    public void add(Order order) {
        NavigableMap<Long, LinkedHashMap<Long, Order>> sideBook = sideBook(order.getSide());
        LinkedHashMap<Long, Order> level = sideBook.computeIfAbsent(order.getPriceTicks(), ignored -> new LinkedHashMap<>());
        level.put(order.getId(), order);
        order.markResting();
        orderIndex.put(order.getId(), order);
    }

    public boolean cancel(long orderId) {
        Order order = orderIndex.remove(orderId);
        if (order == null) {
            return false;
        }

        NavigableMap<Long, LinkedHashMap<Long, Order>> sideBook = sideBook(order.getSide());
        LinkedHashMap<Long, Order> level = sideBook.get(order.getPriceTicks());
        if (level == null || level.remove(orderId) == null) {
            return false;
        }
        if (level.isEmpty()) {
            sideBook.remove(order.getPriceTicks());
        }
        order.markCancelled();
        return true;
    }

    public Order bestBid() {
        return best(bids);
    }

    public Order bestAsk() {
        return best(asks);
    }

    public Order removeBestBid() {
        return removeBest(bids);
    }

    public Order removeBestAsk() {
        return removeBest(asks);
    }

    public long restingOrderCount(Order.Side side) {
        return sideBook(side).values().stream().mapToLong(Map::size).sum();
    }

    public long totalVisibleQuantity(Order.Side side, long priceTicks) {
        LinkedHashMap<Long, Order> level = sideBook(side).get(priceTicks);
        if (level == null) {
            return 0L;
        }
        return level.values().stream().mapToLong(Order::getRemainingQuantity).sum();
    }

    public OrderBookSnapshot snapshot(int depth) {
        Order bestBid = bestBid();
        Order bestAsk = bestAsk();
        return new OrderBookSnapshot(
                bestBid == null ? null : PriceUtil.formatTicks(bestBid.getPriceTicks()),
                bestBid == null ? 0L : totalVisibleQuantity(Order.Side.BUY, bestBid.getPriceTicks()),
                bestAsk == null ? null : PriceUtil.formatTicks(bestAsk.getPriceTicks()),
                bestAsk == null ? 0L : totalVisibleQuantity(Order.Side.SELL, bestAsk.getPriceTicks()),
                restingOrderCount(Order.Side.BUY),
                restingOrderCount(Order.Side.SELL),
                topLevels(bids, depth),
                topLevels(asks, depth)
        );
    }

    private List<String> topLevels(NavigableMap<Long, LinkedHashMap<Long, Order>> sideBook, int depth) {
        List<String> levels = new ArrayList<>();
        for (Map.Entry<Long, LinkedHashMap<Long, Order>> entry : sideBook.entrySet()) {
            long totalQuantity = entry.getValue().values().stream().mapToLong(Order::getRemainingQuantity).sum();
            levels.add(PriceUtil.formatTicks(entry.getKey()) + " x " + totalQuantity);
            if (levels.size() == depth) {
                break;
            }
        }
        return levels;
    }

    public boolean contains(long orderId) {
        return orderIndex.containsKey(orderId);
    }

    private NavigableMap<Long, LinkedHashMap<Long, Order>> sideBook(Order.Side side) {
        return side == Order.Side.BUY ? bids : asks;
    }

    private Order best(NavigableMap<Long, LinkedHashMap<Long, Order>> sideBook) {
        while (!sideBook.isEmpty()) {
            Map.Entry<Long, LinkedHashMap<Long, Order>> entry = sideBook.firstEntry();
            if (entry.getValue().isEmpty()) {
                sideBook.pollFirstEntry();
                continue;
            }
            return entry.getValue().values().iterator().next();
        }
        return null;
    }

    private Order removeBest(NavigableMap<Long, LinkedHashMap<Long, Order>> sideBook) {
        while (!sideBook.isEmpty()) {
            Map.Entry<Long, LinkedHashMap<Long, Order>> entry = sideBook.firstEntry();
            if (entry.getValue().isEmpty()) {
                sideBook.pollFirstEntry();
                continue;
            }
            Map.Entry<Long, Order> firstOrder = entry.getValue().entrySet().iterator().next();
            entry.getValue().remove(firstOrder.getKey());
            if (entry.getValue().isEmpty()) {
                sideBook.pollFirstEntry();
            }
            orderIndex.remove(firstOrder.getKey());
            return firstOrder.getValue();
        }
        return null;
    }
}
