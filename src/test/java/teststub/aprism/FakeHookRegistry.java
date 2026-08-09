/*
 * Test stub emulating the Aprism lowlevel MethodHookRegistry API surface
 * used by AprismBridge. Lives only in the test classpath.
 */

package teststub.aprism;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors the static API of
 * {@code com.aprism.loader.lowlevel.MethodHookRegistry} so the
 * reflective bridge can be exercised without the real Aprism artifact.
 */
public final class FakeHookRegistry {

    public static final List<String> REGISTERED = new ArrayList<>();
    public static final List<String> UNREGISTERED = new ArrayList<>();
    public static int clearCount = 0;

    private FakeHookRegistry() {
    }

    public static void register(String className, String methodName,
                                String descriptor, Runnable listener) {
        REGISTERED.add(className + "." + methodName + descriptor);
    }

    public static void unregister(String className, String methodName,
                                  String descriptor, Runnable listener) {
        UNREGISTERED.add(className + "." + methodName + descriptor);
    }

    public static void clear() {
        clearCount++;
        REGISTERED.clear();
        UNREGISTERED.clear();
    }

    public static void reset() {
        REGISTERED.clear();
        UNREGISTERED.clear();
        clearCount = 0;
    }
}
