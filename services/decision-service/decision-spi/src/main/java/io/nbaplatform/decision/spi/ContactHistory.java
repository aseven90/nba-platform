package io.nbaplatform.decision.spi;

import java.time.Instant;

/** How often a party has been contacted, for fatigue and contact-policy rules. */
public interface ContactHistory {

    /** Impressions of {@code campaignId} shown to the party in the 24 hours before {@code now}. */
    int impressionsLast24h(String tenantId, String partyId, String campaignId, Instant now);
}
