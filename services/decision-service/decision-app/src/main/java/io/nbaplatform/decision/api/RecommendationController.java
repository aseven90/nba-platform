package io.nbaplatform.decision.api;

import io.nbaplatform.decision.engine.DecisionEngine;
import io.nbaplatform.decision.engine.DecisionLogPublisher;
import io.nbaplatform.decision.engine.DecisionOutcome;
import io.nbaplatform.decision.engine.DecisionProperties;
import io.nbaplatform.decision.spi.Candidate;
import io.nbaplatform.decision.spi.DecisionRequest;
import io.nbaplatform.decision.spi.ScoringExplanation;
import io.nbaplatform.events.ValidFor;
import io.nbaplatform.platform.tenant.TenantContext;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** TMF680 Recommendation Management: request the next best actions for a party in a channel. */
@RestController
@RequestMapping(RecommendationController.BASE_PATH)
public class RecommendationController {

    static final String BASE_PATH = "/tmf-api/recommendationManagement/v4/recommendation";
    private static final Duration VALIDITY = Duration.ofMinutes(15);

    private final DecisionEngine engine;
    private final DecisionLogPublisher decisionLog;
    private final DecisionProperties properties;

    public RecommendationController(DecisionEngine engine, DecisionLogPublisher decisionLog,
                                    DecisionProperties properties) {
        this.engine = engine;
        this.decisionLog = decisionLog;
        this.properties = properties;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Recommendation recommend(@Valid @RequestBody RecommendationRequest body,
                                    @RequestParam(defaultValue = "false") boolean explain) {
        int maxItems = body.maxItems() == null ? properties.defaultMaxItems() : body.maxItems();
        if (maxItems > properties.maxItemsLimit()) {
            throw new IllegalArgumentException("maxItems must be at most " + properties.maxItemsLimit());
        }
        Map<String, Object> attributes = new HashMap<>();
        if (body.characteristic() != null) {
            body.characteristic().stream()
                    .filter(c -> c.value() != null)
                    .forEach(c -> attributes.put(c.name(), c.value()));
        }
        String partyId = body.relatedParty().get(0).id();
        DecisionRequest request = new DecisionRequest(TenantContext.require(), partyId, body.channel().id(),
                attributes, maxItems, Instant.now());

        DecisionOutcome outcome = engine.decide(request);
        decisionLog.publish(outcome, engine.strategy());
        return toResource(outcome, explain);
    }

    /** Contributions returned per item, biggest effect first. */
    static final int EXPLAINED_CONTRIBUTIONS = 5;

    private static Recommendation.Explanation explanation(Candidate c) {
        ScoringExplanation scoring = c.scoring();
        List<Recommendation.Contribution> contributions = scoring == null ? List.of() : scoring.contributions().stream()
                .limit(EXPLAINED_CONTRIBUTIONS)
                .map(f -> new Recommendation.Contribution(f.feature(), f.value(), f.weight(), f.contribution()))
                .toList();
        return new Recommendation.Explanation(
                scoring == null ? null : scoring.scorer(),
                scoring == null ? null : scoring.modelName(),
                scoring == null ? null : scoring.modelVersion(),
                c.modelPropensity(), c.propensity(), Double.isNaN(c.value()) ? c.offering().businessValue() : c.value(),
                c.priorityLever(), c.priority(),
                scoring == null ? null : scoring.fallbackReason(),
                contributions);
    }

    private static Recommendation toResource(DecisionOutcome outcome, boolean explain) {
        List<Recommendation.RecommendationItem> items = new ArrayList<>();
        for (int i = 0; i < outcome.items().size(); i++) {
            Candidate c = outcome.items().get(i);
            items.add(new Recommendation.RecommendationItem(
                    String.valueOf(i + 1),
                    i + 1,
                    new Recommendation.Ref(c.offering().id(), c.offering().name(), null, "ProductOffering"),
                    c.campaign() == null ? null
                            : new Recommendation.Ref(c.campaign().id(), c.campaign().name(), null, "Promotion"),
                    outcome.fallback() ? null : c.priority(),
                    outcome.fallback() ? null : c.propensity(),
                    c.reasonCodes(),
                    explain && !outcome.fallback() ? explanation(c) : null,
                    "NbaRecommendationItem"));
        }
        List<Recommendation.SuppressedItem> suppressed = !explain ? null : outcome.suppressed().stream()
                .map(s -> new Recommendation.SuppressedItem(
                        new Recommendation.Ref(s.candidate().offering().id(), s.candidate().offering().name(), null,
                                "ProductOffering"),
                        s.candidate().campaign() == null ? null : new Recommendation.Ref(s.candidate().campaign().id(),
                                s.candidate().campaign().name(), null, "Promotion"),
                        s.stage(),
                        s.reasonCode()))
                .toList();
        Instant now = outcome.request().now();
        return new Recommendation(
                outcome.decisionId(),
                "Next best action",
                "nextBestAction",
                new ValidFor(now, now.plus(VALIDITY)),
                new Recommendation.Ref(outcome.request().channel(), null, null, "Channel"),
                List.of(new Recommendation.Ref(outcome.request().partyId(), null, "customer", "Party")),
                items,
                suppressed,
                outcome.fallback(),
                outcome.fallbackReason(),
                "NbaRecommendation",
                "Recommendation");
    }
}
