package com.muvhulawa.matchingengine.orderbook;

import com.muvhulawa.matchingengine.model.Order;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderBookTest {

    @Test
    void shouldKeepPriceTimePriorityAcrossLevels() {
        OrderBook book = new OrderBook();
        book.add(Order.limit(1L, Order.Side.BUY, 10_010L, 3L, 1L));
        book.add(Order.limit(2L, Order.Side.BUY, 10_020L, 2L, 2L));
        book.add(Order.limit(3L, Order.Side.BUY, 10_020L, 4L, 3L));

        assertEquals(2L, book.bestBid().getId());
        assertEquals(2L, book.removeBestBid().getId());
        assertEquals(3L, book.bestBid().getId());
        assertEquals(4L, book.bestBid().getRemainingQuantity());
    }

    @Test
    void shouldCancelByOrderId() {
        OrderBook book = new OrderBook();
        book.add(Order.limit(10L, Order.Side.SELL, 10_050L, 5L, 1L));
        assertTrue(book.contains(10L));
        assertTrue(book.cancel(10L));
        assertFalse(book.contains(10L));
        assertFalse(book.cancel(10L));
    }
}
