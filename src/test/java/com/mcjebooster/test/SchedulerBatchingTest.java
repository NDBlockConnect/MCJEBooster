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

import com.mcjebooster.adapter.VersionAdapter;
import com.mcjebooster.scheduler.RegionScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v26.0-Alpha.4: tests for refined region granularity resolution and
 * the batched tick-wait partitioning that replaces the single global
 * completion barrier.
 *
 * @author StarsailsClover
 * @since v26.0-Alpha.4
 */
class SchedulerBatchingTest {

    private static final String REGION_SIZE_PROPERTY = "mcjebooster.regionSize";

    @AfterEach
    void clearProperty() {
        System.clearProperty(REGION_SIZE_PROPERTY);
    }

    @Test
    @DisplayName("Default region size is the refined 8x8 granularity")
    void testDefaultRegionSize() {
        assertEquals(8, RegionScheduler.resolveRegionSize(null));
    }

    @Test
    @DisplayName("System property override wins and is clamped at minimum 4")
    void testRegionSizePropertyOverride() {
        System.setProperty(REGION_SIZE_PROPERTY, "4");
        assertEquals(4, RegionScheduler.resolveRegionSize(null));

        System.setProperty(REGION_SIZE_PROPERTY, "2"); // below minimum -> ignored
        assertEquals(8, RegionScheduler.resolveRegionSize(null));

        System.setProperty(REGION_SIZE_PROPERTY, "not-a-number"); // ignored
        assertEquals(8, RegionScheduler.resolveRegionSize(null));
    }

    @Test
    @DisplayName("Adapter-provided region size is respected when valid")
    void testAdapterRegionSize() {
        VersionAdapter adapter = new StubAdapter(32);
        assertEquals(32, RegionScheduler.resolveRegionSize(adapter));

        VersionAdapter invalidAdapter = new StubAdapter(2); // below minimum
        assertEquals(8, RegionScheduler.resolveRegionSize(invalidAdapter));
    }

    @Test
    @DisplayName("partitionBatches splits evenly and handles remainders")
    void testPartitionBatches() {
        List<Integer> items = Arrays.asList(1, 2, 3, 4, 5);

        List<List<Integer>> batches = RegionScheduler.partitionBatches(items, 2);
        assertEquals(3, batches.size());
        assertEquals(Arrays.asList(1, 2), batches.get(0));
        assertEquals(Arrays.asList(3, 4), batches.get(1));
        assertEquals(Collections.singletonList(5), batches.get(2));
    }

    @Test
    @DisplayName("partitionBatches handles empty input and clamps batch size")
    void testPartitionBatchesEdgeCases() {
        assertTrue(RegionScheduler.partitionBatches(Collections.emptyList(), 4).isEmpty());
        assertTrue(RegionScheduler.partitionBatches(null, 4).isEmpty());

        List<List<Integer>> single = RegionScheduler.partitionBatches(Arrays.asList(1, 2, 3), 0);
        assertEquals(3, single.size()); // batchSize clamped to 1
    }

    @Test
    @DisplayName("partitionBatches preserves order for large region counts")
    void testPartitionOrderPreserved() {
        List<Integer> items = new java.util.ArrayList<>();
        for (int i = 0; i < 64; i++) {
            items.add(i);
        }
        List<List<Integer>> batches = RegionScheduler.partitionBatches(items, 8);
        assertEquals(8, batches.size());
        int seen = 0;
        for (List<Integer> batch : batches) {
            for (Integer value : batch) {
                assertEquals(seen++, value.intValue());
            }
        }
        assertEquals(64, seen);
    }

    /** Minimal adapter stub that only controls the region size. */
    private static final class StubAdapter implements VersionAdapter {
        private final int regionSize;

        StubAdapter(int regionSize) {
            this.regionSize = regionSize;
        }

        @Override public String getAdapterId() { return "stub"; }
        @Override public String getMinecraftVersion() { return "test"; }
        @Override public LoaderType getLoaderType() { return LoaderType.VANILLA; }
        @Override public String getLoaderVersion() { return null; }
        @Override public int getRequiredJavaVersion() { return 17; }
        @Override public java.util.Map<String, String> getClassMappings() { return Collections.emptyMap(); }
        @Override public java.util.Map<String, String> getMethodMappings() { return Collections.emptyMap(); }
        @Override public java.util.Map<String, String> getFieldMappings() { return Collections.emptyMap(); }
        @Override public java.util.Map<String, String> getMethodDescriptors() { return Collections.emptyMap(); }
        @Override public String getTickMethodTarget() { return null; }
        @Override public String getEntityTickMethodTarget() { return null; }
        @Override public String getBlockTickMethodTarget() { return null; }
        @Override public String getChunkProviderClass() { return null; }
        @Override public String getWorldClass() { return null; }
        @Override public String getEntityClass() { return null; }
        @Override public String getEntityListField() { return null; }
        @Override public int getRegionSize() { return regionSize; }
        @Override public int getRecommendedWorkerCount() { return 2; }
        @Override public long getTickTimeoutMs() { return 45; }
        @Override public boolean supportsFeature(Feature feature) { return false; }
        @Override public java.util.Set<Feature> getSupportedFeatures() { return Collections.emptySet(); }
        @Override public String[] getRequiredJvmArgs() { return new String[0]; }
        @Override public boolean validate() { return true; }
        @Override public String getAdapterVersion() { return "1.0"; }
    }
}
