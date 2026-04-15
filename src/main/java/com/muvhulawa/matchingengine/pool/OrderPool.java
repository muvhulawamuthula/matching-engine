package com.muvhulawa.matchingengine.pool;

import com.muvhulawa.matchingengine.model.Order;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class OrderPool {

    private final BlockingQueue<Order> pool;

    public OrderPool(int size) {
        this.pool = new ArrayBlockingQueue<>(size);

        for (int i = 0; i < size; i++) {
            pool.offer(new Order());
        }
    }

    public Order borrow() {
        Order order = pool.poll();
        return order != null ? order : new Order();
    }

    public void release(Order order) {
        if (order != null) {
            pool.offer(order);
        }
    }
}