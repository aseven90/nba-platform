package io.nbaplatform.decision.spi;

import java.util.List;

/**
 * A scorer's answer for one model.
 *
 * @param propensity    probability of a positive response, in [0, 1]
 * @param contributions what moved the score, if the model can say (empty otherwise)
 */
public record Score(double propensity, List<FeatureContribution> contributions) {

    public Score {
        contributions = contributions == null ? List.of() : List.copyOf(contributions);
    }

    public static Score of(double propensity) {
        return new Score(propensity, List.of());
    }
}
