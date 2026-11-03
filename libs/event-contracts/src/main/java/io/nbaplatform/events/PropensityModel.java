package io.nbaplatform.events;

import java.util.Map;

/**
 * Logistic propensity model: {@code p = sigmoid(intercept + sum(coefficient[f] * feature[f]))}.
 * Features are numeric context characteristics supplied with the decision request.
 */
public record PropensityModel(String version, double intercept, Map<String, Double> coefficients) {
}
