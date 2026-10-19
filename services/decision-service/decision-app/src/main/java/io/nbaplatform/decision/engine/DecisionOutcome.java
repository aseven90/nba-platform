package io.nbaplatform.decision.engine;

import io.nbaplatform.decision.spi.Candidate;
import io.nbaplatform.decision.spi.DecisionRequest;
import io.nbaplatform.decision.spi.Suppression;
import java.util.List;

/**
 * Result of one decision.
 *
 * @param fallbackReason null for a normal decision, otherwise why the safe default was returned
 */
public record DecisionOutcome(
        String decisionId,
        DecisionRequest request,
        List<Candidate> items,
        List<Suppression> suppressed,
        String fallbackReason,
        long latencyMicros) {

    public boolean fallback() {
        return fallbackReason != null;
    }
}
