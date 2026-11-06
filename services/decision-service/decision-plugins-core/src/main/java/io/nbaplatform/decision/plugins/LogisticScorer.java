package io.nbaplatform.decision.plugins;

import io.nbaplatform.decision.spi.DecisionRequest;
import io.nbaplatform.decision.spi.FeatureContribution;
import io.nbaplatform.decision.spi.PropensityScorer;
import io.nbaplatform.decision.spi.Score;
import io.nbaplatform.events.PropensityModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * In-process logistic regression: {@code p = sigmoid(intercept + sum(coefficient[f] * feature[f]))}. Reports
 * every term's contribution to the log-odds, largest effect first, so each decision can be explained.
 */
public class LogisticScorer implements PropensityScorer {

    @Override
    public String type() {
        return PropensityModel.LOGISTIC;
    }

    @Override
    public Score score(PropensityModel model, DecisionRequest request) {
        Map<String, Object> attributes = request.attributes();
        double z = model.intercept();
        List<FeatureContribution> contributions = new ArrayList<>();
        if (model.coefficients() != null) {
            for (var entry : model.coefficients().entrySet()) {
                double weight = entry.getValue() == null ? 0.0 : entry.getValue();
                FeatureContribution term = FeatureContribution.linear(entry.getKey(),
                        Features.numeric(attributes.get(entry.getKey())), weight);
                z += term.contribution();
                contributions.add(term);
            }
        }
        contributions.sort(Comparator.comparingDouble((FeatureContribution c) -> Math.abs(c.contribution()))
                .reversed()
                .thenComparing(FeatureContribution::feature));
        return new Score(sigmoid(z), contributions);
    }

    static double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }
}
