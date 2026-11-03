package io.nbaplatform.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nbaplatform.catalog.api.OfferingService;
import io.nbaplatform.catalog.api.ProductOffering;
import io.nbaplatform.events.Topics;
import io.nbaplatform.platform.outbox.OutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

/** The outbox relay publishes committed changes to Kafka, keyed by tenant and aggregate, then marks them sent. */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "nba.outbox.relay-enabled=true",
        "nba.outbox.poll-interval=100ms",
        "spring.datasource.url=jdbc:h2:mem:catalog-relay;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@EmbeddedKafka(partitions = 1, topics = Topics.CATALOG_OFFERING_CHANGED)
@ActiveProfiles("test")
class OutboxRelayKafkaTest {

    @Autowired
    OfferingService service;

    @Autowired
    OutboxRepository outbox;

    @Autowired
    EmbeddedKafkaBroker broker;

    @Autowired
    ObjectMapper mapper;

    @Test
    void publishesCommittedChanges() throws Exception {
        ProductOffering created = service.create("acme", new ProductOffering(null, null, "Relay me", null, "Active",
                null, null, null, null, null, 5.0, null, null, null));

        Map<String, Object> props = KafkaTestUtils.consumerProps("relay-test", "false", broker);
        props.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, Topics.CATALOG_OFFERING_CHANGED);
            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, Topics.CATALOG_OFFERING_CHANGED, Duration.ofSeconds(30));
            assertThat(record.key()).isEqualTo("acme:" + created.id());
            assertThat(new String(record.headers().lastHeader("ce_type").value(), StandardCharsets.UTF_8))
                    .isEqualTo("io.nbaplatform.catalog.offering.changed.v1");
            assertThat(mapper.readTree(record.value()).at("/data/name").asText()).isEqualTo("Relay me");
        }
        await().atMost(Duration.ofSeconds(10)).until(() -> outbox.countByPublishedAtIsNull() == 0);
    }
}
