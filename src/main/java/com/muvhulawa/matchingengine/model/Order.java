package com.muvhulawa.matchingengine.model;

public class Order {
    public enum Side { BUY, SELL }
    public enum Type { LIMIT, MARKET, CANCEL }
    public enum Status { NEW, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED, RESTING }

    private long id;
    private Type type;
    private Side side;
    private long priceTicks;
    private long quantity;
    private long remainingQuantity;
    private long timestamp;
    private long targetOrderId;
    private Status status;

    public Order() {
        clear();
    }

    public static Order limit(long id, Side side, long priceTicks, long quantity, long timestamp) {
        Order order = new Order();
        order.resetLimit(id, side, priceTicks, quantity, timestamp);
        return order;
    }

    public static Order market(long id, Side side, long quantity, long timestamp) {
        Order order = new Order();
        order.resetMarket(id, side, quantity, timestamp);
        return order;
    }

    public static Order cancel(long id, long targetOrderId, long timestamp) {
        Order order = new Order();
        order.resetCancel(id, targetOrderId, timestamp);
        return order;
    }

    public void reset(long id, Side side, double price, long quantity, long timestamp) {
        resetLimit(id, side, com.muvhulawa.matchingengine.util.PriceUtil.toTicks(price), quantity, timestamp);
    }

    public void resetLimit(long id, Side side, long priceTicks, long quantity, long timestamp) {
        clear();
        this.id = id;
        this.type = Type.LIMIT;
        this.side = side;
        this.priceTicks = priceTicks;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.timestamp = timestamp;
        this.status = Status.NEW;
    }

    public void resetMarket(long id, Side side, long quantity, long timestamp) {
        clear();
        this.id = id;
        this.type = Type.MARKET;
        this.side = side;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.timestamp = timestamp;
        this.status = Status.NEW;
    }

    public void resetCancel(long id, long targetOrderId, long timestamp) {
        clear();
        this.id = id;
        this.type = Type.CANCEL;
        this.targetOrderId = targetOrderId;
        this.timestamp = timestamp;
        this.status = Status.NEW;
    }

    public void clear() {
        this.id = 0L;
        this.type = Type.LIMIT;
        this.side = Side.BUY;
        this.priceTicks = 0L;
        this.quantity = 0L;
        this.remainingQuantity = 0L;
        this.timestamp = 0L;
        this.targetOrderId = 0L;
        this.status = Status.NEW;
    }

    public long getId() { return id; }
    public Type getType() { return type; }
    public Side getSide() { return side; }
    public long getPriceTicks() { return priceTicks; }
    public long getQuantity() { return quantity; }
    public long getRemainingQuantity() { return remainingQuantity; }
    public long getTimestamp() { return timestamp; }
    public long getTargetOrderId() { return targetOrderId; }
    public Status getStatus() { return status; }

    public boolean isBuy() {
        return side == Side.BUY;
    }

    public boolean isFilled() {
        return remainingQuantity == 0L;
    }

    public void reduceRemaining(long tradedQuantity) {
        if (tradedQuantity < 0 || tradedQuantity > remainingQuantity) {
            throw new IllegalArgumentException("Invalid traded quantity: " + tradedQuantity);
        }
        remainingQuantity -= tradedQuantity;
        status = remainingQuantity == 0L ? Status.FILLED : Status.PARTIALLY_FILLED;
    }

    public void markResting() {
        status = Status.RESTING;
    }

    public void markCancelled() {
        status = Status.CANCELLED;
        remainingQuantity = 0L;
    }

    public void markRejected() {
        status = Status.REJECTED;
        remainingQuantity = 0L;
    }
}
