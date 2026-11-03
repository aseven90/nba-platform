package io.nbaplatform.decision.plugins;

import io.nbaplatform.decision.spi.ContactHistory;
import io.nbaplatform.decision.spi.DecisionContext;
import io.nbaplatform.decision.spi.DecisionRequest;
import io.nbaplatform.decision.spi.DecisionSnapshot;
import io.nbaplatform.decision.spi.LearningState;
import io.nbaplatform.events.CampaignAction;
import io.nbaplatform.events.CampaignChanged;
import io.nbaplatform.events.OfferingChanged;
import io.nbaplatform.events.PropensityModel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class Fixtures {

    static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    final Map<String, OfferingChanged> offerings = new HashMap<>();
    final List<CampaignChanged> campaigns = new ArrayList<>();
    final Map<String, LearningState.ArmStats> arms = new HashMap<>();
    final Map<String, Integer> impressions = new HashMap<>();

    static OfferingChanged offering(String id, double value, PropensityModel model) {
        return new OfferingChanged(id, id, null, null, "Active", List.of(), value, null, model, false, 1);
    }

    static CampaignChanged campaign(String id, String rule, double controlPercent, int cap, CampaignAction... actions) {
        return new CampaignChanged(id, id, "active", null, List.of("web"), rule, controlPercent, cap,
                List.of(actions), false, 1);
    }

    Fixtures with(OfferingChanged o) {
        offerings.put(o.id(), o);
        return this;
    }

    Fixtures with(CampaignChanged c) {
        campaigns.add(c);
        return this;
    }

    DecisionContext context(String partyId, Map<String, Object> attributes, int maxItems) {
        DecisionRequest request = new DecisionRequest("acme", partyId, "web", attributes, maxItems, NOW);
        DecisionSnapshot snapshot = new DecisionSnapshot() {
            @Override
            public Collection<CampaignChanged> campaigns(String tenantId) {
                return campaigns;
            }

            @Override
            public Optional<OfferingChanged> offering(String tenantId, String offeringId) {
                return Optional.ofNullable(offerings.get(offeringId));
            }
        };
        LearningState learning = (t, c, o) -> arms.getOrDefault(c + "/" + o, LearningState.ArmStats.EMPTY);
        ContactHistory history = (t, p, c, now) -> impressions.getOrDefault(p + "/" + c, 0);
        return new DecisionContext("d-1", request, snapshot, learning, history);
    }

    static void run(DecisionContext context, io.nbaplatform.decision.spi.DecisionStage... stages) {
        for (var stage : stages) {
            context.enterStage(stage.name());
            stage.apply(context);
        }
    }
}
