package com.muvhulawa.matchingengine.orderbook;

import java.util.List;

public record OrderBookSnapshot(
        String bestBid,
        long bestBidQuantity,
        String bestAsk,
        long bestAskQuantity,
        long restingBuyOrders,
        long restingSellOrders,
        List<String> topBids,
        List<String> topAsks
) {
}
