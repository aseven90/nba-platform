package io.nbaplatform.decision;

import io.nbaplatform.decision.state.SnapshotStore;
import io.nbaplatform.events.CampaignAction;
import io.nbaplatform.events.CampaignChanged;
import io.nbaplatform.events.OfferingChanged;
import io.nbaplatform.events.PropensityModel;
import java.util.List;
import java.util.Map;

final class TestData {

    private TestData() {
    }

    static OfferingChanged offering(String id, double value, double intercept) {
        return new OfferingChanged(id, "Offer " + id, null, "upsell", "Active", List.of("web"), value, null,
                new PropensityModel("v1", intercept, Map.of("tenureMonths", 0.05)), false, 1);
    }

    static CampaignChanged campaign(String id, String rule, CampaignAction... actions) {
        return new CampaignChanged(id, "Campaign " + id, "active", null, List.of("web"), rule, 0, 0,
                List.of(actions), false, 1);
    }

    static void seed(SnapshotStore store) {
        store.apply("acme", offering("upgrade", 100, -3));   // low propensity, high value
        store.apply("acme", offering("addon", 20, -1));      // higher propensity, lower value
        store.apply("acme", offering("safe-1", 0, 0));
        store.apply("acme", campaign("growth", "tenureMonths >= 12",
                new CampaignAction("upgrade", 1.0), new CampaignAction("addon", 1.0)));
        store.apply("acme", campaign("vip-only", "segment == 'vip'", new CampaignAction("addon", 2.0)));
    }
}
