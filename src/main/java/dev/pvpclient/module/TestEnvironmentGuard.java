package dev.pvpclient.module;

import java.util.function.BooleanSupplier;

/**
 * Gate for gameplay-altering modules. Modules that return true from
 * {@link Module#requiresTestEnvironment()} refuse to enable unless this guard allows it.
 *
 * <p>The default check denies everything. Phase 3 installs a check that allows activation only in
 * singleplayer (integrated server) or when a private-server opt-in is explicitly configured.
 */
public final class TestEnvironmentGuard {
    public static final String DENIED_MESSAGE =
            "Only available in singleplayer or a private test world.";

    private static volatile BooleanSupplier check = () -> false;

    private TestEnvironmentGuard() {}

    public static void setCheck(BooleanSupplier newCheck) {
        check = newCheck == null ? () -> false : newCheck;
    }

    public static boolean isAllowed() {
        try {
            return check.getAsBoolean();
        } catch (Exception | LinkageError e) {
            return false; // fail closed if the game state can't be queried
        }
    }
}
