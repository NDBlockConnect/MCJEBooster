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

/**
 * Central version constant for MCJEBooster.
 *
 * Versioning follows the Aprism-style scheme:
 * {@code v<Year>.<minor>[-Alpha.<n>]} where the major line is the calendar
 * year (v26 = the 2026 line). Within a minor, {@code Alpha.1..Alpha.9} are
 * shipped as GitHub Pre-Releases; Alpha.9 is the release candidate and the
 * bare version (e.g. {@code v26.0}) is the official GA Release.
 * There is no Alpha.10 and no Beta.
 *
 * All user-visible version strings must be sourced from this class so the
 * codebase never drifts between releases.
 *
 * @author StarsailsClover
 * @since v26.0-Alpha.1
 */
public final class BoosterVersion {

    /** Current release version, e.g. {@code v26.0-Alpha.1}. */
    public static final String VERSION = "v26.1-Alpha.2";

    /** Major line of the current version, e.g. {@code v26}. */
    public static final String MAJOR_LINE = "v26";

    /** Development stage of the current version. */
    public enum Stage {
        /** Pre-release alpha, shipped as GitHub Pre-Release. */
        ALPHA,
        /** Release candidate (Alpha.9 semantics). */
        RELEASE_CANDIDATE,
        /** Official GA release (bare version number). */
        GA
    }

    private BoosterVersion() {
    }

    /**
     * Returns the current stage derived from the version string.
     * {@code Alpha.9} is the release candidate by convention.
     *
     * @return the current release stage
     */
    public static Stage stage() {
        if (VERSION.endsWith("-Alpha.9")) {
            return Stage.RELEASE_CANDIDATE;
        }
        if (VERSION.contains("-Alpha.")) {
            return Stage.ALPHA;
        }
        return Stage.GA;
    }

    /**
     * Returns a one-line banner used by the agent and injector.
     *
     * @return banner text
     */
    public static String banner() {
        return "MCJEBooster " + VERSION + " (stage=" + stage() + ")";
    }
}
