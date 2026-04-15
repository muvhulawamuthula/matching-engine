package com.muvhulawa.matchingengine.queue;

import com.muvhulawa.matchingengine.model.Order;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class OrderQueue {

    private final BlockingQueue<Order> queue;

    public OrderQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    public void submit(Order order) throws InterruptedException {
        queue.put(order); // blocks if full → backpressure
    }

    public Order take() throws InterruptedException {
        return queue.take();
    }
}