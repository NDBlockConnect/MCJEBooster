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

import java.util.Locale;

import com.mcjebooster.util.SideDetector;

/**
 * Hybrid bridge-mode resolution (v26.1-Alpha.4).
 *
 * The hybrid client-support design (docs 02) gives operators three
 * modes via {@code -Dmcjebooster.mode=auto|standalone|aprism}:
 *
 * <ul>
 *   <li>{@link Mode#AUTO} (default) — use the Aprism bridge when the
 *       Aprism runtime is present, otherwise standalone</li>
 *   <li>{@link Mode#STANDALONE} — never use the bridge, even if Aprism
 *       is present (server admins who want zero coupling)</li>
 *   <li>{@link Mode#APRISM} — require the bridge; resolves to APRISM
 *       only when the runtime is actually present, otherwise falls back
 *       to STANDALONE with a diagnostic</li>
 * </ul>
 *
 * @author StarsailsClover
 * @since v26.1-Alpha.4
 */
public final class BoosterMode {

    /** The resolved operating mode. */
    public enum Mode {
        /** Resolve automatically from the environment. */
        AUTO,
        /** Classic standalone agent behavior, no Aprism coupling. */
        STANDALONE,
        /** Route client hooks through the Aprism bridge. */
        APRISM
    }

    /** System property controlling the mode. */
    public static final String MODE_PROPERTY = "mcjebooster.mode";

    private BoosterMode() {
    }

    /**
     * Parses a mode string; unknown values resolve to AUTO.
     *
     * @param value raw property value
     * @return parsed mode
     */
    public static Mode parse(String value) {
        if (value == null) {
            return Mode.AUTO;
        }
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "standalone":
                return Mode.STANDALONE;
            case "aprism":
                return Mode.APRISM;
            case "auto":
            default:
                return Mode.AUTO;
        }
    }

    /**
     * Reads the configured mode from the system property.
     *
     * @return configured mode (AUTO when unset)
     */
    public static Mode configured() {
        return parse(System.getProperty(MODE_PROPERTY));
    }

    /**
     * Resolves the effective mode for this JVM.
     *
     * @param configured      the operator-configured mode
     * @param aprismAvailable whether the Aprism runtime is reachable
     * @return the effective mode
     */
    public static Mode resolve(Mode configured, boolean aprismAvailable) {
        switch (configured) {
            case STANDALONE:
                return Mode.STANDALONE;
            case APRISM:
                return aprismAvailable ? Mode.APRISM : Mode.STANDALONE;
            case AUTO:
            default:
                return aprismAvailable ? Mode.APRISM : Mode.STANDALONE;
        }
    }

    /**
     * Convenience: resolve from the live environment.
     *
     * @return effective mode for this JVM
     */
    public static Mode resolveEffective() {
        return resolve(configured(), SideDetector.isAprismPresent());
    }
}
