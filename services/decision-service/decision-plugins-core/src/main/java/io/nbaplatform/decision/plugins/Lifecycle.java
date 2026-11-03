package io.nbaplatform.decision.plugins;

import java.util.Set;

final class Lifecycle {

    private static final Set<String> LIVE_OFFERING = Set.of("active", "launched");

    private Lifecycle() {
    }

    static boolean campaignLive(String status) {
        return status != null && status.equalsIgnoreCase("active");
    }

    static boolean offeringLive(String status) {
        return status != null && LIVE_OFFERING.contains(status.toLowerCase());
    }
}
