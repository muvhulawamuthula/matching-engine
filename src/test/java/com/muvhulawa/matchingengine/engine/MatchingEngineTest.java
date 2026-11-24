package com.muvhulawa.matchingengine.engine;

import com.muvhulawa.matchingengine.events.EventLog;
import com.muvhulawa.matchingengine.metrics.EngineMetrics;
import com.muvhulawa.matchingengine.model.Order;
import com.muvhulawa.matchingengine.pool.OrderPool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchingEngineTest {

    @Test
    void shouldRespectPriceTimePriorityForAsks() {
        OrderPool pool = new OrderPool(32);
        EngineMetrics metrics = new EngineMetrics();
        EventLog eventLog = new EventLog();
        MatchingEngine engine = new MatchingEngine(pool, metrics, eventLog);

        engine.process(Order.limit(1L, Order.Side.SELL, 10_000L, 5L, 1L));
        engine.process(Order.limit(2L, Order.Side.SELL, 10_000L, 7L, 2L));
        engine.process(Order.limit(3L, Order.Side.BUY, 10_000L, 6L, 3L));

        assertEquals(3L, metrics.getProcessedCommands());
        assertEquals(2L, metrics.getTradeCount());
        assertEquals(6L, metrics.getTradedQuantity());
        assertFalse(engine.containsRestingOrder(1L));
        assertTrue(engine.containsRestingOrder(2L));
        assertEquals("100.00", engine.snapshot(3).bestAsk());
        assertEquals(6L, engine.snapshot(3).bestAskQuantity());
    }

    @Test
    void marketOrderShouldNotRestResidual() {
        OrderPool pool = new OrderPool(32);
        EngineMetrics metrics = new EngineMetrics();
        MatchingEngine engine = new MatchingEngine(pool, metrics, new EventLog());

        engine.process(Order.limit(10L, Order.Side.SELL, 10_100L, 4L, 1L));
        engine.process(Order.market(11L, Order.Side.BUY, 10L, 2L));

        assertEquals(2L, metrics.getProcessedCommands());
        assertEquals(1L, metrics.getTradeCount());
        assertEquals(4L, metrics.getTradedQuantity());
        assertEquals(1L, metrics.getRejectedCommands());
        assertFalse(engine.containsRestingOrder(11L));
        assertEquals(0L, engine.snapshot(3).restingBuyOrders());
        assertEquals(0L, engine.snapshot(3).restingSellOrders());
    }

    @Test
    void cancelShouldRemoveRestingOrder() {
        OrderPool pool = new OrderPool(32);
        EngineMetrics metrics = new EngineMetrics();
        MatchingEngine engine = new MatchingEngine(pool, metrics, new EventLog());

        engine.process(Order.limit(20L, Order.Side.BUY, 9_950L, 3L, 1L));
        assertTrue(engine.containsRestingOrder(20L));

        engine.process(Order.cancel(21L, 20L, 2L));

        assertFalse(engine.containsRestingOrder(20L));
        assertEquals(1L, metrics.getCancelledOrders());
        assertEquals(0L, engine.snapshot(3).restingBuyOrders());
    }
}
