package io.nbaplatform.decision.spi;

import java.util.List;

/**
 * Where a candidate's model propensity came from, for explainable decisions.
 *
 * @param scorer          scorer type that produced the propensity, or {@code prior} when no model was used
 * @param modelName       model name, when the model has one
 * @param modelVersion    model version, when the model has one
 * @param contributions   feature contributions, biggest effect first (may be empty)
 * @param fallbackReason  null when the model was used; otherwise why the default prior replaced it
 */
public record ScoringExplanation(
        String scorer,
        String modelName,
        String modelVersion,
        List<FeatureContribution> contributions,
        String fallbackReason) {

    public static final String PRIOR = "prior";

    public ScoringExplanation {
        contributions = contributions == null ? List.of() : List.copyOf(contributions);
    }
}
