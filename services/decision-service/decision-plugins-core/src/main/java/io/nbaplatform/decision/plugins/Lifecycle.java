package io.nbaplatform.decision.plugins;

import java.util.Set;

/** Which lifecycle statuses are live for decisioning. */
public final class Lifecycle {

    private static final Set<String> LIVE_OFFERING = Set.of("active", "launched");

    private Lifecycle() {
    }

    public static boolean campaignLive(String status) {
        return status != null && status.equalsIgnoreCase("active");
    }

    public static boolean offeringLive(String status) {
        return status != null && LIVE_OFFERING.contains(status.toLowerCase());
    }
}
