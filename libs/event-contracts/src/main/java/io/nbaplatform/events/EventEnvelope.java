package io.nbaplatform.events;

import java.time.Instant;
import java.util.UUID;

/**
 * CloudEvents 1.0 structured-mode envelope with the tenant as an extension attribute.
 *
 * @param <T> payload type
 */
public record EventEnvelope<T>(
        String specversion,
        String id,
        String source,
        String type,
        Instant time,
        String tenantid,
        String subject,
        T data) {

    public static <T> EventEnvelope<T> of(String source, String type, String tenantId, String subject, T data) {
        return new EventEnvelope<>("1.0", UUID.randomUUID().toString(), source, type, Instant.now(), tenantId, subject, data);
    }
}
