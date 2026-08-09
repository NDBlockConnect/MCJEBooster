/*
 * MCJEBooster - Minecraft Java Edition Multi-Core Optimization Engine
 * Copyright (C) 2026 StarsailsClover
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 */

package com.mcjebooster.util;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.Collection;

/**
 * Client/server side detection (v26.0-Alpha.8, design doc 02).
 *
 * MCJEBooster is extending from server-only optimization to client
 * awareness. This detector classifies the hosting JVM into one of four
 * sides so the agent can pick a safe strategy:
 *
 * <ul>
 *   <li>{@link Side#SERVER} — dedicated server process</li>
 *   <li>{@link Side#CLIENT_INTEGRATED} — single-player client with an
 *       integrated server (the server pipeline can target it)</li>
 *   <li>{@link Side#CLIENT_MULTIPLAYER} — client connected to an external
 *       server (measurement/hooks only, no tick modification)</li>
 *   <li>{@link Side#UNKNOWN} — could not determine</li>
 * </ul>
 *
 * Detection is heuristic and version-agnostic: it probes class names
 * (mapping-tolerant suffixes), system properties, and launcher hints.
 *
 * @author StarsailsClover
 * @since v26.0-Alpha.8
 */
public final class SideDetector {

    /** The hosting side of the JVM. */
    public enum Side {
        /** Dedicated server. */
        SERVER,
        /** Single-player client running an integrated server. */
        CLIENT_INTEGRATED,
        /** Client without a local server. */
        CLIENT_MULTIPLAYER,
        /** Side could not be determined. */
        UNKNOWN
    }

    /** Client class markers (mapping-tolerant suffixes). */
    private static final String[] CLIENT_MARKERS = {
        "net.minecraft.client.Minecraft",
        "net.minecraft.client.main.Main",
        "net.minecraft.class_310",            // Yarn intermediary (Minecraft client)
    };

    /** Integrated server markers. */
    private static final String[] INTEGRATED_MARKERS = {
        "net.minecraft.server.integrated.IntegratedServer",
        "net.minecraft.class_1132",            // Yarn intermediary
    };

    /** Dedicated server markers. */
    private static final String[] DEDICATED_MARKERS = {
        "net.minecraft.server.dedicated.DedicatedServer",
        "net.minecraft.server.MinecraftServer",
        "net.minecraft.class_3176",            // Yarn intermediary
    };

    private SideDetector() {
    }

    /**
     * Classifies the side from a set of loaded class names.
     *
     * @param classNames fully qualified class names; may be null/empty
     * @return the detected side
     */
    public static Side detectFromClassNames(Collection<String> classNames) {
        if (classNames == null || classNames.isEmpty()) {
            return Side.UNKNOWN;
        }

        boolean client = anyMatch(classNames, CLIENT_MARKERS);
        boolean integrated = anyMatch(classNames, INTEGRATED_MARKERS);
        boolean dedicated = anyMatch(classNames, DEDICATED_MARKERS);

        if (client) {
            return integrated ? Side.CLIENT_INTEGRATED : Side.CLIENT_MULTIPLAYER;
        }
        if (dedicated && !integrated) {
            return Side.SERVER;
        }
        return Side.UNKNOWN;
    }

    /**
     * Classifies the side from loaded classes using instrumentation.
     *
     * @param inst instrumentation instance; may be null
     * @return the detected side
     */
    public static Side detect(Instrumentation inst) {
        if (inst == null) {
            return detectFromSystemProperties();
        }
        Class<?>[] loaded = inst.getAllLoadedClasses();
        java.util.List<String> names = new java.util.ArrayList<>(loaded.length);
        for (Class<?> clazz : loaded) {
            names.add(clazz.getName());
        }
        Side side = detectFromClassNames(names);
        if (side != Side.UNKNOWN) {
            return side;
        }
        return detectFromSystemProperties();
    }

    /**
     * Best-effort detection from system properties set by launchers.
     *
     * @return the detected side or {@link Side#UNKNOWN}
     */
    public static Side detectFromSystemProperties() {
        String side = System.getProperty("mcjebooster.side");
        if (side != null) {
            String normalized = side.trim().toLowerCase(java.util.Locale.ROOT);
            switch (normalized) {
                case "server":
                    return Side.SERVER;
                case "client-integrated":
                case "integrated":
                    return Side.CLIENT_INTEGRATED;
                case "client":
                case "client-multiplayer":
                case "multiplayer":
                    return Side.CLIENT_MULTIPLAYER;
                default:
                    return Side.UNKNOWN;
            }
        }

        // Aprism agent passes side=client|server
        String aprismSide = System.getProperty("aprism.agent.side");
        if ("server".equalsIgnoreCase(aprismSide)) {
            return Side.SERVER;
        }
        if ("client".equalsIgnoreCase(aprismSide)) {
            return Side.CLIENT_MULTIPLAYER;
        }

        // Common launcher hints
        if (System.getProperty("minecraft.applet.TargetDirectory") != null
            || "true".equalsIgnoreCase(System.getProperty("minecraft.launcher"))) {
            return Side.CLIENT_MULTIPLAYER;
        }
        return Side.UNKNOWN;
    }

    /**
     * Returns true when MCJEBooster runs inside an Aprism agent.
     * Checks the documented Aprism active flag and class availability.
     *
     * @return true when the Aprism runtime is present
     */
    public static boolean isAprismPresent() {
        if ("true".equalsIgnoreCase(System.getProperty("aprism.agent.active"))) {
            return true;
        }
        try {
            Class.forName("com.aprism.loader.lowlevel.MethodHookRegistry", false,
                SideDetector.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean anyMatch(Collection<String> classNames, String[] markers) {
        for (String marker : markers) {
            String slashed = marker.replace('.', '/');
            for (String name : classNames) {
                if (name == null) {
                    continue;
                }
                if (name.equals(marker) || name.endsWith("." + marker)
                    || name.equals(slashed) || name.endsWith("/" + slashed)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Helper for tests: build a name list from classes. */
    public static Collection<String> namesOf(Class<?>... classes) {
        String[] names = new String[classes.length];
        for (int i = 0; i < classes.length; i++) {
            names[i] = classes[i].getName();
        }
        return Arrays.asList(names);
    }
}
