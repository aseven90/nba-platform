package io.nbaplatform.decision.spi;

/**
 * How much one feature moved a model score.
 *
 * @param feature      attribute name
 * @param value        numeric value the model saw
 * @param weight       model weight for the feature, or null when the model does not expose one
 * @param contribution effect on the model's raw output (log-odds for logistic models), or null when unknown
 */
public record FeatureContribution(String feature, double value, Double weight, Double contribution) {

    /** A linear term: contribution = weight x value. */
    public static FeatureContribution linear(String feature, double value, double weight) {
        return new FeatureContribution(feature, value, weight, weight * value);
    }

    /** An input whose effect the model does not report. */
    public static FeatureContribution input(String feature, double value) {
        return new FeatureContribution(feature, value, null, null);
    }
}
