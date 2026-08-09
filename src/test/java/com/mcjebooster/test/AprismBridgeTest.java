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

import com.mcjebooster.client.AprismBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import teststub.aprism.FakeHookRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v26.1-Alpha.3: Aprism bridge tests. The bridge must be reflective,
 * compile-safe, and degrade to safe no-ops when Aprism is absent.
 *
 * @author StarsailsClover
 * @since v26.1-Alpha.3
 */
class AprismBridgeTest {

    private static final String REGISTRY_PROPERTY = "mcjebooster.aprism.registryClass";
    private AprismBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = AprismBridge.getInstance();
        bridge.resetState();
        FakeHookRegistry.reset();
        System.clearProperty(REGISTRY_PROPERTY);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(REGISTRY_PROPERTY);
    }

    @Test
    @DisplayName("Bridge reports unavailable when Aprism is absent (default class)")
    void testUnavailableWithoutAprism() {
        assertFalse(bridge.isAvailable(),
            "real Aprism classes are not on the test classpath");
        // Every operation must degrade to a safe no-op
        assertFalse(bridge.registerHook("net/minecraft/client/Minecraft", "tick", "()V", () -> { }));
        assertFalse(bridge.unregisterHook("net/minecraft/client/Minecraft", "tick", "()V", () -> { }));
        assertFalse(bridge.clearAllHooks());
        assertEquals(0, bridge.retransformViaAprism(null));
        assertEquals(0, bridge.getRegisteredHookCount());
        assertNotNull(bridge.getLastError(), "failed ops must record a diagnostic");
    }

    @Test
    @DisplayName("Property override redirects the bridge to a compatible registry")
    void testRegistryOverride() {
        System.setProperty(REGISTRY_PROPERTY, "teststub.aprism.FakeHookRegistry");
        assertTrue(bridge.isAvailable());
        assertEquals("teststub.aprism.FakeHookRegistry", AprismBridge.registryClassName());
    }

    @Test
    @DisplayName("registerHook routes through the Aprism registry")
    void testRegisterHook() {
        System.setProperty(REGISTRY_PROPERTY, "teststub.aprism.FakeHookRegistry");
        boolean ok = bridge.registerHook("net/minecraft/client/Minecraft",
            "tick", "()V", () -> { });
        assertTrue(ok);
        assertEquals(1, bridge.getRegisteredHookCount());
        assertTrue(FakeHookRegistry.REGISTERED.contains(
            "net/minecraft/client/Minecraft.tick()V"));
    }

    @Test
    @DisplayName("unregisterHook and clearAllHooks route through the registry")
    void testUnregisterAndClear() {
        System.setProperty(REGISTRY_PROPERTY, "teststub.aprism.FakeHookRegistry");
        Runnable listener = () -> { };
        assertTrue(bridge.registerHook("C", "m", "()V", listener));
        assertTrue(bridge.unregisterHook("C", "m", "()V", listener));
        assertTrue(FakeHookRegistry.UNREGISTERED.contains("C.m()V"));

        assertTrue(bridge.clearAllHooks());
        assertEquals(1, FakeHookRegistry.clearCount);
        assertEquals(0, bridge.getRegisteredHookCount());
    }

    @Test
    @DisplayName("Null arguments never reach the registry")
    void testNullGuards() {
        System.setProperty(REGISTRY_PROPERTY, "teststub.aprism.FakeHookRegistry");
        assertFalse(bridge.registerHook(null, "m", "()V", () -> { }));
        assertFalse(bridge.registerHook("C", null, "()V", () -> { }));
        assertFalse(bridge.registerHook("C", "m", "()V", null));
        assertEquals(0, bridge.getRegisteredHookCount());
        assertTrue(FakeHookRegistry.REGISTERED.isEmpty());
    }

    @Test
    @DisplayName("Bridge info summarizes availability and state")
    void testBridgeInfo() {
        String info = bridge.getBridgeInfo();
        assertTrue(info.contains("AprismBridge"));
        assertTrue(info.contains("available="));
        assertTrue(info.contains("registeredHooks="));
    }

    @Test
    @DisplayName("Default class names follow the Aprism lowlevel contract")
    void testDefaultClassNames() {
        assertEquals("com.aprism.loader.lowlevel.MethodHookRegistry",
            AprismBridge.DEFAULT_REGISTRY_CLASS);
        assertEquals("com.aprism.loader.lowlevel.ClassRedefiner",
            AprismBridge.DEFAULT_REDEFINER_CLASS);
    }
}
