package com.muvhulawa.matchingengine.queue;

import com.muvhulawa.matchingengine.model.Order;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingBufferTest {

    @Test
    void shouldHandleMultipleProducersAndSingleConsumerWithoutLosingOrders() throws Exception {
        int producerCount = 4;
        int ordersPerProducer = 2_000;
        RingBuffer buffer = new RingBuffer(1024);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(producerCount);
        AtomicInteger produced = new AtomicInteger();
        Set<Long> consumedIds = ConcurrentHashMap.newKeySet();

        Thread consumer = new Thread(() -> {
            while (consumedIds.size() < producerCount * ordersPerProducer) {
                Order order = buffer.consume();
                if (order != null) {
                    consumedIds.add(order.getId());
                } else {
                    Thread.onSpinWait();
                }
            }
        });
        consumer.start();

        for (int p = 0; p < producerCount; p++) {
            final int producerId = p;
            Thread producer = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < ordersPerProducer; i++) {
                        long id = producerId * 1_000_000L + i;
                        Order order = new Order();
                        order.reset(id, Order.Side.BUY, 100.0, 1L, i);
                        while (!buffer.publish(order)) {
                            Thread.onSpinWait();
                        }
                        produced.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            producer.start();
        }

        start.countDown();
        done.await();
        consumer.join();

        assertEquals(producerCount * ordersPerProducer, produced.get());
        assertEquals(producerCount * ordersPerProducer, consumedIds.size());
        assertTrue(buffer.isEmpty());
    }
}
