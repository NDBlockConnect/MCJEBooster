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

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import com.mcjebooster.util.Logger;

/**
 * Reflective bridge to the Aprism Loader lower-level API (v26.1-Alpha.3).
 *
 * Per the hybrid client-support design (docs 02), when MCJEBooster runs
 * inside the Aprism agent it must route hooks and class redefinitions
 * through Aprism's {@code com.aprism.loader.lowlevel} package:
 *
 * <ul>
 *   <li>{@code MethodHookRegistry} — on-enter hook registration where
 *       exceptions are swallowed and never crash the game</li>
 *   <li>{@code ClassRedefiner} — fault-tolerant class redefinition</li>
 * </ul>
 *
 * This bridge accesses those classes <b>purely through reflection</b>,
 * so the MCJEBooster jar carries zero compile-time or runtime
 * dependency on Aprism. When Aprism is absent, every operation
 * degrades to a safe no-op and reports {@code false}/0.
 *
 * The registry class name can be overridden with
 * {@code -Dmcjebooster.aprism.registryClass=...} for migration testing.
 *
 * @author StarsailsClover
 * @since v26.1-Alpha.3
 */
public final class AprismBridge {

    /** Default Aprism hook registry class (v26.1-Alpha.8 lowlevel API). */
    public static final String DEFAULT_REGISTRY_CLASS =
        "com.aprism.loader.lowlevel.MethodHookRegistry";

    /** Default Aprism class redefiner class. */
    public static final String DEFAULT_REDEFINER_CLASS =
        "com.aprism.loader.lowlevel.ClassRedefiner";

    private static final AprismBridge INSTANCE = new AprismBridge();

    private final AtomicInteger registeredHooks = new AtomicInteger();
    private volatile String lastError;

    private AprismBridge() {
    }

    public static AprismBridge getInstance() {
        return INSTANCE;
    }

    /** Resolves the hook registry class name (property override aware). */
    public static String registryClassName() {
        String override = System.getProperty("mcjebooster.aprism.registryClass");
        return override != null && !override.trim().isEmpty()
            ? override.trim() : DEFAULT_REGISTRY_CLASS;
    }

    /** Resolves the class redefiner class name (property override aware). */
    public static String redefinerClassName() {
        String override = System.getProperty("mcjebooster.aprism.redefinerClass");
        return override != null && !override.trim().isEmpty()
            ? override.trim() : DEFAULT_REDEFINER_CLASS;
    }

    /**
     * Returns true when the Aprism hook registry is reachable.
     *
     * @return true in bridge mode, false in standalone mode
     */
    public boolean isAvailable() {
        try {
            Class.forName(registryClassName(), false, AprismBridge.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Registers an on-enter hook via the Aprism registry.
     *
     * @param className  slashed internal class name
     * @param methodName hooked method name
     * @param descriptor method descriptor, e.g. {@code ()V}
     * @param listener   callback fired on method entry
     * @return true when the hook was registered through Aprism
     */
    public boolean registerHook(String className, String methodName,
                                String descriptor, Runnable listener) {
        if (className == null || methodName == null || listener == null) {
            return false;
        }
        try {
            Class<?> registry = Class.forName(registryClassName(), true,
                AprismBridge.class.getClassLoader());
            Method register = registry.getMethod("register",
                String.class, String.class, String.class, Runnable.class);
            register.invoke(null, className, methodName,
                descriptor == null ? "" : descriptor, listener);
            registeredHooks.incrementAndGet();
            return true;
        } catch (Throwable t) {
            lastError = t.toString();
            Logger.debug("Aprism hook registration failed: " + lastError);
            return false;
        }
    }

    /**
     * Unregisters a previously registered hook.
     *
     * @return true when the hook was unregistered through Aprism
     */
    public boolean unregisterHook(String className, String methodName,
                                  String descriptor, Runnable listener) {
        if (className == null || methodName == null || listener == null) {
            return false;
        }
        try {
            Class<?> registry = Class.forName(registryClassName(), true,
                AprismBridge.class.getClassLoader());
            Method unregister = registry.getMethod("unregister",
                String.class, String.class, String.class, Runnable.class);
            unregister.invoke(null, className, methodName,
                descriptor == null ? "" : descriptor, listener);
            if (registeredHooks.get() > 0) {
                registeredHooks.decrementAndGet();
            }
            return true;
        } catch (Throwable t) {
            lastError = t.toString();
            Logger.debug("Aprism hook unregistration failed: " + lastError);
            return false;
        }
    }

    /**
     * Clears all Aprism hooks (shutdown/rollback path).
     *
     * @return true when the registry clear call succeeded
     */
    public boolean clearAllHooks() {
        try {
            Class<?> registry = Class.forName(registryClassName(), true,
                AprismBridge.class.getClassLoader());
            Method clear = registry.getMethod("clear");
            clear.invoke(null);
            registeredHooks.set(0);
            return true;
        } catch (Throwable t) {
            lastError = t.toString();
            Logger.debug("Aprism hook clear failed: " + lastError);
            return false;
        }
    }

    /**
     * Retransforms already-loaded classes through Aprism's ClassRedefiner.
     *
     * @param inst    instrumentation instance
     * @param targets classes to retransform
     * @return number of successfully retransformed classes, 0 on any failure
     */
    public int retransformViaAprism(Instrumentation inst, Class<?>... targets) {
        if (inst == null || targets == null || targets.length == 0) {
            return 0;
        }
        try {
            Class<?> redefinerClass = Class.forName(redefinerClassName(), true,
                AprismBridge.class.getClassLoader());
            Object redefiner = redefinerClass
                .getConstructor(Instrumentation.class)
                .newInstance(inst);
            Method retransform = redefinerClass.getMethod("retransform", Class[].class);
            Object result = retransform.invoke(redefiner, (Object) targets);
            return result instanceof Number ? ((Number) result).intValue() : 0;
        } catch (Throwable t) {
            lastError = t.toString();
            Logger.debug("Aprism retransform failed: " + lastError);
            return 0;
        }
    }

    /** Number of hooks registered through this bridge since last clear. */
    public int getRegisteredHookCount() {
        return registeredHooks.get();
    }

    /**
     * Resets local bridge bookkeeping (rollback/shutdown path).
     * Does not contact Aprism; use {@link #clearAllHooks()} for that.
     */
    public void resetState() {
        registeredHooks.set(0);
        lastError = null;
    }

    /** Last bridge error message, or null when healthy. */
    public String getLastError() {
        return lastError;
    }

    /** Diagnostic one-liner. */
    public String getBridgeInfo() {
        return "AprismBridge available=" + isAvailable()
            + " registryClass=" + registryClassName()
            + " registeredHooks=" + registeredHooks.get();
    }
}
