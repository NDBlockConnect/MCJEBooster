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

import com.mcjebooster.config.ConfigManager;
import com.mcjebooster.config.ConfigManager.Config;
import com.mcjebooster.config.ConfigManager.Preset;
import com.mcjebooster.util.BoosterVersion;
import com.mcjebooster.util.ReflectionHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v26.0-Alpha.2: test coverage for the configuration system,
 * the central version constant, and the reflection helper.
 *
 * @author StarsailsClover
 * @since v26.0-Alpha.2
 */
class ConfigManagerTest {

    @BeforeEach
    void clearReflectionCache() {
        ReflectionHelper.clearCache();
    }

    @Test
    @DisplayName("Config returns typed values and falls back to defaults")
    void testTypedAccessorsAndDefaults() {
        Map<String, Object> values = new HashMap<>();
        values.put("performance.workerCount", 4);
        values.put("health.minTPS", 7.5);
        values.put("health.autoRollback", false);
        values.put("logging.level", "DEBUG");
        Config config = new Config(values);

        assertEquals(4, config.getInt("performance.workerCount"));
        assertEquals(7.5, config.getDouble("health.minTPS"), 1e-9);
        assertFalse(config.getBoolean("health.autoRollback"));
        assertEquals("DEBUG", config.getString("logging.level"));
        // Missing key falls back to DEFAULTS (tickTimeout default is 45)
        assertEquals(45, config.getInt("performance.tickTimeout"));
    }

    @Test
    @DisplayName("Config numeric coercion between int and double")
    void testNumericCoercion() {
        Map<String, Object> values = new HashMap<>();
        values.put("asInt", 3.9);
        values.put("asDouble", 7);
        Config config = new Config(values);

        assertEquals(3, config.getInt("asInt"));
        assertEquals(7.0, config.getDouble("asDouble"), 1e-9);
    }

    @Test
    @DisplayName("All presets produce a valid configuration")
    void testPresets() {
        for (Preset preset : Preset.values()) {
            Config config = preset.createConfig();
            assertNotNull(preset.getDescription());
            assertTrue(config.getInt("performance.workerCount") >= 1,
                preset + " workerCount must be >= 1");
            assertTrue(config.getInt("performance.regionSize") >= 8,
                preset + " regionSize must be >= 8");
            assertTrue(config.getInt("performance.tickTimeout") > 0,
                preset + " tickTimeout must be positive");
        }
    }

    @Test
    @DisplayName("ConfigManager singleton exposes configuration after getConfig")
    void testSingletonConfigAccess() {
        ConfigManager manager = ConfigManager.getInstance();
        assertNotNull(manager.getConfig());
        assertSame(manager, ConfigManager.getInstance());
    }

    @Test
    @DisplayName("BoosterVersion follows the v<Year>.<minor>[-Alpha.<n>] contract")
    void testVersionContract() {
        String version = BoosterVersion.VERSION;
        assertNotNull(version);
        assertTrue(version.matches("^v\\d{2}\\.\\d+(-Alpha\\.\\d+)?$"),
            "version must match v<Year>.<minor>[-Alpha.<n>]: " + version);
        assertEquals(BoosterVersion.MAJOR_LINE,
            version.substring(0, version.indexOf('.') == -1 ? version.length() : 3));
        assertFalse(version.contains("Alpha.10"), "Alpha.10 must never exist");
        assertNotNull(BoosterVersion.stage());
        // Stage semantics: Alpha.9 is the release candidate
        if (version.endsWith("-Alpha.9")) {
            assertEquals(BoosterVersion.Stage.RELEASE_CANDIDATE, BoosterVersion.stage());
        } else if (version.contains("-Alpha.")) {
            assertEquals(BoosterVersion.Stage.ALPHA, BoosterVersion.stage());
        } else {
            assertEquals(BoosterVersion.Stage.GA, BoosterVersion.stage());
        }
        assertTrue(BoosterVersion.banner().contains(version));
    }

    // ---- ReflectionHelper coverage ---------------------------------------

    /** Simple fixture with private state for reflection tests. */
    static class Fixture {
        private int counter = 0;
        private String label = "initial";

        private int increment(int delta) {
            counter += delta;
            return counter;
        }

        private String getLabel() {
            return label;
        }
    }

    @Test
    @DisplayName("ReflectionHelper reads and writes private fields")
    void testFieldAccess() {
        Fixture fixture = new Fixture();

        assertEquals(0, ((Number) ReflectionHelper.getFieldValue(fixture, "counter")).intValue());
        assertTrue(ReflectionHelper.setFieldValue(fixture, 42, "counter"));
        assertEquals(42, ((Number) ReflectionHelper.getFieldValue(fixture, "counter")).intValue());

        assertTrue(ReflectionHelper.setFieldValue(fixture, "updated", "label"));
        assertEquals("updated", ReflectionHelper.getFieldValue(fixture, "label"));
    }

    @Test
    @DisplayName("ReflectionHelper resolves the first existing candidate name")
    void testCandidateResolution() {
        Fixture fixture = new Fixture();
        assertEquals("initial",
            ReflectionHelper.getFieldValue(fixture, "missingField", "label"));
        assertEquals("initial", ReflectionHelper.invokeMethod(fixture,
            new String[] { "noSuchMethod", "getLabel" }));
    }

    @Test
    @DisplayName("ReflectionHelper invokes private methods with arguments")
    void testMethodInvocation() {
        Fixture fixture = new Fixture();
        Object result = ReflectionHelper.invokeMethod(fixture, new String[] { "increment" }, 5);
        assertEquals(5, ((Number) result).intValue());
        result = ReflectionHelper.invokeMethod(fixture, new String[] { "increment" }, 3);
        assertEquals(8, ((Number) result).intValue());
    }

    @Test
    @DisplayName("ReflectionHelper cache stats are reported")
    void testCacheStats() {
        Fixture fixture = new Fixture();
        ReflectionHelper.getFieldValue(fixture, "counter");
        String stats = ReflectionHelper.getCacheStats();
        assertNotNull(stats);
        assertFalse(stats.isEmpty());
        assertTrue(stats.contains("Handles"), "stats must report MethodHandle cache");
    }

    @Test
    @DisplayName("invokeMethod hot path returns stable results across repeated calls")
    void testMethodHandleRepeatStability() {
        Fixture fixture = new Fixture();
        // First call resolves and caches the MethodHandle, subsequent calls
        // must keep returning identical semantics (regression guard for the
        // v26.0-Alpha.3 MethodHandle migration).
        for (int i = 1; i <= 50; i++) {
            Object result = ReflectionHelper.invokeMethod(fixture, new String[] { "increment" }, 1);
            assertEquals(i, ((Number) result).intValue(), "iteration " + i);
        }
        assertEquals(50, ((Number) ReflectionHelper.getFieldValue(fixture, "counter")).intValue());
    }
}
