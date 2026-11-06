package io.nbaplatform.events;

import java.util.List;
import java.util.Map;

/**
 * Propensity model attached to an offering. {@code type} picks the scorer plugin that evaluates it:
 * <ul>
 *   <li>{@code logistic} (default when absent): evaluated in-process,
 *       {@code p = sigmoid(intercept + sum(coefficient[f] * feature[f]))}.</li>
 *   <li>{@code remote}: served by an external model server (for example a Python model on KServe or Seldon)
 *       under {@code name}; {@code features} lists the inputs in the order the model expects them.</li>
 * </ul>
 * Features are numeric context characteristics supplied with the decision request or by the feature store.
 */
public record PropensityModel(
        String type,
        String name,
        String version,
        double intercept,
        Map<String, Double> coefficients,
        List<String> features) {

    public static final String LOGISTIC = "logistic";
    public static final String REMOTE = "remote";

    /** In-process logistic model. */
    public PropensityModel(String version, double intercept, Map<String, Double> coefficients) {
        this(LOGISTIC, null, version, intercept, coefficients, null);
    }

    /** The scorer type, {@code logistic} when not set. */
    public String effectiveType() {
        return type == null || type.isBlank() ? LOGISTIC : type;
    }
}
