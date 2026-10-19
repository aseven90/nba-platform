package io.nbaplatform.decision.plugins;

import io.nbaplatform.decision.spi.Candidate;
import io.nbaplatform.decision.spi.DecisionContext;
import io.nbaplatform.decision.spi.DecisionStage;
import io.nbaplatform.decision.spi.Stage;

/**
 * priority = propensity x value x lever. Actions with no monetary value (service messages, education) use a
 * value of 1 so they rank on propensity and lever alone.
 */
public class PriorityArbitrationStage implements DecisionStage {

    @Override
    public Stage stage() {
        return Stage.ARBITRATION;
    }

    @Override
    public String name() {
        return "priority-pvl";
    }

    @Override
    public void apply(DecisionContext context) {
        for (Candidate c : context.candidates()) {
            double value = c.offering().businessValue() > 0 ? c.offering().businessValue() : 1.0;
            c.priority(c.propensity() * value * c.priorityLever());
        }
    }
}
