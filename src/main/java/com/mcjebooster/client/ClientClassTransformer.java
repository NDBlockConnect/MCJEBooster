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

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

import com.mcjebooster.util.Logger;

/**
 * Read-only client class transformer (v26.1-Alpha.2).
 *
 * This is the "seam discovery" stage of the client rollout. It is a
 * {@link ClassFileTransformer} that <b>never returns modified bytes</b>;
 * it only inspects candidate client classes and records confirmed hook
 * seams (tick/render entry points) in {@link ClientSeamRegistry}.
 *
 * Returning {@code null} from {@link #transform} is guaranteed by the
 * JVM contract to leave the class untouched, which keeps this stage
 * safe to enable on a live client.
 *
 * @author StarsailsClover
 * @since v26.1-Alpha.2
 */
public class ClientClassTransformer implements ClassFileTransformer, Opcodes {

    /** Method names considered a client game-loop / tick seam. */
    private static final String[] MAIN_TICK_CANDIDATES = {
        "tick", "runTick", "handleTick", "method_1572", "render",
    };

    /** Method names considered a chunk-render seam. */
    private static final String[] RENDER_CANDIDATES = {
        "render", "compile", "upload", "renderChunk", "method_20253",
    };

    /** Method names considered an integrated-server tick seam. */
    private static final String[] INTEGRATED_CANDIDATES = {
        "tick", "tickServer", "method_3748",
    };

    private final ClientSeamRegistry registry;

    public ClientClassTransformer() {
        this(ClientSeamRegistry.getInstance());
    }

    public ClientClassTransformer(ClientSeamRegistry registry) {
        this.registry = registry;
    }

    /**
     * Inspects (never modifies) a class. Always returns {@code null}.
     */
    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (classfileBuffer == null || className == null) {
            return null;
        }
        ClientClassMatcher.Role role = ClientClassMatcher.classify(className);
        if (role == ClientClassMatcher.Role.NONE) {
            return null;
        }

        try {
            discoverSeams(className, role, classfileBuffer);
        } catch (Throwable t) {
            Logger.debug("Client seam discovery failed for " + className + ": " + t.getMessage());
        }
        // Read-only stage: never return transformed bytes.
        return null;
    }

    /**
     * Scans the bytecode of a candidate class and records matching seams.
     *
     * @param className   internal class name
     * @param role        classified role
     * @param classBytes  class bytecode
     * @return number of seams recorded from this class
     */
    public int discoverSeams(String className, ClientClassMatcher.Role role, byte[] classBytes) {
        String[] candidates = candidatesFor(role);
        if (candidates == null) {
            return 0;
        }

        ClassReader reader = new ClassReader(classBytes);
        SeamCollector collector = new SeamCollector(className, role, candidates);
        reader.accept(collector, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
        return collector.count;
    }

    private String[] candidatesFor(ClientClassMatcher.Role role) {
        switch (role) {
            case CLIENT_MAIN:
                return MAIN_TICK_CANDIDATES;
            case CHUNK_RENDER:
                return RENDER_CANDIDATES;
            case INTEGRATED_SERVER:
                return INTEGRATED_CANDIDATES;
            default:
                return null;
        }
    }

    /** Visits methods and records candidates as seams. */
    private final class SeamCollector extends ClassVisitor {
        private final String className;
        private final ClientClassMatcher.Role role;
        private final String[] candidates;
        int count;

        SeamCollector(String className, ClientClassMatcher.Role role, String[] candidates) {
            super(ASM9);
            this.className = className;
            this.role = role;
            this.candidates = candidates;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if ((access & (ACC_ABSTRACT | ACC_NATIVE)) == 0 && isCandidate(name)) {
                ClientSeamRegistry.Seam seam =
                    new ClientSeamRegistry.Seam(className, name, descriptor, role);
                if (!registry.contains(className, name, descriptor)) {
                    registry.register(seam);
                    count++;
                    Logger.debug("Client seam discovered: " + seam);
                }
            }
            return null;
        }

        private boolean isCandidate(String name) {
            if (name.startsWith("<")) {
                return false;
            }
            for (String candidate : candidates) {
                if (name.equals(candidate)) {
                    return true;
                }
            }
            return false;
        }
    }
}
