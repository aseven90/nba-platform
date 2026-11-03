package io.nbaplatform.decision.spi;

import java.time.Instant;
import java.util.Map;

/**
 * Who the decision is for and in which context.
 *
 * @param attributes context characteristics supplied by the caller, merged with features from the feature store
 */
public record DecisionRequest(
        String tenantId,
        String partyId,
        String channel,
        Map<String, Object> attributes,
        int maxItems,
        Instant now) {

    public DecisionRequest {
        attributes = Map.copyOf(attributes);
    }
}
