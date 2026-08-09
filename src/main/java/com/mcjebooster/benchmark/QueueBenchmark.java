/*
 * MCJEBooster - Minecraft Java Edition Multi-Core Optimization Engine
 * Copyright (C) 2026 StarsailsClover
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 */

package com.mcjebooster.benchmark;

import com.mcjebooster.core.DynamicTickQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * v26.0-Alpha.6 performance validation baseline.
 *
 * Measures the two region-tick scheduling strategies under a skewed
 * workload that mimics a real Minecraft world (a few hot regions next
 * to many idle regions):
 *
 * <ol>
 *   <li><b>static-futures</b> — one fixed future per region, single
 *       global {@code allOf().get()} wait (pre-Alpha.4 behaviour)</li>
 *   <li><b>batched-futures</b> — Alpha.4 batched waits</li>
 *   <li><b>dynamic-queue</b> — Alpha.5 shared work queue drain</li>
 * </ol>
 *
 * Output is a single machine-parseable line prefixed with
 * {@code MCJEBoosterQueueBench} so CI can trend it over time.
 *
 * @author StarsailsClover
 * @since v26.0-Alpha.6
 */
public final class QueueBenchmark {

    private QueueBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        int regions = intArg(args, 0, 64);
        int hotRegions = intArg(args, 1, 4);
        long hotWorkNanos = longArg(args, 2, 2_000_000L); // ~2ms hot task
        long coldWorkNanos = longArg(args, 3, 20_000L);   // ~0.02ms cold task
        int rounds = intArg(args, 4, 7);
        int warmup = intArg(args, 5, 2);
        int workers = intArg(args, 6, Math.max(2, Runtime.getRuntime().availableProcessors() - 1));

        ExecutorService pool = Executors.newWorkStealingPool(workers);
        try {
            long staticBest = Long.MAX_VALUE;
            long batchedBest = Long.MAX_VALUE;
            long queueBest = Long.MAX_VALUE;

            for (int round = -warmup; round < rounds; round++) {
                boolean measure = round >= 0;

                long t1 = runStaticFutures(pool, regions, hotRegions, hotWorkNanos, coldWorkNanos);
                long t2 = runBatchedFutures(pool, regions, hotRegions, hotWorkNanos, coldWorkNanos, workers);
                long t3 = runDynamicQueue(pool, regions, hotRegions, hotWorkNanos, coldWorkNanos, workers);

                if (measure) {
                    staticBest = Math.min(staticBest, t1);
                    batchedBest = Math.min(batchedBest, t2);
                    queueBest = Math.min(queueBest, t3);
                }
            }

            System.out.printf(java.util.Locale.ROOT,
                "MCJEBoosterQueueBench regions=%d hot=%d workers=%d rounds=%d "
                    + "staticMs=%.3f batchedMs=%.3f queueMs=%.3f "
                    + "queueSpeedupVsStatic=%.3f queueSpeedupVsBatched=%.3f%n",
                regions, hotRegions, workers, rounds,
                staticBest / 1e6, batchedBest / 1e6, queueBest / 1e6,
                staticBest / (double) queueBest, batchedBest / (double) queueBest);
        } finally {
            pool.shutdownNow();
        }
    }

    private static long runStaticFutures(ExecutorService pool, int regions, int hot,
                                         long hotNanos, long coldNanos) throws Exception {
        List<CompletableFuture<Void>> futures = new ArrayList<>(regions);
        long start = System.nanoTime();
        for (int i = 0; i < regions; i++) {
            final long work = i < hot ? hotNanos : coldNanos;
            futures.add(CompletableFuture.runAsync(() -> spin(work), pool));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
        return System.nanoTime() - start;
    }

    private static long runBatchedFutures(ExecutorService pool, int regions, int hot,
                                          long hotNanos, long coldNanos, int workers) throws Exception {
        int batchSize = Math.max(1, workers * 2);
        long budgetMs = 15_000;
        long start = System.nanoTime();
        for (int from = 0; from < regions; from += batchSize) {
            int to = Math.min(from + batchSize, regions);
            List<CompletableFuture<Void>> futures = new ArrayList<>(to - from);
            for (int i = from; i < to; i++) {
                final long work = i < hot ? hotNanos : coldNanos;
                futures.add(CompletableFuture.runAsync(() -> spin(work), pool));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(budgetMs, TimeUnit.MILLISECONDS);
        }
        return System.nanoTime() - start;
    }

    private static long runDynamicQueue(ExecutorService pool, int regions, int hot,
                                        long hotNanos, long coldNanos, int workers) {
        DynamicTickQueue queue = new DynamicTickQueue();
        List<DynamicTickQueue.TickTask> tasks = new ArrayList<>(regions);
        for (int i = 0; i < regions; i++) {
            final long work = i < hot ? hotNanos : coldNanos;
            tasks.add(new DynamicTickQueue.TickTask() {
                @Override public String id() { return "bench"; }
                @Override public void run() { spin(work); }
            });
        }
        long start = System.nanoTime();
        queue.offerAll(tasks);
        queue.drain(pool, workers, 30_000);
        return System.nanoTime() - start;
    }

    /** CPU-bound synthetic work with a deterministic duration floor. */
    private static void spin(long nanos) {
        long deadline = System.nanoTime() + nanos;
        long value = 0x9E3779B97F4A7C15L;
        while (System.nanoTime() < deadline) {
            value ^= value << 13;
            value ^= value >>> 7;
            value ^= value << 17;
        }
        if (value == Long.MIN_VALUE) {
            System.err.println(value); // never happens; keeps the loop honest
        }
    }

    private static int intArg(String[] args, int index, int def) {
        try {
            return args != null && args.length > index ? Integer.parseInt(args[index]) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long longArg(String[] args, int index, long def) {
        try {
            return args != null && args.length > index ? Long.parseLong(args[index]) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
