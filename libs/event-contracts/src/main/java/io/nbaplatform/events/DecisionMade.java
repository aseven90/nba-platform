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
            List<String> reasonCodes,
            Explanation explanation) {
    }

    /**
     * Why an item got its priority: {@code priority = propensity x value x priorityLever}, and where the
     * propensity came from. Raw attribute values are deliberately not logged (privacy by design).
     *
     * @param scorer          scorer plugin type, or {@code prior} when no model was used
     * @param scoringFallback null when the model was used, otherwise why the default prior replaced it
     */
    public record Explanation(
            String scorer,
            String modelName,
            String modelVersion,
            double modelPropensity,
            double propensity,
            double value,
            double priorityLever,
            double priority,
            String scoringFallback,
            List<Contribution> contributions) {
    }

    /** One feature's effect on the model's log-odds. */
    public record Contribution(String feature, Double weight, Double contribution) {
    }

    public record Suppression(String offeringId, String campaignId, String reasonCode) {
    }
}
