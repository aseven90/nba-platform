package io.nbaplatform.decision.spi;

import io.nbaplatform.events.CampaignChanged;
import io.nbaplatform.events.OfferingChanged;
import java.util.Collection;
import java.util.Optional;

/** Read-only, in-memory view of campaigns and offerings, kept current from events. */
public interface DecisionSnapshot {

    Collection<CampaignChanged> campaigns(String tenantId);

    Optional<OfferingChanged> offering(String tenantId, String offeringId);
}
