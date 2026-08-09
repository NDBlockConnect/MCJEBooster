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

import com.mcjebooster.core.DynamicTickQueue;
import com.mcjebooster.core.DynamicTickQueue.DrainStats;
import com.mcjebooster.core.DynamicTickQueue.TickTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v26.0-Alpha.5: tests for the dynamic tick queue that replaces static
 * region pre-allocation with a shared work queue (real work-stealing).
 *
 * @author StarsailsClover
 * @since v26.0-Alpha.5
 */
class DynamicTickQueueTest {

    private static TickTask task(String id, Runnable body) {
        return new TickTask() {
            @Override public String id() { return id; }
            @Override public void run() { body.run(); }
        };
    }

    @Test
    @DisplayName("offer/poll preserve FIFO order and ignore nulls")
    void testOfferPollOrder() {
        DynamicTickQueue queue = new DynamicTickQueue();
        AtomicInteger sequence = new AtomicInteger();
        queue.offer(task("a", sequence::incrementAndGet));
        queue.offer(null); // ignored
        queue.offer(task("b", sequence::incrementAndGet));

        assertEquals(2, queue.pendingCount());
        assertEquals("a", queue.poll().id());
        assertEquals("b", queue.poll().id());
        assertNull(queue.poll());
        assertEquals(0, queue.pendingCount());
    }

    @Test
    @DisplayName("offerAll skips nulls and returns enqueued count")
    void testOfferAll() {
        DynamicTickQueue queue = new DynamicTickQueue();
        List<TickTask> batch = new ArrayList<>();
        batch.add(task("x", () -> { }));
        batch.add(null);
        batch.add(task("y", () -> { }));
        assertEquals(2, queue.offerAll(batch));
        assertEquals(0, queue.offerAll(null));
        assertEquals(0, queue.offerAll(Collections.emptyList()));
    }

    @Test
    @DisplayName("drain executes every task exactly once")
    void testDrainExecutesAll() throws Exception {
        DynamicTickQueue queue = new DynamicTickQueue();
        Set<String> seen = ConcurrentHashMap.newKeySet();
        List<TickTask> tasks = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            final String id = "task-" + i;
            tasks.add(task(id, () -> assertTrue(seen.add(id), "duplicate execution of " + id)));
        }
        queue.offerAll(tasks);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            DrainStats stats = queue.drain(pool, 4, 5000);
            assertEquals(200, stats.queued());
            assertEquals(200, stats.executed());
            assertEquals(0, stats.failed());
            assertEquals(200, seen.size());
            assertEquals(0, queue.pendingCount());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("drain isolates failing tasks without aborting")
    void testDrainIsolatesFailures() throws Exception {
        DynamicTickQueue queue = new DynamicTickQueue();
        AtomicInteger completed = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            final boolean fail = (i % 3 == 0);
            queue.offer(task("t" + i, () -> {
                if (fail) {
                    throw new IllegalStateException("boom");
                }
                completed.incrementAndGet();
            }));
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            DrainStats stats = queue.drain(pool, 2, 5000);
            assertEquals(10, stats.queued());
            assertEquals(4, stats.failed()); // i in {0,3,6,9}
            assertEquals(6, completed.get());
            assertEquals(6, stats.executed());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("drain on empty queue returns zero stats without touching executor")
    void testDrainEmpty() {
        DynamicTickQueue queue = new DynamicTickQueue();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            DrainStats stats = queue.drain(pool, 2, 100);
            assertEquals(0, stats.queued());
            assertEquals(0, stats.executed());
            assertEquals(0, stats.wallNanos());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("drain clamps worker count to at least one loop")
    void testDrainWorkerClamp() throws Exception {
        DynamicTickQueue queue = new DynamicTickQueue();
        AtomicInteger ran = new AtomicInteger();
        queue.offer(task("only", ran::incrementAndGet));

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            DrainStats stats = queue.drain(pool, 0, 1000);
            assertEquals(1, stats.executed());
            assertEquals(1, ran.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
