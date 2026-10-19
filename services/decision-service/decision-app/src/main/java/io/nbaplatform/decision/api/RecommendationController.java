package io.nbaplatform.decision.api;

import io.nbaplatform.decision.engine.DecisionEngine;
import io.nbaplatform.decision.engine.DecisionLogPublisher;
import io.nbaplatform.decision.engine.DecisionOutcome;
import io.nbaplatform.decision.engine.DecisionProperties;
import io.nbaplatform.decision.spi.Candidate;
import io.nbaplatform.decision.spi.DecisionRequest;
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
    public Recommendation recommend(@Valid @RequestBody RecommendationRequest body) {
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
        return toResource(outcome);
    }

    private static Recommendation toResource(DecisionOutcome outcome) {
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
                    "NbaRecommendationItem"));
        }
        Instant now = outcome.request().now();
        return new Recommendation(
                outcome.decisionId(),
                "Next best action",
                "nextBestAction",
                new ValidFor(now, now.plus(VALIDITY)),
                new Recommendation.Ref(outcome.request().channel(), null, null, "Channel"),
                List.of(new Recommendation.Ref(outcome.request().partyId(), null, "customer", "Party")),
                items,
                outcome.fallback(),
                "NbaRecommendation",
                "Recommendation");
    }
}
