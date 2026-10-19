package io.nbaplatform.decision.plugins;

import io.nbaplatform.decision.spi.Candidate;
import io.nbaplatform.decision.spi.DecisionContext;
import io.nbaplatform.decision.spi.DecisionStage;
import io.nbaplatform.decision.spi.LearningState.ArmStats;
import io.nbaplatform.decision.spi.Stage;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Contextual Thompson sampling. Each arm (campaign x offering) has a Beta posterior whose prior is centred on
 * the model propensity with weight {@code priorStrength} pseudo-observations, updated with observed accepts
 * and non-accepts. The candidate's propensity is a draw from that posterior, so uncertain arms are explored
 * and proven arms exploited; as evidence grows the draw concentrates on the observed rate.
 */
public class ThompsonSamplingStage implements DecisionStage {

    private final double priorStrength;
    private final Supplier<RandomGenerator> random;

    public ThompsonSamplingStage(double priorStrength, Supplier<RandomGenerator> random) {
        this.priorStrength = priorStrength;
        this.random = random;
    }

    @Override
    public Stage stage() {
        return Stage.LEARNING;
    }

    @Override
    public String name() {
        return "thompson-sampling";
    }

    @Override
    public void apply(DecisionContext context) {
        RandomGenerator rng = random.get();
        String tenant = context.request().tenantId();
        for (Candidate c : context.candidates()) {
            ArmStats stats = context.learning().stats(tenant, c.campaign().id(), c.offering().id());
            double prior = clamp(c.modelPropensity());
            double alpha = prior * priorStrength + stats.accepts();
            double beta = (1.0 - prior) * priorStrength + stats.failures();
            c.propensity(BetaSampler.sample(rng, alpha, beta));
            c.reason(stats.impressions() == 0 ? "EXPLORING_NEW_ARM" : "POSTERIOR_SAMPLED");
        }
    }

    private static double clamp(double p) {
        return Math.min(0.999, Math.max(0.001, p));
    }
}
