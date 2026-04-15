package com.muvhulawa.matchingengine.events;

import com.muvhulawa.matchingengine.model.Order;

public record ExecutionReport(
        long orderId,
        Order.Status status,
        long remainingQuantity,
        String detail,
        long timestamp
) {
}
