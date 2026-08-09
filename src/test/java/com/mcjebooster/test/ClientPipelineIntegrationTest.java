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
import com.mcjebooster.client.BoosterMode;
import com.mcjebooster.client.ClientClassMatcher;
import com.mcjebooster.client.ClientClassMatcher.Role;
import com.mcjebooster.client.ClientClassTransformer;
import com.mcjebooster.client.ClientIntegration;
import com.mcjebooster.client.ClientSeamRegistry;
import com.mcjebooster.util.SideDetector.Side;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import teststub.aprism.FakeHookRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v26.1-Alpha.5: end-to-end client pipeline integration test.
 *
 * Exercises the full rollout chain on synthetic bytecode, exactly as it
 * runs on a real client:
 *
 * <pre>
 * SideDetector → ClientClassMatcher → ClientClassTransformer
 *   → ClientSeamRegistry → BoosterMode → AprismBridge → ClientIntegration
 * </pre>
 *
 * @author StarsailsClover
 * @since v26.1-Alpha.5
 */
class ClientPipelineIntegrationTest {

    private static final String REGISTRY_PROPERTY = "mcjebooster.aprism.registryClass";
    private static final String MODE_PROPERTY = "mcjebooster.mode";

    @BeforeEach
    void setUp() {
        ClientIntegration.resetForTests();
        ClientSeamRegistry.getInstance().clear();
        FakeHookRegistry.reset();
        System.clearProperty(REGISTRY_PROPERTY);
        System.clearProperty(MODE_PROPERTY);
    }

    @AfterEach
    void tearDown() {
        ClientIntegration.resetForTests();
        ClientSeamRegistry.getInstance().clear();
        System.clearProperty(REGISTRY_PROPERTY);
        System.clearProperty(MODE_PROPERTY);
    }

    @Test
    @DisplayName("E2E: Aprism mode arms metrics hooks for every CLIENT_MAIN seam")
    void testEndToEndAprismPipeline() {
        // 1. Simulate an Aprism runtime by pointing the bridge at the stub.
        System.setProperty(REGISTRY_PROPERTY, "teststub.aprism.FakeHookRegistry");
        assertTrue(AprismBridge.getInstance().isAvailable());

        // 2. Operator requests Aprism mode; environment has the runtime.
        System.setProperty(MODE_PROPERTY, "aprism");
        BoosterMode.Mode effective = BoosterMode.resolve(
            BoosterMode.configured(), AprismBridge.getInstance().isAvailable());
        assertEquals(BoosterMode.Mode.APRISM, effective);

        // 3. Seam discovery on a synthetic client class.
        byte[] clientClass = classWithMethods("net/minecraft/client/Minecraft",
            "tick", "()V", "render", "()V", "someHelper", "(I)V");
        ClientClassTransformer transformer = new ClientClassTransformer();
        int seams = transformer.discoverSeams("net/minecraft/client/Minecraft",
            Role.CLIENT_MAIN, clientClass);
        assertEquals(2, seams, "tick + render are CLIENT_MAIN candidates");

        // 4. Arm integration on a multiplayer client in Aprism mode.
        int registered = ClientIntegration.arm(Side.CLIENT_MULTIPLAYER, effective);
        assertEquals(2, registered, "one metrics hook per CLIENT_MAIN seam");
        assertTrue(ClientIntegration.isArmed());
        assertEquals(2, FakeHookRegistry.REGISTERED.size());
        assertTrue(FakeHookRegistry.REGISTERED.contains("net/minecraft/client/Minecraft.tick()V"));
        assertTrue(FakeHookRegistry.REGISTERED.contains("net/minecraft/client/Minecraft.render()V"));

        // 5. Rollback disarms and clears the bridge hooks.
        ClientIntegration.disarm();
        assertFalse(ClientIntegration.isArmed());
        assertTrue(FakeHookRegistry.clearCount >= 1);
    }

    @Test
    @DisplayName("E2E: standalone mode never registers hooks even with seams present")
    void testEndToEndStandalonePipeline() {
        System.setProperty(MODE_PROPERTY, "standalone");
        BoosterMode.Mode effective = BoosterMode.resolve(BoosterMode.configured(), true);
        assertEquals(BoosterMode.Mode.STANDALONE, effective);

        byte[] clientClass = classWithMethods("net/minecraft/client/Minecraft",
            "tick", "()V");
        new ClientClassTransformer().discoverSeams("net/minecraft/client/Minecraft",
            Role.CLIENT_MAIN, clientClass);
        assertEquals(1, ClientSeamRegistry.getInstance().size());

        int registered = ClientIntegration.arm(Side.CLIENT_MULTIPLAYER, effective);
        assertEquals(0, registered, "standalone defers hooks");
        assertTrue(ClientIntegration.isArmed());
        assertTrue(FakeHookRegistry.REGISTERED.isEmpty());
    }

    @Test
    @DisplayName("E2E: server side short-circuits the entire pipeline")
    void testEndToEndServerShortCircuit() {
        System.setProperty(MODE_PROPERTY, "aprism");
        int registered = ClientIntegration.arm(Side.SERVER, BoosterMode.Mode.APRISM);
        assertEquals(0, registered);
        assertFalse(ClientIntegration.isArmed(), "server never arms client integration");
    }

    @Test
    @DisplayName("E2E: integrated-server client arms but defers hooks to the server pipeline")
    void testEndToEndIntegratedClient() {
        int registered = ClientIntegration.arm(Side.CLIENT_INTEGRATED, BoosterMode.Mode.AUTO);
        assertEquals(0, registered);
        assertTrue(ClientIntegration.isArmed());
    }

    @Test
    @DisplayName("E2E: seam discovery + mode resolution compose without state leaks")
    void testPipelineComposability() {
        byte[] minecraft = classWithMethods("net/minecraft/client/Minecraft", "tick", "()V");
        byte[] integrated = classWithMethods("net/minecraft/server/integrated/IntegratedServer",
            "tickServer", "()V");

        ClientClassTransformer transformer = new ClientClassTransformer();
        assertEquals(Role.CLIENT_MAIN, ClientClassMatcher.classify("net/minecraft/client/Minecraft"));
        assertEquals(Role.INTEGRATED_SERVER,
            ClientClassMatcher.classify("net/minecraft/server/integrated/IntegratedServer"));

        transformer.discoverSeams("net/minecraft/client/Minecraft", Role.CLIENT_MAIN, minecraft);
        transformer.discoverSeams("net/minecraft/server/integrated/IntegratedServer",
            Role.INTEGRATED_SERVER, integrated);

        assertEquals(2, ClientSeamRegistry.getInstance().size());
        assertEquals(1, ClientSeamRegistry.getInstance()
            .forClass("net/minecraft/client/Minecraft").size());

        // Mode resolution is pure and does not consume seams.
        assertEquals(BoosterMode.Mode.STANDALONE,
            BoosterMode.resolve(BoosterMode.Mode.AUTO, false));
        assertEquals(2, ClientSeamRegistry.getInstance().size());
    }

    private static byte[] classWithMethods(String internalName, String... nameDescPairs) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null,
            "java/lang/Object", null);
        for (int i = 0; i < nameDescPairs.length; i += 2) {
            MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, nameDescPairs[i],
                nameDescPairs[i + 1], null, null);
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }
}
