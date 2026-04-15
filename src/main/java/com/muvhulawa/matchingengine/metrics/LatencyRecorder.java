package com.muvhulawa.matchingengine.metrics;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class LatencyRecorder {
    private final long[] samples;
    private final int stride;
    private final AtomicLong seen = new AtomicLong();
    private final AtomicInteger stored = new AtomicInteger();

    public LatencyRecorder(int maxSamples, int stride) {
        this.samples = new long[maxSamples];
        this.stride = Math.max(1, stride);
    }

    public void record(long nanos) {
        long index = seen.incrementAndGet();
        if (index % stride != 0) {
            return;
        }
        int slot = stored.getAndIncrement();
        if (slot < samples.length) {
            samples[slot] = nanos;
        }
    }

    public String percentileSummaryMicros() {
        int count = Math.min(stored.get(), samples.length);
        if (count == 0) {
            return "no samples";
        }
        long[] copy = Arrays.copyOf(samples, count);
        Arrays.sort(copy);
        return "p50=" + micros(percentile(copy, 0.50)) + "us, p95=" + micros(percentile(copy, 0.95)) +
                "us, p99=" + micros(percentile(copy, 0.99)) + "us";
    }

    private long percentile(long[] sorted, double p) {
        int index = Math.min(sorted.length - 1, (int) Math.ceil(p * sorted.length) - 1);
        return sorted[index];
    }

    private String micros(long nanos) {
        return String.format("%.2f", nanos / 1_000.0);
    }
}
