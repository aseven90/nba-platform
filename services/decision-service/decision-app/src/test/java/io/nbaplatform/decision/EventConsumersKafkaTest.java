package io.nbaplatform.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nbaplatform.decision.state.InMemoryLearningStore;
import io.nbaplatform.decision.state.SnapshotStore;
import io.nbaplatform.events.CampaignAction;
import io.nbaplatform.events.EventEnvelope;
import io.nbaplatform.events.EventTypes;
import io.nbaplatform.events.InteractionRecorded;
import io.nbaplatform.events.InteractionRecorded.Outcome;
import io.nbaplatform.events.OfferingChanged;
import io.nbaplatform.events.Topics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

/** End-to-end over a real (embedded) broker: change and interaction events reach the decision state. */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true"})
@EmbeddedKafka(partitions = 1, topics = {Topics.CATALOG_OFFERING_CHANGED, Topics.CAMPAIGN_CHANGED,
        Topics.INTERACTION_RECORDED, Topics.DECISION_MADE})
@ActiveProfiles("test")
class EventConsumersKafkaTest {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    SnapshotStore snapshot;

    @Autowired
    InMemoryLearningStore learning;

    @Test
    void consumesChangeAndInteractionEvents() throws Exception {
        send(Topics.CATALOG_OFFERING_CHANGED, EventTypes.OFFERING_CHANGED, "o-1",
                TestData.offering("o-1", 10, 0));
        send(Topics.CAMPAIGN_CHANGED, EventTypes.CAMPAIGN_CHANGED, "c-1",
                TestData.campaign("c-1", null, new CampaignAction("o-1", 1)));
        EventEnvelope<InteractionRecorded> impression = EventEnvelope.of("/test", EventTypes.INTERACTION_RECORDED,
                "globex", "p-1", new InteractionRecorded("i-1", "p-1", "d-1", "o-1", "c-1", "web",
                        Outcome.IMPRESSION, Instant.now()));
        kafka.send(Topics.INTERACTION_RECORDED, "globex:p-1", mapper.writeValueAsString(impression));
        kafka.send(Topics.INTERACTION_RECORDED, "globex:p-1", mapper.writeValueAsString(impression)); // duplicate
        send(Topics.INTERACTION_RECORDED, EventTypes.INTERACTION_RECORDED, "p-1",
                new InteractionRecorded("i-2", "p-1", "d-1", "o-1", "c-1", "web", Outcome.ACCEPTED, Instant.now()));
        kafka.send(Topics.CAMPAIGN_CHANGED, "globex:bad", "not json");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(snapshot.offering("globex", "o-1")).isPresent();
            assertThat(snapshot.campaigns("globex")).extracting(c -> c.id()).containsExactly("c-1");
            assertThat(learning.stats("globex", "c-1", "o-1").impressions()).isEqualTo(1);
            assertThat(learning.stats("globex", "c-1", "o-1").accepts()).isEqualTo(1);
            assertThat(learning.impressionsLast24h("globex", "p-1", "c-1", Instant.now())).isEqualTo(1);
        });

        send(Topics.CATALOG_OFFERING_CHANGED, EventTypes.OFFERING_CHANGED, "o-1",
                new OfferingChanged("o-1", null, null, null, null, List.of(), 0, null, null, true, 2));
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(snapshot.offering("globex", "o-1")).isEmpty());
    }

    private void send(String topic, String type, String subject, Object data) throws Exception {
        EventEnvelope<Object> envelope = EventEnvelope.of("/test", type, "globex", subject, data);
        kafka.send(topic, Topics.key("globex", subject), mapper.writeValueAsString(envelope));
    }
}
