package io.nbaplatform.events;

import java.time.Instant;
import java.util.List;

/** Immutable decision log entry: what was recommended, what was suppressed and why. */
public record DecisionMade(
        String decisionId,
        String partyId,
        String channel,
        Instant decidedAt,
        boolean fallback,
        String strategy,
        List<Item> items,
        List<Suppression> suppressed,
        long latencyMicros) {

    public record Item(
            int rank,
            String offeringId,
            String campaignId,
            double priority,
            double propensity,
            double businessValue,
            List<String> reasonCodes) {
    }

    public record Suppression(String offeringId, String campaignId, String reasonCode) {
    }
}
