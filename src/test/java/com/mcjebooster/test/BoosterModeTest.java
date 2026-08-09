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

import com.mcjebooster.client.BoosterMode;
import com.mcjebooster.client.BoosterMode.Mode;
import com.mcjebooster.client.ClientIntegration;
import com.mcjebooster.client.ClientSeamRegistry;
import com.mcjebooster.util.SideDetector.Side;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v26.1-Alpha.4: hybrid mode selection + client integration tests.
 *
 * @author StarsailsClover
 * @since v26.1-Alpha.4
 */
class BoosterModeTest {

    private static final String MODE_PROPERTY = "mcjebooster.mode";

    @BeforeEach
    void setUp() {
        System.clearProperty(MODE_PROPERTY);
        ClientIntegration.resetForTests();
        ClientSeamRegistry.getInstance().clear();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(MODE_PROPERTY);
        ClientIntegration.resetForTests();
    }

    @Test
    @DisplayName("parse handles all documented values and defaults to AUTO")
    void testParse() {
        assertEquals(Mode.AUTO, BoosterMode.parse(null));
        assertEquals(Mode.AUTO, BoosterMode.parse("auto"));
        assertEquals(Mode.AUTO, BoosterMode.parse("  AUTO  "));
        assertEquals(Mode.STANDALONE, BoosterMode.parse("standalone"));
        assertEquals(Mode.APRISM, BoosterMode.parse("aprism"));
        assertEquals(Mode.AUTO, BoosterMode.parse("something-weird"));
    }

    @Test
    @DisplayName("configured reads the system property")
    void testConfigured() {
        assertEquals(Mode.AUTO, BoosterMode.configured());
        System.setProperty(MODE_PROPERTY, "standalone");
        assertEquals(Mode.STANDALONE, BoosterMode.configured());
        System.setProperty(MODE_PROPERTY, "aprism");
        assertEquals(Mode.APRISM, BoosterMode.configured());
    }

    @Test
    @DisplayName("resolve: STANDALONE never bridges, APRISM requires runtime, AUTO follows runtime")
    void testResolve() {
        assertEquals(Mode.STANDALONE, BoosterMode.resolve(Mode.STANDALONE, true));
        assertEquals(Mode.STANDALONE, BoosterMode.resolve(Mode.STANDALONE, false));

        assertEquals(Mode.APRISM, BoosterMode.resolve(Mode.APRISM, true));
        assertEquals(Mode.STANDALONE, BoosterMode.resolve(Mode.APRISM, false));

        assertEquals(Mode.APRISM, BoosterMode.resolve(Mode.AUTO, true));
        assertEquals(Mode.STANDALONE, BoosterMode.resolve(Mode.AUTO, false));
    }

    @Test
    @DisplayName("arm on SERVER side is a no-op")
    void testArmServerNoop() {
        assertEquals(0, ClientIntegration.arm(Side.SERVER, Mode.APRISM));
        assertFalse(ClientIntegration.isArmed());
        assertEquals(0, ClientIntegration.arm(null, Mode.AUTO));
    }

    @Test
    @DisplayName("arm is idempotent on client sides")
    void testArmIdempotent() {
        assertEquals(0, ClientIntegration.arm(Side.CLIENT_MULTIPLAYER, Mode.STANDALONE));
        assertTrue(ClientIntegration.isArmed());
        // Second arm is rejected but keeps armed state
        assertEquals(0, ClientIntegration.arm(Side.CLIENT_MULTIPLAYER, Mode.STANDALONE));
        assertTrue(ClientIntegration.isArmed());
    }

    @Test
    @DisplayName("arm in standalone mode registers zero hooks")
    void testArmStandalone() {
        int hooks = ClientIntegration.arm(Side.CLIENT_MULTIPLAYER, Mode.STANDALONE);
        assertEquals(0, hooks);
        assertTrue(ClientIntegration.isArmed());
    }

    @Test
    @DisplayName("arm in aprism mode without runtime degrades to measurement-only")
    void testArmAprismWithoutRuntime() {
        int hooks = ClientIntegration.arm(Side.CLIENT_MULTIPLAYER, Mode.APRISM);
        assertEquals(0, hooks, "no Aprism runtime on test classpath");
        assertTrue(ClientIntegration.isArmed());
    }

    @Test
    @DisplayName("disarm resets armed state and is safe when not armed")
    void testDisarm() {
        ClientIntegration.disarm(); // safe when never armed
        ClientIntegration.arm(Side.CLIENT_INTEGRATED, Mode.STANDALONE);
        assertTrue(ClientIntegration.isArmed());
        ClientIntegration.disarm();
        assertFalse(ClientIntegration.isArmed());
        ClientIntegration.disarm(); // double-disarm safe
    }

    @Test
    @DisplayName("integrated server side arms successfully")
    void testArmIntegrated() {
        assertEquals(0, ClientIntegration.arm(Side.CLIENT_INTEGRATED, Mode.AUTO));
        assertTrue(ClientIntegration.isArmed());
    }
}
