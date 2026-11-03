package io.nbaplatform.decision.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.nbaplatform.decision.spi.DecisionRequest;
import io.nbaplatform.events.Topics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class DecisionLogPublisherTest {

    @Test
    @SuppressWarnings("unchecked")
    void doesNotBlockTheCallerWhenTheBrokerIsUnreachable() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        // KafkaTemplate.send blocks the calling thread while it waits for metadata from a broker that is down.
        when(kafka.send(eq(Topics.DECISION_MADE), anyString(), anyString())).thenAnswer(invocation -> {
            Thread.sleep(3_000);
            return new CompletableFuture<>();
        });
        var publisher = new DecisionLogPublisher(kafka, new ObjectMapper().registerModule(new JavaTimeModule()));
        var request = new DecisionRequest("t1", "p1", "web", Map.of(), 3, Instant.now());
        var outcome = new DecisionOutcome("d1", request, List.of(), List.of(), null, 10);

        long start = System.nanoTime();
        publisher.publish(outcome, "priority-pvl");
        publisher.publish(outcome, "priority-pvl");

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(500));
        verify(kafka, timeout(1_000)).send(eq(Topics.DECISION_MADE), eq(Topics.key("t1", "p1")), anyString());
        publisher.shutdown();
    }
}
