package io.nbaplatform.decision.plugins;

import io.nbaplatform.decision.spi.Candidate;
import io.nbaplatform.decision.spi.DecisionContext;
import io.nbaplatform.decision.spi.DecisionStage;
import io.nbaplatform.decision.spi.Stage;
import io.nbaplatform.events.PropensityModel;
import java.util.Map;

/**
 * Scores each candidate with its offering's logistic model over numeric attributes. Offerings without a model
 * get the configured prior. Missing or non-numeric features contribute zero.
 */
public class LogisticPropensityStage implements DecisionStage {

    private final double defaultPropensity;

    public LogisticPropensityStage(double defaultPropensity) {
        this.defaultPropensity = defaultPropensity;
    }

    @Override
    public Stage stage() {
        return Stage.SCORING;
    }

    @Override
    public String name() {
        return "logistic-propensity";
    }

    @Override
    public void apply(DecisionContext context) {
        Map<String, Object> attributes = context.request().attributes();
        for (Candidate c : context.candidates()) {
            PropensityModel model = c.offering().propensityModel();
            if (model == null) {
                c.modelPropensity(defaultPropensity);
                c.reason("MODEL_DEFAULT_PRIOR");
            } else {
                c.modelPropensity(score(model, attributes));
                c.reason("MODEL_" + (model.version() == null ? "UNVERSIONED" : model.version()));
            }
        }
    }

    static double score(PropensityModel model, Map<String, Object> attributes) {
        double z = model.intercept();
        if (model.coefficients() != null) {
            for (var entry : model.coefficients().entrySet()) {
                z += entry.getValue() * numeric(attributes.get(entry.getKey()));
            }
        }
        return 1.0 / (1.0 + Math.exp(-z));
    }

    private static double numeric(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof Boolean b) {
            return b ? 1.0 : 0.0;
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
