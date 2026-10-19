package io.nbaplatform.decision.state;

import io.nbaplatform.decision.spi.DecisionSnapshot;
import io.nbaplatform.events.CampaignChanged;
import io.nbaplatform.events.OfferingChanged;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory copy of campaigns and offerings per tenant, rebuilt on start-up by replaying the compacted change
 * topics and kept current by consuming them. Reads never leave the process. Older versions are ignored, so a
 * replayed or duplicated event cannot roll state back.
 */
@Component
public class SnapshotStore implements DecisionSnapshot {

    private final Map<String, Map<String, OfferingChanged>> offerings = new ConcurrentHashMap<>();
    private final Map<String, Map<String, CampaignChanged>> campaigns = new ConcurrentHashMap<>();

    public void apply(String tenantId, OfferingChanged event) {
        Map<String, OfferingChanged> tenant = offerings.computeIfAbsent(tenantId, t -> new ConcurrentHashMap<>());
        tenant.compute(event.id(), (id, current) -> {
            if (current != null && current.version() > event.version()) {
                return current;
            }
            return event.deleted() ? null : event;
        });
    }

    public void apply(String tenantId, CampaignChanged event) {
        Map<String, CampaignChanged> tenant = campaigns.computeIfAbsent(tenantId, t -> new ConcurrentHashMap<>());
        tenant.compute(event.id(), (id, current) -> {
            if (current != null && current.version() > event.version()) {
                return current;
            }
            return event.deleted() ? null : event;
        });
    }

    @Override
    public Collection<CampaignChanged> campaigns(String tenantId) {
        Map<String, CampaignChanged> tenant = campaigns.get(tenantId);
        return tenant == null ? List.of() : tenant.values();
    }

    @Override
    public Optional<OfferingChanged> offering(String tenantId, String offeringId) {
        Map<String, OfferingChanged> tenant = offerings.get(tenantId);
        return tenant == null ? Optional.empty() : Optional.ofNullable(tenant.get(offeringId));
    }
}
