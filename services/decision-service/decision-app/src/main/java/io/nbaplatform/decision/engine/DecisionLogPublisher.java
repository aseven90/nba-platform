package io.nbaplatform.decision.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nbaplatform.decision.spi.Candidate;
import io.nbaplatform.events.DecisionMade;
import io.nbaplatform.events.EventEnvelope;
import io.nbaplatform.events.EventTypes;
import io.nbaplatform.events.Topics;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Appends every decision, including what was suppressed and why, to the decision log topic. Publishing is
 * asynchronous so it never adds latency to the response; the producer retries transient broker errors.
 */
@Component
public class DecisionLogPublisher {

    private static final Logger log = LoggerFactory.getLogger(DecisionLogPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public DecisionLogPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    public void publish(DecisionOutcome outcome, String strategy) {
        var request = outcome.request();
        List<DecisionMade.Item> items = new ArrayList<>();
        for (int i = 0; i < outcome.items().size(); i++) {
            Candidate c = outcome.items().get(i);
            items.add(new DecisionMade.Item(i + 1, c.offering().id(), c.campaign() == null ? null : c.campaign().id(),
                    c.priority(), c.propensity(), c.offering().businessValue(), c.reasonCodes()));
        }
        List<DecisionMade.Suppression> suppressed = outcome.suppressed().stream()
                .map(s -> new DecisionMade.Suppression(s.candidate().offering().id(),
                        s.candidate().campaign() == null ? null : s.candidate().campaign().id(), s.reasonCode()))
                .toList();
        DecisionMade event = new DecisionMade(outcome.decisionId(), request.partyId(), request.channel(),
                Instant.now(), outcome.fallback(), outcome.fallback() ? "safe-default" : strategy, items, suppressed,
                outcome.latencyMicros());
        EventEnvelope<DecisionMade> envelope = EventEnvelope.of("/nba/decision-service", EventTypes.DECISION_MADE,
                request.tenantId(), request.partyId(), event);
        try {
            kafka.send(Topics.DECISION_MADE, Topics.key(request.tenantId(), request.partyId()),
                            mapper.writeValueAsString(envelope))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Decision {} not written to decision log: {}", outcome.decisionId(),
                                    error.toString());
                        }
                    });
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Decision {} not written to decision log: {}", outcome.decisionId(), e.toString());
        }
    }
}
