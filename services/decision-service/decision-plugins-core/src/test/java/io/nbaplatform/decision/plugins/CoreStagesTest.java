package io.nbaplatform.decision.plugins;

import static io.nbaplatform.decision.plugins.Fixtures.campaign;
import static io.nbaplatform.decision.plugins.Fixtures.offering;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.nbaplatform.decision.spi.Candidate;
import io.nbaplatform.decision.spi.DecisionContext;
import io.nbaplatform.decision.spi.LearningState;
import io.nbaplatform.decision.spi.Suppression;
import io.nbaplatform.events.CampaignAction;
import io.nbaplatform.events.PropensityModel;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class CoreStagesTest {

    @Test
    void candidatesComeFromLiveCampaignsAndUnknownOfferingsAreRecorded() {
        Fixtures f = new Fixtures()
                .with(offering("o1", 10, null))
                .with(campaign("c1", null, 0, 0, new CampaignAction("o1", 1), new CampaignAction("missing", 1)));
        DecisionContext ctx = f.context("p1", Map.of(), 3);

        Fixtures.run(ctx, new CampaignCandidateStage());

        assertThat(ctx.candidates()).extracting(c -> c.offering().id()).containsExactly("o1");
        assertThat(ctx.suppressed()).extracting(Suppression::reasonCode).containsExactly("OFFERING_UNKNOWN");
    }

    @Test
    void rulesFailClosedAndCannotCallMethods() {
        EligibilityRuleStage stage = new EligibilityRuleStage();
        Fixtures f = new Fixtures()
                .with(offering("o1", 1, null))
                .with(campaign("gold", "tenureMonths > 6 and segment == 'gold'", 0, 0, new CampaignAction("o1", 1)))
                .with(campaign("missingAttr", "creditScore > 600", 0, 0, new CampaignAction("o1", 1)))
                .with(campaign("evil", "T(java.lang.Runtime).getRuntime()", 0, 0, new CampaignAction("o1", 1)));
        DecisionContext ctx = f.context("p1", Map.of("tenureMonths", 12, "segment", "gold"), 5);

        Fixtures.run(ctx, new CampaignCandidateStage(), stage);

        assertThat(ctx.candidates()).extracting(c -> c.campaign().id()).containsExactly("gold");
        assertThat(ctx.suppressed()).extracting(Suppression::reasonCode)
                .containsExactly("RULE_NOT_EVALUABLE", "RULE_NOT_EVALUABLE");
    }

    @Test
    void consentAndContactCapsSuppress() {
        Fixtures f = new Fixtures()
                .with(offering("o1", 1, null))
                .with(campaign("capped", null, 0, 2, new CampaignAction("o1", 1)));
        f.impressions.put("p1/capped", 2);

        DecisionContext capped = f.context("p1", Map.of(), 3);
        Fixtures.run(capped, new CampaignCandidateStage(), new ConsentStage(false), new ContactPolicyStage());
        assertThat(capped.suppressed()).extracting(Suppression::reasonCode).containsExactly("CONTACT_CAP_REACHED");

        DecisionContext noConsent = f.context("p2", Map.of("consent.web", false), 3);
        Fixtures.run(noConsent, new CampaignCandidateStage(), new ConsentStage(false), new ContactPolicyStage());
        assertThat(noConsent.suppressed()).extracting(Suppression::reasonCode).containsExactly("NO_CHANNEL_CONSENT");

        DecisionContext explicit = f.context("p3", Map.of(), 3);
        Fixtures.run(explicit, new CampaignCandidateStage(), new ConsentStage(true));
        assertThat(explicit.candidates()).isEmpty();
    }

    @Test
    void controlGroupIsStableAndRoughlySized() {
        int held = 0;
        for (int i = 0; i < 20_000; i++) {
            boolean first = ControlGroupStage.inControlGroup("acme", "c1", "party-" + i, 10);
            assertThat(ControlGroupStage.inControlGroup("acme", "c1", "party-" + i, 10)).isEqualTo(first);
            if (first) {
                held++;
            }
        }
        assertThat(held / 20_000.0).isCloseTo(0.10, within(0.01));
        assertThat(ControlGroupStage.inControlGroup("acme", "c1", "anyone", 0)).isFalse();
    }

    @Test
    void logisticModelScoresNumericFeatures() {
        PropensityModel model = new PropensityModel("v1", -1.0, Map.of("tenureMonths", 0.1, "isGold", 1.0));
        assertThat(LogisticPropensityStage.score(model, Map.of("tenureMonths", 10, "isGold", true)))
                .isCloseTo(1 / (1 + Math.exp(-1.0)), within(1e-9));
        assertThat(LogisticPropensityStage.score(model, Map.of())).isCloseTo(1 / (1 + Math.exp(1.0)), within(1e-9));
    }

    @Test
    void thompsonSamplingConcentratesOnObservedRate() {
        Fixtures f = new Fixtures()
                .with(offering("o1", 1, null))
                .with(campaign("c1", null, 0, 0, new CampaignAction("o1", 1)));
        f.arms.put("c1/o1", new LearningState.ArmStats(10_000, 3_000));
        SplittableRandom seed = new SplittableRandom(7);
        ThompsonSamplingStage stage = new ThompsonSamplingStage(20, () -> seed);

        double sum = 0;
        for (int i = 0; i < 200; i++) {
            DecisionContext ctx = f.context("p1", Map.of(), 1);
            Fixtures.run(ctx, new CampaignCandidateStage(), new LogisticPropensityStage(0.05), stage);
            sum += ctx.candidates().get(0).propensity();
        }
        assertThat(sum / 200).isCloseTo(0.30, within(0.01));
    }

    @Test
    void newArmsAreSampledAroundTheModelPrior() {
        SplittableRandom seed = new SplittableRandom(11);
        double sum = 0;
        for (int i = 0; i < 5_000; i++) {
            sum += BetaSampler.sample(seed, 0.2 * 20, 0.8 * 20);
        }
        assertThat(sum / 5_000).isCloseTo(0.2, within(0.01));
    }

    @Test
    void arbitrationRanksByPropensityValueLeverAndDeduplicates() {
        Fixtures f = new Fixtures()
                .with(offering("cheap", 10, new PropensityModel("v1", 0, Map.of())))     // p = 0.5
                .with(offering("premium", 100, new PropensityModel("v1", -2, Map.of()))) // p ~ 0.119
                .with(offering("service", 0, new PropensityModel("v1", 3, Map.of())))    // p ~ 0.95, value 1
                .with(campaign("c1", null, 0, 0, new CampaignAction("cheap", 1), new CampaignAction("premium", 1),
                        new CampaignAction("service", 1)))
                .with(campaign("c2", null, 0, 0, new CampaignAction("cheap", 3)));
        DecisionContext ctx = f.context("p1", Map.of(), 2);

        Fixtures.run(ctx, new CampaignCandidateStage(), new LogisticPropensityStage(0.05),
                new PriorityArbitrationStage(), new TopNSelectionStage());

        // cheap via c2: 0.5 * 10 * 3 = 15; premium: 11.9; cheap via c1: 5; service: 0.95
        assertThat(ctx.candidates()).extracting(c -> c.offering().id() + "@" + c.campaign().id())
                .containsExactly("cheap@c2", "premium@c1");
        assertThat(ctx.suppressed()).extracting(Suppression::reasonCode)
                .containsExactlyInAnyOrder("DUPLICATE_OFFERING", "RANKED_OUT");
        assertThat(ctx.candidates()).extracting(Candidate::priority).isSortedAccordingTo((a, b) -> Double.compare(b, a));
    }
}
