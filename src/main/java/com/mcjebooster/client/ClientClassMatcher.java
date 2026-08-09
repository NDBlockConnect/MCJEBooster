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

/**
 * Mapping-tolerant client class classifier (v26.1-Alpha.2).
 *
 * The client rollout needs to know *which* classes carry the hook seams
 * before any bytecode is touched. This matcher classifies internal class
 * names ({@code net/minecraft/...} form) into client roles across
 * Mojang (proguard) and Yarn intermediary mappings.
 *
 * @author StarsailsClover
 * @since v26.1-Alpha.2
 */
public final class ClientClassMatcher {

    /** Client-side class roles. */
    public enum Role {
        /** The main client class (game loop owner). */
        CLIENT_MAIN,
        /** The integrated (single-player) server. */
        INTEGRATED_SERVER,
        /** Chunk rendering / meshing pipeline. */
        CHUNK_RENDER,
        /** Not a recognized client class. */
        NONE
    }

    /** Mojang + Yarn markers for the main client class. */
    private static final String[] CLIENT_MAIN_MARKERS = {
        "net/minecraft/client/Minecraft",
        "net/minecraft/class_310",
    };

    /** Markers for the integrated server. */
    private static final String[] INTEGRATED_MARKERS = {
        "net/minecraft/server/integrated/IntegratedServer",
        "net/minecraft/class_1132",
    };

    /** Markers for the chunk render pipeline. */
    private static final String[] CHUNK_RENDER_MARKERS = {
        "net/minecraft/client/renderer/chunk/ChunkRenderDispatcher",
        "net/minecraft/client/renderer/SectionRenderDispatcher",
        "net/minecraft/client/render/RenderSectionManager",
        "net/minecraft/class_803",
        "net/minecraft/class_70",
    };

    private ClientClassMatcher() {
    }

    /**
     * Classifies an internal class name.
     *
     * @param internalName class name in internal form (slashes); may be null
     * @return the detected role, or {@link Role#NONE}
     */
    public static Role classify(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            return Role.NONE;
        }
        if (matches(internalName, CLIENT_MAIN_MARKERS)) {
            return Role.CLIENT_MAIN;
        }
        if (matches(internalName, INTEGRATED_MARKERS)) {
            return Role.INTEGRATED_SERVER;
        }
        if (matches(internalName, CHUNK_RENDER_MARKERS)) {
            return Role.CHUNK_RENDER;
        }
        return Role.NONE;
    }

    /**
     * Converts a dotted class name to internal form for matching.
     *
     * @param dottedClass dotted class name
     * @return internal form
     */
    public static String toInternal(String dottedClass) {
        return dottedClass == null ? null : dottedClass.replace('.', '/');
    }

    private static boolean matches(String internalName, String[] markers) {
        for (String marker : markers) {
            if (internalName.equals(marker) || internalName.endsWith("/" + marker)) {
                return true;
            }
        }
        return false;
    }
}
