package io.nbaplatform.decision.state;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nbaplatform.events.CampaignChanged;
import io.nbaplatform.events.EventEnvelope;
import io.nbaplatform.events.InteractionRecorded;
import io.nbaplatform.events.OfferingChanged;
import io.nbaplatform.events.Topics;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds the in-memory state from the event backbone. Each replica uses its own consumer group
 * ({@code nba.decision.instance-id}) so every replica sees every event and rebuilds its state from the start of
 * the compacted topics after a restart. Malformed events are logged and skipped, never retried forever.
 */
@Component
public class EventConsumers {

    private static final Logger log = LoggerFactory.getLogger(EventConsumers.class);

    private final ObjectMapper mapper;
    private final SnapshotStore snapshot;
    private final InMemoryLearningStore learning;

    public EventConsumers(ObjectMapper mapper, SnapshotStore snapshot, InMemoryLearningStore learning) {
        this.mapper = mapper;
        this.snapshot = snapshot;
        this.learning = learning;
    }

    @KafkaListener(id = "offerings", topics = Topics.CATALOG_OFFERING_CHANGED,
            groupId = "decision-${nba.decision.instance-id:local}")
    void onOffering(String json) {
        EventEnvelope<OfferingChanged> e = read(json, new TypeReference<>() { });
        if (e != null) {
            snapshot.apply(e.tenantid(), e.data());
        }
    }

    @KafkaListener(id = "campaigns", topics = Topics.CAMPAIGN_CHANGED,
            groupId = "decision-${nba.decision.instance-id:local}")
    void onCampaign(String json) {
        EventEnvelope<CampaignChanged> e = read(json, new TypeReference<>() { });
        if (e != null) {
            snapshot.apply(e.tenantid(), e.data());
        }
    }

    @KafkaListener(id = "interactions", topics = Topics.INTERACTION_RECORDED,
            groupId = "decision-${nba.decision.instance-id:local}")
    void onInteraction(String json) {
        EventEnvelope<InteractionRecorded> e = read(json, new TypeReference<>() { });
        if (e != null) {
            learning.apply(e.id(), e.tenantid(), e.data());
        }
    }

    private <T> EventEnvelope<T> read(String json, TypeReference<EventEnvelope<T>> type) {
        try {
            EventEnvelope<T> envelope = mapper.readValue(json, type);
            if (envelope.tenantid() == null || envelope.data() == null) {
                log.warn("Skipping event without tenant or data: {}", envelope.id());
                return null;
            }
            return envelope;
        } catch (IOException e) {
            log.warn("Skipping malformed event: {}", e.getMessage());
            return null;
        }
    }
}
