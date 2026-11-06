package io.nbaplatform.decision.plugins;

import static io.nbaplatform.decision.plugins.Fixtures.campaign;
import static io.nbaplatform.decision.plugins.Fixtures.offering;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.nbaplatform.decision.spi.Candidate;
import io.nbaplatform.decision.spi.DecisionContext;
import io.nbaplatform.decision.spi.DecisionRequest;
import io.nbaplatform.decision.spi.FeatureContribution;
import io.nbaplatform.decision.spi.PropensityScorer;
import io.nbaplatform.decision.spi.Score;
import io.nbaplatform.decision.spi.ScoringExplanation;
import io.nbaplatform.decision.spi.ScoringFailedException;
import io.nbaplatform.events.CampaignAction;
import io.nbaplatform.events.PropensityModel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class PropensityScoringStageTest {

    /** A scorer of type {@code test} whose behaviour is chosen per model name. */
    static final class TestScorer implements PropensityScorer {
        final AtomicInteger calls = new AtomicInteger();
        final boolean remote;
        final Function<String, Double> behaviour;

        TestScorer(boolean remote, Function<String, Double> behaviour) {
            this.remote = remote;
            this.behaviour = behaviour;
        }

        @Override
        public String type() {
            return "test";
        }

        @Override
        public boolean remote() {
            return remote;
        }

        @Override
        public Score score(PropensityModel model, DecisionRequest request) {
            calls.incrementAndGet();
            return Score.of(behaviour.apply(model.name()));
        }
    }

    static PropensityModel test(String name) {
        return new PropensityModel("test", name, "v7", 0, null, List.of());
    }

    private static Map<String, Candidate> byOffering(DecisionContext ctx) {
        Map<String, Candidate> map = new java.util.HashMap<>();
        ctx.candidates().forEach(c -> map.put(c.offering().id(), c));
        return map;
    }

    private static Fixtures twoOffers(PropensityModel a, PropensityModel b) {
        return new Fixtures()
                .with(offering("a", 10, a))
                .with(offering("b", 10, b))
                .with(campaign("c1", null, 0, 0, new CampaignAction("a", 1), new CampaignAction("b", 1)));
    }

    @Test
    void logisticScoresAreExplainedByTheirLargestContributions() {
        PropensityModel model = new PropensityModel("v3", -2.0, Map.of("tenureMonths", 0.05, "isGold", 1.5,
                "complaints", -0.4));
        Fixtures f = new Fixtures().with(offering("o1", 10, model))
                .with(campaign("c1", null, 0, 0, new CampaignAction("o1", 1)));
        DecisionContext ctx = f.context("p1", Map.of("tenureMonths", 24, "isGold", true, "complaints", "1"), 3);

        Fixtures.run(ctx, new CampaignCandidateStage(), Fixtures.scoring());

        Candidate c = ctx.candidates().get(0);
        double z = -2.0 + 0.05 * 24 + 1.5 - 0.4;
        assertThat(c.modelPropensity()).isCloseTo(1 / (1 + Math.exp(-z)), within(1e-9));
        assertThat(c.reasonCodes()).containsExactly("MODEL_v3");
        ScoringExplanation why = c.scoring();
        assertThat(why.scorer()).isEqualTo("logistic");
        assertThat(why.modelVersion()).isEqualTo("v3");
        assertThat(why.fallbackReason()).isNull();
        assertThat(why.contributions()).extracting(FeatureContribution::feature)
                .containsExactly("isGold", "tenureMonths", "complaints");
        assertThat(why.contributions().get(1).contribution()).isCloseTo(1.2, within(1e-9));
        assertThat(why.contributions().get(1).value()).isEqualTo(24.0);
    }

    @Test
    void offeringsWithoutModelGetThePrior() {
        Fixtures f = new Fixtures().with(offering("o1", 10, null))
                .with(campaign("c1", null, 0, 0, new CampaignAction("o1", 1)));
        DecisionContext ctx = f.context("p1", Map.of(), 3);

        Fixtures.run(ctx, new CampaignCandidateStage(), Fixtures.scoring());

        Candidate c = ctx.candidates().get(0);
        assertThat(c.modelPropensity()).isEqualTo(0.05);
        assertThat(c.reasonCodes()).containsExactly("MODEL_DEFAULT_PRIOR");
        assertThat(c.scoring().scorer()).isEqualTo(ScoringExplanation.PRIOR);
        assertThat(c.scoring().fallbackReason()).isNull();
    }

    @Test
    void unknownModelTypeFallsBackToPriorForThatCandidateOnly() {
        Fixtures f = twoOffers(new PropensityModel("gbm", "churn", "v1", 0, null, null),
                new PropensityModel("v1", 0, Map.of()));
        List<String> fallbacks = new ArrayList<>();
        DecisionContext ctx = f.context("p1", Map.of(), 3);

        Fixtures.run(ctx, new CampaignCandidateStage(), new PropensityScoringStage(List.of(new LogisticScorer()),
                0.05, Duration.ofMillis(40), PropensityScoringStage.OnFailure.PRIOR,
                (scorer, reason) -> fallbacks.add(scorer + "/" + reason)));

        Map<String, Candidate> c = byOffering(ctx);
        assertThat(c.get("a").modelPropensity()).isEqualTo(0.05);
        assertThat(c.get("a").reasonCodes()).containsExactly("SCORING_FALLBACK", "SCORER_UNAVAILABLE");
        assertThat(c.get("a").scoring().fallbackReason()).isEqualTo("SCORER_UNAVAILABLE");
        assertThat(c.get("a").scoring().modelName()).isEqualTo("churn");
        assertThat(c.get("b").modelPropensity()).isEqualTo(0.5);
        assertThat(fallbacks).containsExactly("gbm/SCORER_UNAVAILABLE");
    }

    @Test
    void failingAndInvalidModelsFallBackWithTheirCause() {
        TestScorer scorer = new TestScorer(false, name -> switch (name) {
            case "boom" -> throw new IllegalStateException("model file corrupt");
            case "nan" -> Double.NaN;
            case "big" -> 1.5;
            default -> 0.3;
        });
        Fixtures f = new Fixtures()
                .with(offering("boom", 1, test("boom")))
                .with(offering("nan", 1, test("nan")))
                .with(offering("big", 1, test("big")))
                .with(offering("ok", 1, test("ok")))
                .with(campaign("c1", null, 0, 0, new CampaignAction("boom", 1), new CampaignAction("nan", 1),
                        new CampaignAction("big", 1), new CampaignAction("ok", 1)));
        DecisionContext ctx = f.context("p1", Map.of(), 5);

        Fixtures.run(ctx, new CampaignCandidateStage(), new PropensityScoringStage(List.of(scorer), 0.02));

        Map<String, Candidate> c = byOffering(ctx);
        assertThat(c.get("boom").scoring().fallbackReason()).isEqualTo("SCORING_ERROR");
        assertThat(c.get("nan").scoring().fallbackReason()).isEqualTo("SCORING_INVALID_OUTPUT");
        assertThat(c.get("big").scoring().fallbackReason()).isEqualTo("SCORING_INVALID_OUTPUT");
        assertThat(c.get("boom").modelPropensity()).isEqualTo(0.02);
        assertThat(c.get("ok").modelPropensity()).isEqualTo(0.3);
        assertThat(c.get("ok").scoring().fallbackReason()).isNull();
        assertThat(ctx.candidates()).hasSize(4); // nothing suppressed: scoring degrades, never removes
    }

    @Test
    void slowRemoteModelsTimeOutWithoutHoldingTheDecision() {
        TestScorer scorer = new TestScorer(true, name -> {
            if (name.equals("slow")) {
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return 0.4;
        });
        Fixtures f = twoOffers(test("slow"), test("fast"));
        DecisionContext ctx = f.context("p1", Map.of(), 3);
        PropensityScoringStage stage = new PropensityScoringStage(List.of(scorer), 0.05, Duration.ofMillis(50),
                PropensityScoringStage.OnFailure.PRIOR, null);

        long start = System.nanoTime();
        Fixtures.run(ctx, new CampaignCandidateStage(), stage);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        stage.close();

        assertThat(elapsedMs).isLessThan(1_000);
        Map<String, Candidate> c = byOffering(ctx);
        assertThat(c.get("a").scoring().fallbackReason()).isEqualTo("SCORING_TIMEOUT");
        assertThat(c.get("a").modelPropensity()).isEqualTo(0.05);
        assertThat(c.get("b").modelPropensity()).isEqualTo(0.4);
    }

    @Test
    void eachDistinctModelIsScoredOncePerDecision() {
        TestScorer scorer = new TestScorer(false, name -> 0.2);
        PropensityModel shared = test("shared");
        Fixtures f = twoOffers(shared, shared)
                .with(campaign("c2", null, 0, 0, new CampaignAction("a", 2)));
        DecisionContext ctx = f.context("p1", Map.of(), 3);

        Fixtures.run(ctx, new CampaignCandidateStage(), new PropensityScoringStage(List.of(scorer), 0.05));

        assertThat(ctx.candidates()).hasSize(3).allSatisfy(c -> assertThat(c.modelPropensity()).isEqualTo(0.2));
        assertThat(scorer.calls).hasValue(1);
    }

    @Test
    void safeDefaultModeFailsTheDecisionInsteadOfUsingThePrior() {
        TestScorer scorer = new TestScorer(false, name -> {
            throw new IllegalStateException("down");
        });
        Fixtures f = twoOffers(test("x"), null);
        DecisionContext ctx = f.context("p1", Map.of(), 3);
        PropensityScoringStage stage = new PropensityScoringStage(List.of(scorer), 0.05, Duration.ofMillis(40),
                PropensityScoringStage.OnFailure.SAFE_DEFAULT, null);

        assertThatThrownBy(() -> Fixtures.run(ctx, new CampaignCandidateStage(), stage))
                .isInstanceOf(ScoringFailedException.class)
                .extracting(e -> ((ScoringFailedException) e).reasonCode()).isEqualTo("SCORING_ERROR");
    }

    @Test
    void arbitrationRecordsTheValueItUsed() {
        Fixtures f = twoOffers(new PropensityModel("v1", 0, Map.of()), null);
        f.with(offering("free", 0, new PropensityModel("v1", 0, Map.of())))
                .with(campaign("c3", null, 0, 0, new CampaignAction("free", 2)));
        DecisionContext ctx = f.context("p1", Map.of(), 5);

        Fixtures.run(ctx, new CampaignCandidateStage(), Fixtures.scoring(), new PriorityArbitrationStage());

        Map<String, Candidate> c = byOffering(ctx);
        assertThat(c.get("a").value()).isEqualTo(10.0);
        assertThat(c.get("free").value()).isEqualTo(1.0);
        assertThat(c.get("free").priority()).isCloseTo(0.5 * 1.0 * 2, within(1e-9));
    }

    @Test
    void rejectsAPriorOutsideZeroToOne() {
        assertThatThrownBy(() -> new PropensityScoringStage(List.of(), 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
