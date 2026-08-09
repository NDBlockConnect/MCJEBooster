/*
 * MCJEBooster - Minecraft Java Edition Multi-Core Optimization Engine
 * Copyright (C) 2026 StarsailsClover
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 */

package com.mcjebooster.client;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Client-side performance metrics (v26.1-Alpha.1).
 *
 * On a client process MCJEBooster does not own the tick loop, so the
 * first safe contribution is *observation*: frame and integrated-tick
 * timing collected without modifying any game behavior. All methods are
 * lock-free and allocation-free on the hot path.
 *
 * The metrics window is a ring-free accumulator pair (sum + count) with
 * periodic snapshots; it is intentionally simple so the client bridge
 * (v26.1-Alpha.3+) can report it through either the standalone logger or
 * the Aprism registry.
 *
 * @author StarsailsClover
 * @since v26.1-Alpha.1
 */
public final class ClientMetrics {

    private static final ClientMetrics INSTANCE = new ClientMetrics();

    /** Total frames observed. */
    private final LongAdder frameCount = new LongAdder();

    /** Total frame duration in nanoseconds. */
    private final LongAdder frameNanos = new LongAdder();

    /** Worst frame duration observed (nanoseconds). */
    private final AtomicLong worstFrameNanos = new AtomicLong();

    /** Total client ticks observed (integrated server or client tick). */
    private final LongAdder tickCount = new LongAdder();

    /** Total client tick duration in nanoseconds. */
    private final LongAdder tickNanos = new LongAdder();

    /** Frame count at the last snapshot, for interval rates. */
    private final AtomicLong lastSnapshotFrames = new AtomicLong();

    /** Timestamp of the last snapshot (System.nanoTime). */
    private final AtomicLong lastSnapshotTime = new AtomicLong(System.nanoTime());

    private ClientMetrics() {
    }

    public static ClientMetrics getInstance() {
        return INSTANCE;
    }

    /** Records one rendered frame with its duration. */
    public void recordFrame(long durationNanos) {
        if (durationNanos < 0) {
            return;
        }
        frameCount.increment();
        frameNanos.add(durationNanos);
        updateMax(worstFrameNanos, durationNanos);
    }

    /** Records one client-side tick with its duration. */
    public void recordTick(long durationNanos) {
        if (durationNanos < 0) {
            return;
        }
        tickCount.increment();
        tickNanos.add(durationNanos);
    }

    public long getFrameCount() {
        return frameCount.sum();
    }

    public long getTickCount() {
        return tickCount.sum();
    }

    /** Average frame time in milliseconds, or 0 when no frames recorded. */
    public double getAverageFrameMs() {
        long frames = frameCount.sum();
        return frames == 0 ? 0.0 : frameNanos.sum() / 1_000_000.0 / frames;
    }

    /** Worst frame time in milliseconds, or 0 when no frames recorded. */
    public double getWorstFrameMs() {
        return worstFrameNanos.get() / 1_000_000.0;
    }

    /** Average client tick time in milliseconds, or 0 when none recorded. */
    public double getAverageTickMs() {
        long ticks = tickCount.sum();
        return ticks == 0 ? 0.0 : tickNanos.sum() / 1_000_000.0 / ticks;
    }

    /**
     * Computes the frame rate since the previous snapshot and updates the
     * snapshot window.
     *
     * @return frames per second since the last snapshot, 0 if no frames
     */
    public double snapshotFps() {
        long frames = frameCount.sum();
        long now = System.nanoTime();

        long prevFrames = lastSnapshotFrames.getAndSet(frames);
        long prevTime = lastSnapshotTime.getAndSet(now);

        long elapsedNanos = now - prevTime;
        if (elapsedNanos <= 0) {
            return 0.0;
        }
        return (frames - prevFrames) * 1_000_000_000.0 / elapsedNanos;
    }

    /** One-line diagnostic summary. */
    public String summarize() {
        return String.format(java.util.Locale.ROOT,
            "ClientMetrics frames=%d avgFrameMs=%.3f worstFrameMs=%.3f ticks=%d avgTickMs=%.3f",
            getFrameCount(), getAverageFrameMs(), getWorstFrameMs(),
            getTickCount(), getAverageTickMs());
    }

    /** Resets all accumulators (used between test runs). */
    public void reset() {
        frameCount.reset();
        frameNanos.reset();
        worstFrameNanos.set(0);
        tickCount.reset();
        tickNanos.reset();
        lastSnapshotFrames.set(0);
        lastSnapshotTime.set(System.nanoTime());
    }

    private static void updateMax(AtomicLong target, long value) {
        long current = target.get();
        while (value > current) {
            if (target.compareAndSet(current, value)) {
                return;
            }
            current = target.get();
        }
    }
}
