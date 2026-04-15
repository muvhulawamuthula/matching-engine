package com.muvhulawa.matchingengine.events;

public record Trade(
        long aggressiveOrderId,
        long passiveOrderId,
        long priceTicks,
        long quantity,
        long timestamp,
        boolean aggressiveBuy
) {
}
