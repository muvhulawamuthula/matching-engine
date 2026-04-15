package com.muvhulawa.matchingengine.snapshot;

import com.muvhulawa.matchingengine.events.Trade;
import com.muvhulawa.matchingengine.metrics.EngineMetrics;
import com.muvhulawa.matchingengine.orderbook.OrderBookSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotWriterTest {

    @Test
    void shouldWriteHumanReadableSnapshotFile() throws Exception {
        OrderBookSnapshot snapshot = new OrderBookSnapshot(
                "10.00", 5L, "10.01", 7L, 1L, 2L,
                List.of("10.00 x 5"), List.of("10.01 x 7")
        );
        EngineMetrics metrics = new EngineMetrics();
        metrics.incrementProcessed();
        Path file = Files.createTempDirectory("ome-snapshot").resolve("snapshot.txt");

        SnapshotWriter.write(file, snapshot, metrics, List.of(new Trade(1L, 2L, 1000L, 3L, 5L, true)));
        String text = Files.readString(file);

        assertTrue(text.contains("ORDER MATCHING ENGINE SNAPSHOT"));
        assertTrue(text.contains("Best bid: 10.00"));
        assertTrue(text.contains("Recent trades:"));
    }
}
