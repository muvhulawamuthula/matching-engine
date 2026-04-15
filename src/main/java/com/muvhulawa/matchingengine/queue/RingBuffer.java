package com.muvhulawa.matchingengine.queue;

import com.muvhulawa.matchingengine.model.Order;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Bounded multi-producer, single-consumer ring buffer.
 *
 * Uses a per-slot sequence algorithm so producers can safely claim slots
 * concurrently without overwriting each other, while a single consumer drains
 * in order.
 */
public class RingBuffer {

    private final AtomicReferenceArray<Order> buffer;
    private final AtomicLongArray sequences;
    private final int size;
    private final int mask;

    private final AtomicLong enqueueSequence = new AtomicLong();
    private final AtomicLong dequeueSequence = new AtomicLong();

    public RingBuffer(int size) {
        if (Integer.bitCount(size) != 1) {
            throw new IllegalArgumentException("Size must be power of 2");
        }

        this.size = size;
        this.mask = size - 1;
        this.buffer = new AtomicReferenceArray<>(size);
        this.sequences = new AtomicLongArray(size);

        for (int i = 0; i < size; i++) {
            sequences.set(i, i);
        }
    }

    public boolean publish(Order order) {
        while (true) {
            long currentEnqueue = enqueueSequence.get();
            int index = (int) (currentEnqueue & mask);
            long sequence = sequences.get(index);
            long difference = sequence - currentEnqueue;

            if (difference == 0) {
                if (enqueueSequence.compareAndSet(currentEnqueue, currentEnqueue + 1)) {
                    buffer.set(index, order);
                    sequences.set(index, currentEnqueue + 1);
                    return true;
                }
            } else if (difference < 0) {
                return false;
            } else {
                Thread.onSpinWait();
            }
        }
    }

    public Order consume() {
        while (true) {
            long currentDequeue = dequeueSequence.get();
            int index = (int) (currentDequeue & mask);
            long sequence = sequences.get(index);
            long difference = sequence - (currentDequeue + 1);

            if (difference == 0) {
                if (dequeueSequence.compareAndSet(currentDequeue, currentDequeue + 1)) {
                    Order order = buffer.get(index);
                    buffer.set(index, null);
                    sequences.set(index, currentDequeue + size);
                    return order;
                }
            } else if (difference < 0) {
                return null;
            } else {
                Thread.onSpinWait();
            }
        }
    }

    public boolean isEmpty() {
        return dequeueSequence.get() >= enqueueSequence.get();
    }

    public int capacity() {
        return size;
    }
}
