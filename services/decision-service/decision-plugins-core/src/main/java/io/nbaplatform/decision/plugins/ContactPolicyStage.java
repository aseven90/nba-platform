package io.nbaplatform.decision.plugins;

import io.nbaplatform.decision.spi.DecisionContext;
import io.nbaplatform.decision.spi.DecisionStage;
import io.nbaplatform.decision.spi.Stage;

/** Enforces each campaign's daily impression cap per party (contact fatigue). */
public class ContactPolicyStage implements DecisionStage {

    @Override
    public Stage stage() {
        return Stage.ELIGIBILITY;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public String name() {
        return "contact-policy";
    }

    @Override
    public void apply(DecisionContext context) {
        var request = context.request();
        context.suppressIf(c -> {
            int cap = c.campaign().maxImpressionsPerDay();
            if (cap <= 0) {
                return null;
            }
            int seen = context.contactHistory()
                    .impressionsLast24h(request.tenantId(), request.partyId(), c.campaign().id(), request.now());
            return seen >= cap ? "CONTACT_CAP_REACHED" : null;
        });
    }
}
