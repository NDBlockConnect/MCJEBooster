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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mcjebooster.util.Logger;
import com.mcjebooster.util.SideDetector;

/**
 * Client-side integration coordinator (v26.1-Alpha.4).
 *
 * Wires the client rollout pieces together according to the resolved
 * {@link BoosterMode.Mode} and the detected {@link SideDetector.Side}:
 *
 * <table>
 *   <tr><th>Side</th><th>Mode</th><th>Behavior</th></tr>
 *   <tr><td>SERVER</td><td>any</td><td>nothing — server pipeline unchanged</td></tr>
 *   <tr><td>CLIENT_INTEGRATED</td><td>any</td><td>seam discovery + metrics;
 *       integrated server handled by the existing server pipeline</td></tr>
 *   <tr><td>CLIENT_MULTIPLAYER</td><td>STANDALONE</td><td>seam discovery +
 *       metrics only</td></tr>
 *   <tr><td>CLIENT_MULTIPLAYER</td><td>APRISM</td><td>seam discovery + metrics,
 *       plus hook registration through the Aprism bridge for every
 *       discovered CLIENT_MAIN tick seam</td></tr>
 * </table>
 *
 * Tick parallelism is never enabled on the client: hooks feed
 * {@link ClientMetrics} (measurement), not the region scheduler.
 *
 * @author StarsailsClover
 * @since v26.1-Alpha.4
 */
public final class ClientIntegration {

    private static final AtomicBoolean ARMED = new AtomicBoolean(false);

    private ClientIntegration() {
    }

    /**
     * Arms the client integration if the side/mode combination allows it.
     *
     * @param side detected hosting side
     * @param mode resolved bridge mode
     * @return number of Aprism hooks registered (0 in standalone/server cases)
     */
    public static int arm(SideDetector.Side side, BoosterMode.Mode mode) {
        if (side == null || side == SideDetector.Side.SERVER) {
            Logger.info("Client integration not required on side: " + side);
            return 0;
        }
        if (!ARMED.compareAndSet(false, true)) {
            Logger.warn("Client integration already armed");
            return 0;
        }

        Logger.info("Arming client integration: side=" + side + " mode=" + mode);

        // Metrics hook: feed ClientMetrics from every discovered tick seam.
        Runnable metricsListener = () -> ClientMetrics.getInstance().recordTick(tickSpan());

        int registered = 0;
        if (mode == BoosterMode.Mode.APRISM) {
            AprismBridge bridge = AprismBridge.getInstance();
            if (bridge.isAvailable()) {
                List<ClientSeamRegistry.Seam> seams =
                    ClientSeamRegistry.getInstance().all();
                for (ClientSeamRegistry.Seam seam : seams) {
                    if (seam.role() == ClientClassMatcher.Role.CLIENT_MAIN
                        && bridge.registerHook(seam.className(), seam.methodName(),
                            seam.descriptor(), metricsListener)) {
                        registered++;
                    }
                }
                Logger.info("Aprism bridge hooks registered: " + registered);
            } else {
                Logger.warn("Mode APRISM requested but bridge unavailable;"
                    + " continuing measurement-only");
            }
        } else {
            Logger.info("Standalone client mode: measurement-only, hooks deferred");
        }

        Logger.info(ClientMetrics.getInstance().summarize());
        return registered;
    }

    /** Disarms (rollback path). */
    public static void disarm() {
        if (ARMED.compareAndSet(true, false)) {
            AprismBridge.getInstance().clearAllHooks();
            Logger.info("Client integration disarmed");
        }
    }

    /** True when armed. */
    public static boolean isArmed() {
        return ARMED.get();
    }

    /** Reset for tests. */
    public static void resetForTests() {
        ARMED.set(false);
    }

    /** Approximate span of one client tick (50ms at 20 TPS). */
    private static long tickSpan() {
        return 50_000_000L;
    }
}
