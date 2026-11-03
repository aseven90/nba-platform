package io.nbaplatform.platform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nbaplatform.events.EventEnvelope;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Appends events to the outbox. Must be called inside the transaction that changes the aggregate. */
public class OutboxWriter {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final String source;

    public OutboxWriter(OutboxRepository repository, ObjectMapper objectMapper, String source) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.source = source;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> EventEnvelope<T> append(String topic, String type, String tenantId, String subject, T data) {
        EventEnvelope<T> envelope = EventEnvelope.of(source, type, tenantId, subject, data);
        try {
            String json = objectMapper.writeValueAsString(envelope);
            repository.save(new OutboxMessage(envelope.id(), topic, tenantId + ":" + subject, type, json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event " + type, e);
        }
        return envelope;
    }
}
