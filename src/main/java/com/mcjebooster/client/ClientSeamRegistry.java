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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of discovered client hook seams (v26.1-Alpha.2).
 *
 * Before MCJEBooster ever transforms a client class, it needs to know
 * which seam methods actually exist in the running version (names and
 * descriptors vary wildly across mappings). The
 * {@link ClientClassTransformer} records every confirmed seam here;
 * later phases (hook registration, Aprism bridge) consume this registry
 * instead of guessing.
 *
 * @author StarsailsClover
 * @since v26.1-Alpha.2
 */
public final class ClientSeamRegistry {

    /** A confirmed hook seam in a client class. */
    public static final class Seam {
        private final String className;
        private final String methodName;
        private final String descriptor;
        private final ClientClassMatcher.Role role;

        public Seam(String className, String methodName, String descriptor,
                    ClientClassMatcher.Role role) {
            this.className = Objects.requireNonNull(className, "className");
            this.methodName = Objects.requireNonNull(methodName, "methodName");
            this.descriptor = descriptor == null ? "" : descriptor;
            this.role = role == null ? ClientClassMatcher.Role.NONE : role;
        }

        public String className() { return className; }
        public String methodName() { return methodName; }
        public String descriptor() { return descriptor; }
        public ClientClassMatcher.Role role() { return role; }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Seam)) {
                return false;
            }
            Seam seam = (Seam) other;
            return className.equals(seam.className)
                && methodName.equals(seam.methodName)
                && descriptor.equals(seam.descriptor)
                && role == seam.role;
        }

        @Override
        public int hashCode() {
            return Objects.hash(className, methodName, descriptor, role);
        }

        @Override
        public String toString() {
            return role + " " + className + "." + methodName + descriptor;
        }
    }

    private static final ClientSeamRegistry INSTANCE = new ClientSeamRegistry();

    private final Map<String, Seam> seams = new ConcurrentHashMap<>();

    private ClientSeamRegistry() {
    }

    public static ClientSeamRegistry getInstance() {
        return INSTANCE;
    }

    /** Registers a seam; duplicates are ignored. */
    public void register(Seam seam) {
        if (seam == null) {
            return;
        }
        seams.put(key(seam), seam);
    }

    /** Returns all registered seams (defensive copy). */
    public List<Seam> all() {
        return new ArrayList<>(seams.values());
    }

    /** Returns seams for one class (defensive copy). */
    public List<Seam> forClass(String className) {
        List<Seam> result = new ArrayList<>();
        for (Seam seam : seams.values()) {
            if (seam.className().equals(className)) {
                result.add(seam);
            }
        }
        return result;
    }

    /** True when the exact seam is registered. */
    public boolean contains(String className, String methodName, String descriptor) {
        String normalizedDesc = descriptor == null ? "" : descriptor;
        for (Seam seam : seams.values()) {
            if (seam.className().equals(className)
                && seam.methodName().equals(methodName)
                && seam.descriptor().equals(normalizedDesc)) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return seams.size();
    }

    /** Clears the registry (used between test runs and rollback). */
    public void clear() {
        seams.clear();
    }

    private static String key(Seam seam) {
        return seam.className() + "#" + seam.methodName() + "#" + seam.descriptor();
    }

    /** Diagnostic summary line. */
    public String summarize() {
        return "ClientSeamRegistry seams=" + seams.size()
            + " " + Collections.unmodifiableCollection(seams.values());
    }
}
