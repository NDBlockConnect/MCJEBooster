# 02 - Client-Side Support Architecture (Hybrid Aprism Design)

> English is the canonical documentation; `docs/zh/` holds the Chinese copy.
> Status: design accepted in v26.0-Alpha.8, implemented in the v26.1 series.

## 1. Problem statement

Until the v26.0 line, MCJEBooster was **server-side only**:

- `MinecraftServerTransformer` matches only `MinecraftServer` /
  `DedicatedServer` classes and their tick methods.
- `InjectionBridge.tickRegions(Object)` expects a dedicated-server-style
  object with reachable levels, entities and block entities.
- The external injector and the agent were validated against dedicated
  server processes.

Client processes are a different beast:

| Concern | Dedicated server | Client |
|---|---|---|
| Class loader topology | `-jar` system class loader | launcher-specific (official, HMCL, PCL2, MultiMC/Prism, CurseForge...) |
| Entry point | `MinecraftServer.run()` | `net.minecraft.client.main.Main` → renderer + integrated server |
| Tick ownership | single server thread | render thread + (single-player) integrated server thread |
| Obfuscation | Mojang/proguard | Mojang/proguard + loader remaps |
| Third-party interference | few | OptiFine, performance mods, overlays, anti-tamper tools |

## 2. Why standalone client injection alone is fragile

Attaching to a client JVM with the raw Attach API *works*, but:

1. Every launcher assembles the class path differently; classloader probes
   tuned for servers miss client layouts.
2. There is no lifecycle contract — the agent can land before or after the
   client classes load, and there is no callback for "game fully started".
3. There is no coordination with other transformers (Mixin, OptiFine), so
   ordering conflicts are undefined.
4. The optimization surface on a *pure* multiplayer client is the render and
   chunk pipeline, not the tick loop — blind tick-hook injection there is
   both useless and risky.

## 3. What Aprism v26.1-Alpha.8 provides

The Aprism Loader (sibling project, same organization) shipped a
**lower-level API foundation** in `v26.1-Alpha.8`, explicitly documented as
the *"MCJEBooster layer"*:

```java
package com.aprism.loader.lowlevel;

public final class ClassRedefiner {
    public ClassRedefiner(Instrumentation instrumentation);
    public boolean isRedefineSupported();
    public boolean isRetransformSupported();
    public boolean redefine(Class<?> target, byte[] newBytes);  // fault-tolerant
    public int retransform(Class<?>... targets);                // returns success count
}

public final class MethodHookRegistry {
    public static String hookKey(String className, String methodName, String descriptor);
    public static void register(String className, String methodName, String descriptor, Runnable listener);
    public static void unregister(String className, String methodName, String descriptor, Runnable listener);
    public static boolean hasAnyHookForClass(String className);
    public static boolean hasHook(String className, String methodName, String descriptor);
    public static void fire(String hookKey);   // exceptions swallowed, never crashes the game
    public static void clear();
}

public final class MethodHookTransformer extends ClassVisitor { /* on-enter hook injection */ }
```

Aprism also provides the missing client-side scaffolding:

- A proven `javaagent` bootstrap with `side=client|server`, `gameRoot`,
  `mcVersion` arguments and a two-phase production bootstrap.
- The `IAprismMod` lifecycle (`PREINIT/INIT/SETUP/COMPLETE`, plus
  `CLIENT`/`SERVER` side phases).
- The `.aje` package format and manifest contract for drop-in distribution.

## 4. Trade-off analysis

| Option | Stability | Standalone use | Effort | Verdict |
|---|---|---|---|---|
| A. Raw client attach only | low (per-launcher fragility) | yes | low | insufficient |
| B. Pure Aprism extension (`.aje`) | high | no — requires Aprism | medium | too exclusive |
| C. **Hybrid**: standalone core + optional Aprism bridge | high where Aprism runs, unchanged elsewhere | yes | medium | **chosen** |

### The hybrid contract

1. MCJEBooster remains a standalone agent/injector — nothing breaks for
   dedicated-server users.
2. A new `SideDetector` determines `SERVER | CLIENT_INTEGRATED |
   CLIENT_MULTIPLAYER | UNKNOWN` from loaded classes, system properties and
   the presence of an integrated server.
3. When MCJEBooster detects it is running **inside the Aprism agent**
   (system property `aprism.agent.active=true` or the Aprism classes are
   loadable), it switches to **Aprism bridge mode**:
   - hooks go through `MethodHookRegistry.register(...)` instead of raw
     instrumentation,
   - redefinitions go through `ClassRedefiner`,
   - initialization aligns with the Aprism lifecycle phases.
   The bridge is accessed **reflectively** (compile-time optional), so the
   standalone jar never depends on Aprism artifacts.
4. Without Aprism, the agent falls back to the classic standalone path.
   On a client this enables: integrated-server optimization (single-player)
   via the existing server pipeline, and safe measurement-only mode on pure
   multiplayer clients.
5. Mode selection: `mcjebooster.mode=auto|standalone|aprism` (default
   `auto`). Client-side tick parallelism stays **off by default**
   (`experimental.parallelTick=false`), consistent with server behavior.

## 5. Client-side scope in v26.1

| Scenario | v26.1 behavior |
|---|---|
| Dedicated server | unchanged (server pipeline) |
| Single-player client (integrated server) | server pipeline targets the integrated server |
| Multiplayer client | detection + metrics + hook seam only; no tick modification |
| Client under Aprism | bridge mode; hooks registered via Aprism registry |

Render-thread and chunk-mesh optimizations are **explicitly out of scope**
for v26.1 (candidate for v26.2+ once the seam is proven).

## 6. v26.1 delivery plan

| Pre-Release | Content |
|---|---|
| v26.1-Alpha.1 | `SideDetector` + tests (client/server/integrated/unknown) |
| v26.1-Alpha.2 | `ClientClassTransformer` seam (client class matching, measurement hooks) |
| v26.1-Alpha.3 | `AprismBridge` (reflective, compile-safe, optional) |
| v26.1-Alpha.4 | hybrid mode selection in agent + config integration |
| v26.1-Alpha.5 | integration tests, docs refresh, release candidate prep |

## 7. Decision record

**ADR-006: Hybrid client support via Aprism bridge**
- Date: 2026-08-10
- Decision: adopt option C (hybrid), with reflective Aprism integration
- Rationale: Aprism v26.1-Alpha.8's lowlevel API exists precisely for this
  layer; standalone behavior must not regress; a reflective bridge keeps the
  LGPL artifact dependency-free.
- Alternatives rejected: A (fragile), B (forces Aprism on all users).
