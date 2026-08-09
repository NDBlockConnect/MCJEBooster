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

import com.mcjebooster.client.ClientClassMatcher;
import com.mcjebooster.client.ClientClassMatcher.Role;
import com.mcjebooster.client.ClientClassTransformer;
import com.mcjebooster.client.ClientSeamRegistry;
import com.mcjebooster.client.ClientSeamRegistry.Seam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v26.1-Alpha.2: client seam discovery tests. The transformer must stay
 * read-only, classify classes mapping-tolerantly, and record exactly the
 * confirmed hook seams.
 *
 * @author StarsailsClover
 * @since v26.1-Alpha.2
 */
class ClientSeamTest {

    private ClientSeamRegistry registry;
    private ClientClassTransformer transformer;

    @BeforeEach
    void setUp() {
        registry = ClientSeamRegistry.getInstance();
        registry.clear();
        transformer = new ClientClassTransformer(registry);
    }

    @Test
    @DisplayName("ClientClassMatcher classifies Mojang and Yarn client classes")
    void testClassification() {
        assertEquals(Role.CLIENT_MAIN, ClientClassMatcher.classify("net/minecraft/client/Minecraft"));
        assertEquals(Role.CLIENT_MAIN, ClientClassMatcher.classify("net/minecraft/class_310"));
        assertEquals(Role.INTEGRATED_SERVER,
            ClientClassMatcher.classify("net/minecraft/server/integrated/IntegratedServer"));
        assertEquals(Role.INTEGRATED_SERVER, ClientClassMatcher.classify("net/minecraft/class_1132"));
        assertEquals(Role.CHUNK_RENDER,
            ClientClassMatcher.classify("net/minecraft/client/renderer/chunk/ChunkRenderDispatcher"));
        assertEquals(Role.NONE, ClientClassMatcher.classify("net/minecraft/server/MinecraftServer"));
        assertEquals(Role.NONE, ClientClassMatcher.classify(null));
        assertEquals(Role.NONE, ClientClassMatcher.classify(""));
    }

    @Test
    @DisplayName("toInternal converts dotted names")
    void testToInternal() {
        assertEquals("net/minecraft/client/Minecraft",
            ClientClassMatcher.toInternal("net.minecraft.client.Minecraft"));
        assertNull(ClientClassMatcher.toInternal(null));
    }

    @Test
    @DisplayName("transform always returns null (read-only contract)")
    void testTransformNeverModifies() {
        byte[] bytes = syntheticClass("net/minecraft/client/Minecraft");
        assertNull(transformer.transform(null, "net/minecraft/client/Minecraft", null, null, bytes));
        assertNull(transformer.transform(null, null, null, null, null));
        assertNull(transformer.transform(null, "net/minecraft/server/MinecraftServer", null, null, bytes));
    }

    @Test
    @DisplayName("discoverSeams records tick/render entry points")
    void testSeamDiscovery() {
        byte[] bytes = syntheticClientClassWithMethods("net/minecraft/client/Minecraft",
            "tick", "()V", "render", "()V", "someOther", "()V");

        int recorded = transformer.discoverSeams("net/minecraft/client/Minecraft",
            Role.CLIENT_MAIN, bytes);

        assertTrue(recorded >= 2, "expected tick + render seams, got " + recorded);
        assertTrue(registry.contains("net/minecraft/client/Minecraft", "tick", "()V"));
        assertTrue(registry.contains("net/minecraft/client/Minecraft", "render", "()V"));
        assertFalse(registry.contains("net/minecraft/client/Minecraft", "someOther", "()V"));
    }

    @Test
    @DisplayName("discovery is idempotent - duplicate seams not re-registered")
    void testIdempotentDiscovery() {
        byte[] bytes = syntheticClientClassWithMethods("net/minecraft/client/Minecraft",
            "tick", "()V");
        transformer.discoverSeams("net/minecraft/client/Minecraft", Role.CLIENT_MAIN, bytes);
        int sizeAfterFirst = registry.size();
        transformer.discoverSeams("net/minecraft/client/Minecraft", Role.CLIENT_MAIN, bytes);
        assertEquals(sizeAfterFirst, registry.size());
    }

    @Test
    @DisplayName("registry returns defensive copies and filters by class")
    void testRegistryQueries() {
        byte[] a = syntheticClientClassWithMethods("net/minecraft/client/Minecraft", "tick", "()V");
        byte[] b = syntheticClientClassWithMethods("net/minecraft/client/renderer/chunk/ChunkRenderDispatcher",
            "render", "()V");
        transformer.discoverSeams("net/minecraft/client/Minecraft", Role.CLIENT_MAIN, a);
        transformer.discoverSeams("net/minecraft/client/renderer/chunk/ChunkRenderDispatcher",
            Role.CHUNK_RENDER, b);

        List<Seam> all = registry.all();
        assertEquals(2, all.size());
        assertEquals(1, registry.forClass("net/minecraft/client/Minecraft").size());
        assertEquals(1, registry.forClass("net/minecraft/client/renderer/chunk/ChunkRenderDispatcher").size());
        assertTrue(registry.summarize().contains("seams=2"));

        // Defensive copy: mutating the returned list must not affect registry
        all.clear();
        assertEquals(2, registry.size());
    }

    @Test
    @DisplayName("Seam value equality covers all fields")
    void testSeamEquality() {
        Seam s1 = new Seam("C", "m", "()V", Role.CLIENT_MAIN);
        Seam s2 = new Seam("C", "m", "()V", Role.CLIENT_MAIN);
        Seam s3 = new Seam("C", "m", "(I)V", Role.CLIENT_MAIN);
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
        assertNotEquals(s1, s3);
        assertNotEquals(s1, new Seam("C", "m", "()V", Role.NONE));
    }

    // ---- synthetic bytecode helpers --------------------------------------

    private static byte[] syntheticClass(String internalName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null,
            "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] syntheticClientClassWithMethods(String internalName, String... nameDescPairs) {
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
