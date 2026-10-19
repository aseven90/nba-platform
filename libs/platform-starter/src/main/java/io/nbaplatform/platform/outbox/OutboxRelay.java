package io.nbaplatform.platform.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes outbox rows to Kafka in id order. Delivery is at-least-once: a row is marked published only
 * after the broker acknowledges it, so consumers must be idempotent (they key on the CloudEvents id).
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;
    private final OutboxProperties properties;

    public OutboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafka, TransactionTemplate tx,
                       OutboxProperties properties) {
        this.repository = repository;
        this.kafka = kafka;
        this.tx = tx;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${nba.outbox.poll-interval:500ms}")
    public void relay() {
        try {
            Integer sent;
            do {
                sent = tx.execute(status -> publishBatch());
            } while (sent != null && sent == properties.getBatchSize());
        } catch (RuntimeException e) {
            // Rows stay unpublished and are retried on the next tick.
            log.warn("Outbox relay failed, will retry: {}", e.getMessage());
        }
    }

    int publishBatch() {
        List<OutboxMessage> batch = repository.lockUnpublished(Limit.of(properties.getBatchSize()));
        for (OutboxMessage message : batch) {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(message.getTopic(), message.getMessageKey(), message.getPayload());
            record.headers().add("ce_id", message.getEventId().getBytes(StandardCharsets.UTF_8));
            record.headers().add("ce_type", message.getEventType().getBytes(StandardCharsets.UTF_8));
            record.headers().add("content-type", "application/cloudevents+json".getBytes(StandardCharsets.UTF_8));
            try {
                kafka.send(record).get(properties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while publishing outbox", e);
            } catch (Exception e) {
                throw new IllegalStateException("Kafka send failed for outbox row " + message.getId(), e);
            }
            message.markPublished();
        }
        return batch.size();
    }

    @Scheduled(fixedDelayString = "${nba.outbox.cleanup-interval:10m}")
    public void cleanup() {
        Duration retention = properties.getRetention();
        Integer deleted = tx.execute(status -> repository.deletePublishedBefore(Instant.now().minus(retention)));
        if (deleted != null && deleted > 0) {
            log.debug("Deleted {} published outbox rows", deleted);
        }
    }
}
