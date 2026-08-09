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

import com.mcjebooster.util.SideDetector;
import com.mcjebooster.util.SideDetector.Side;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v26.0-Alpha.8: side detection tests for the client-support rollout.
 *
 * @author StarsailsClover
 * @since v26.0-Alpha.8
 */
class SideDetectorTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty("mcjebooster.side");
        System.clearProperty("aprism.agent.side");
    }

    @Test
    @DisplayName("Dedicated server classes resolve to SERVER")
    void testServerDetection() {
        assertEquals(Side.SERVER, SideDetector.detectFromClassNames(
            Arrays.asList("net.minecraft.server.dedicated.DedicatedServer",
                "net.minecraft.server.MinecraftServer")));
        // Yarn intermediary mapping
        assertEquals(Side.SERVER, SideDetector.detectFromClassNames(
            Collections.singletonList("net.minecraft.class_3176")));
    }

    @Test
    @DisplayName("Client without integrated server resolves to CLIENT_MULTIPLAYER")
    void testMultiplayerClientDetection() {
        assertEquals(Side.CLIENT_MULTIPLAYER, SideDetector.detectFromClassNames(
            Arrays.asList("net.minecraft.client.Minecraft",
                "net.minecraft.client.main.Main")));
    }

    @Test
    @DisplayName("Client with integrated server resolves to CLIENT_INTEGRATED")
    void testIntegratedClientDetection() {
        assertEquals(Side.CLIENT_INTEGRATED, SideDetector.detectFromClassNames(
            Arrays.asList("net.minecraft.client.Minecraft",
                "net.minecraft.server.integrated.IntegratedServer")));
        // Yarn intermediary client marker
        assertEquals(Side.CLIENT_INTEGRATED, SideDetector.detectFromClassNames(
            Arrays.asList("net.minecraft.class_310", "net.minecraft.class_1132")));
    }

    @Test
    @DisplayName("Empty or unknown class sets resolve to UNKNOWN")
    void testUnknownDetection() {
        assertEquals(Side.UNKNOWN, SideDetector.detectFromClassNames(null));
        assertEquals(Side.UNKNOWN, SideDetector.detectFromClassNames(Collections.emptyList()));
        assertEquals(Side.UNKNOWN, SideDetector.detectFromClassNames(
            Collections.singletonList("java.lang.String")));
    }

    @Test
    @DisplayName("Explicit mcjebooster.side property overrides heuristics")
    void testPropertyOverride() {
        System.setProperty("mcjebooster.side", "server");
        assertEquals(Side.SERVER, SideDetector.detectFromSystemProperties());

        System.setProperty("mcjebooster.side", "integrated");
        assertEquals(Side.CLIENT_INTEGRATED, SideDetector.detectFromSystemProperties());

        System.setProperty("mcjebooster.side", "client");
        assertEquals(Side.CLIENT_MULTIPLAYER, SideDetector.detectFromSystemProperties());

        System.setProperty("mcjebooster.side", "bogus");
        assertEquals(Side.UNKNOWN, SideDetector.detectFromSystemProperties());
    }

    @Test
    @DisplayName("Aprism side property is honored")
    void testAprismSideProperty() {
        System.setProperty("aprism.agent.side", "server");
        assertEquals(Side.SERVER, SideDetector.detectFromSystemProperties());

        System.setProperty("aprism.agent.side", "client");
        assertEquals(Side.CLIENT_MULTIPLAYER, SideDetector.detectFromSystemProperties());
    }

    @Test
    @DisplayName("Aprism presence flag is detected without Aprism installed")
    void testAprismPresenceFlag() {
        try {
            System.setProperty("aprism.agent.active", "true");
            assertTrue(SideDetector.isAprismPresent());
        } finally {
            System.clearProperty("aprism.agent.active");
        }
        // Without the flag and without Aprism classes, must be false
        assertFalse(SideDetector.isAprismPresent());
    }
}
