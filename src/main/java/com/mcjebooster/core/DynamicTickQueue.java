/*
 * MCJEBooster - Minecraft Java Edition Multi-Core Optimization Engine
 * Copyright (C) 2026 StarsailsClover
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 */

package com.mcjebooster.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dynamic tick task queue (v26.0-Alpha.5).
 *
 * FACT.md Phase-1 item 4: with static region pre-allocation the
 * ForkJoinPool never gets a chance to work-steal, because every worker
 * owns a fixed region up front. This queue inverts the model: tasks are
 * pushed into a shared {@link ConcurrentLinkedQueue} and each worker
 * polls until the queue is empty, which yields natural dynamic load
 * balancing without per-task coordination.
 *
 * The queue is intentionally minimal and allocation-light on the hot
 * path: {@link #offer} and {@link #poll} are lock-free.
 *
 * @author StarsailsClover
 * @since v26.0-Alpha.5
 */
public final class DynamicTickQueue {

    /** A unit of tick work pulled from the queue by workers. */
    public interface TickTask {
        /** Stable identifier used in diagnostics. */
        String id();

        /** Executes the task; exceptions are isolated by the queue. */
        void run();
    }

    /** Immutable execution report produced by {@link #drain}. */
    public static final class DrainStats {
        private final int queued;
        private final int executed;
        private final int failed;
        private final long wallNanos;
        private final long taskNanos;

        DrainStats(int queued, int executed, int failed, long wallNanos, long taskNanos) {
            this.queued = queued;
            this.executed = executed;
            this.failed = failed;
            this.wallNanos = wallNanos;
            this.taskNanos = taskNanos;
        }

        public int queued() { return queued; }
        public int executed() { return executed; }
        public int failed() { return failed; }
        public long wallNanos() { return wallNanos; }
        public long taskNanos() { return taskNanos; }
    }

    private final ConcurrentLinkedQueue<TickTask> tasks = new ConcurrentLinkedQueue<>();

    /**
     * Enqueues a single task. Null tasks are ignored.
     *
     * @param task the task to enqueue
     */
    public void offer(TickTask task) {
        if (task != null) {
            tasks.offer(task);
        }
    }

    /**
     * Enqueues a batch of tasks. Null collection entries are skipped.
     *
     * @param batch the tasks to enqueue
     * @return number of tasks actually enqueued
     */
    public int offerAll(Collection<? extends TickTask> batch) {
        if (batch == null || batch.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (TickTask task : batch) {
            if (task != null) {
                tasks.offer(task);
                count++;
            }
        }
        return count;
    }

    /** Number of tasks currently waiting in the queue. */
    public int pendingCount() {
        return tasks.size();
    }

    /** Removes and returns the next task, or null when empty. */
    public TickTask poll() {
        return tasks.poll();
    }

    /**
     * Drains the queue using the given executor.
     *
     * {@code workerCount} polling loops run concurrently; each loop pulls
     * tasks until the queue is empty, so fast workers automatically absorb
     * load from slow workers (dynamic work-stealing semantics on top of a
     * shared queue). A failing task is counted but never aborts the drain.
     *
     * @param executor    the executor that runs the polling loops
     * @param workerCount number of concurrent polling loops (&gt;= 1)
     * @param timeoutMs   maximum wall time to wait for completion
     * @return drain statistics
     */
    public DrainStats drain(Executor executor, int workerCount, long timeoutMs) {
        long start = System.nanoTime();
        int queued = tasks.size();
        if (queued == 0) {
            return new DrainStats(0, 0, 0, 0, 0);
        }

        int loops = Math.max(1, workerCount);
        AtomicLong executed = new AtomicLong();
        AtomicLong failed = new AtomicLong();
        AtomicLong taskNanos = new AtomicLong();

        List<CompletableFuture<Void>> futures = new ArrayList<>(loops);
        for (int i = 0; i < loops; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                TickTask task;
                while ((task = tasks.poll()) != null) {
                    long taskStart = System.nanoTime();
                    try {
                        task.run();
                        executed.incrementAndGet();
                    } catch (Throwable t) {
                        failed.incrementAndGet();
                    } finally {
                        taskNanos.addAndGet(System.nanoTime() - taskStart);
                    }
                }
            }, executor));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            for (CompletableFuture<Void> future : futures) {
                future.cancel(true);
            }
        } catch (Exception e) {
            // Interruption or execution exception: report what completed.
        }

        return new DrainStats(
            queued,
            executed.intValue(),
            failed.intValue(),
            System.nanoTime() - start,
            taskNanos.get()
        );
    }
}
