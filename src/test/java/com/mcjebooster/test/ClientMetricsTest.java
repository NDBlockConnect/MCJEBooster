/*
 * MCJEBooster - Minecraft Java Edition Multi-Core Optimization Engine
 * Copyright (C) 2026 StarsailsClover
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 */

package com.mcjebooster.test;

import com.mcjebooster.client.ClientMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v26.1-Alpha.1: client metrics collector tests. The client rollout's
 * first runtime piece must be measurement-safe: no modification of game
 * behavior, lock-free accumulation, sane aggregation.
 *
 * @author StarsailsClover
 * @since v26.1-Alpha.1
 */
class ClientMetricsTest {

    private ClientMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = ClientMetrics.getInstance();
        metrics.reset();
    }

    @Test
    @DisplayName("Singleton identity is stable")
    void testSingleton() {
        assertSame(metrics, ClientMetrics.getInstance());
    }

    @Test
    @DisplayName("Frame recording aggregates count, average and worst")
    void testFrameRecording() {
        metrics.recordFrame(10_000_000); // 10ms
        metrics.recordFrame(20_000_000); // 20ms
        metrics.recordFrame(50_000_000); // 50ms (worst)

        assertEquals(3, metrics.getFrameCount());
        assertEquals((10 + 20 + 50) / 3.0, metrics.getAverageFrameMs(), 1e-9);
        assertEquals(50.0, metrics.getWorstFrameMs(), 1e-9);
    }

    @Test
    @DisplayName("Negative durations are ignored")
    void testNegativeDurationsIgnored() {
        metrics.recordFrame(-1);
        metrics.recordTick(-1);
        assertEquals(0, metrics.getFrameCount());
        assertEquals(0, metrics.getTickCount());
    }

    @Test
    @DisplayName("Tick recording aggregates count and average")
    void testTickRecording() {
        metrics.recordTick(40_000_000); // 40ms
        metrics.recordTick(60_000_000); // 60ms

        assertEquals(2, metrics.getTickCount());
        assertEquals(50.0, metrics.getAverageTickMs(), 1e-9);
    }

    @Test
    @DisplayName("Empty metrics report zeros, not NaN")
    void testEmptyMetrics() {
        assertEquals(0.0, metrics.getAverageFrameMs(), 1e-9);
        assertEquals(0.0, metrics.getWorstFrameMs(), 1e-9);
        assertEquals(0.0, metrics.getAverageTickMs(), 1e-9);
    }

    @Test
    @DisplayName("Summarize includes all counters")
    void testSummarize() {
        metrics.recordFrame(1_000_000);
        metrics.recordTick(2_000_000);
        String summary = metrics.summarize();
        assertTrue(summary.contains("frames=1"));
        assertTrue(summary.contains("ticks=1"));
        assertTrue(summary.contains("avgFrameMs"));
        assertTrue(summary.contains("avgTickMs"));
    }

    @Test
    @DisplayName("Reset clears all accumulators")
    void testReset() {
        metrics.recordFrame(1_000_000);
        metrics.recordTick(1_000_000);
        metrics.reset();
        assertEquals(0, metrics.getFrameCount());
        assertEquals(0, metrics.getTickCount());
        assertEquals(0.0, metrics.getWorstFrameMs(), 1e-9);
    }

    @Test
    @DisplayName("Concurrent frame recording loses no updates")
    void testConcurrentRecording() throws Exception {
        int threads = 8;
        int perThread = 10_000;
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < perThread; j++) {
                    metrics.recordFrame(1_000);
                }
            });
        }
        for (Thread worker : workers) {
            worker.start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        assertEquals((long) threads * perThread, metrics.getFrameCount());
    }
}
