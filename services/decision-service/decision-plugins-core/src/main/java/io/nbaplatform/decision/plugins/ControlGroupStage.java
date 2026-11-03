package io.nbaplatform.decision.plugins;

import io.nbaplatform.decision.spi.DecisionContext;
import io.nbaplatform.decision.spi.DecisionStage;
import io.nbaplatform.decision.spi.Stage;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Holds out a stable, random share of parties per campaign so incremental lift can be measured honestly. The
 * assignment is a deterministic hash of tenant, campaign and party, so a party stays in the same group.
 */
public class ControlGroupStage implements DecisionStage {

    @Override
    public Stage stage() {
        return Stage.ELIGIBILITY;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public String name() {
        return "control-group";
    }

    @Override
    public void apply(DecisionContext context) {
        var request = context.request();
        context.suppressIf(c -> inControlGroup(request.tenantId(), c.campaign().id(), request.partyId(),
                c.campaign().controlGroupPercent()) ? "CONTROL_GROUP" : null);
    }

    static boolean inControlGroup(String tenantId, String campaignId, String partyId, double percent) {
        if (percent <= 0) {
            return false;
        }
        UUID hash = UUID.nameUUIDFromBytes((tenantId + '|' + campaignId + '|' + partyId).getBytes(StandardCharsets.UTF_8));
        long bucket = Math.floorMod(hash.getMostSignificantBits(), 10_000L);
        return bucket < Math.round(percent * 100);
    }
}
