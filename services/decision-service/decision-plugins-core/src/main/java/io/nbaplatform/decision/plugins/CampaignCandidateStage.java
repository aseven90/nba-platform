package io.nbaplatform.decision.plugins;

import io.nbaplatform.decision.spi.Candidate;
import io.nbaplatform.decision.spi.DecisionContext;
import io.nbaplatform.decision.spi.DecisionRequest;
import io.nbaplatform.decision.spi.DecisionStage;
import io.nbaplatform.decision.spi.Stage;
import io.nbaplatform.events.CampaignAction;
import io.nbaplatform.events.CampaignChanged;
import io.nbaplatform.events.OfferingChanged;
import java.util.List;
import java.util.Optional;

/** Proposes every action of every live campaign that targets the request channel. */
public class CampaignCandidateStage implements DecisionStage {

    @Override
    public Stage stage() {
        return Stage.CANDIDATES;
    }

    @Override
    public String name() {
        return "campaign-candidates";
    }

    @Override
    public void apply(DecisionContext context) {
        DecisionRequest request = context.request();
        for (CampaignChanged campaign : context.snapshot().campaigns(request.tenantId())) {
            if (!Lifecycle.campaignLive(campaign.lifecycleStatus())
                    || (campaign.validFor() != null && !campaign.validFor().includes(request.now()))
                    || !targets(campaign.channels(), request.channel())) {
                continue;
            }
            for (CampaignAction action : campaign.actions()) {
                Optional<OfferingChanged> offering = context.snapshot().offering(request.tenantId(), action.offeringId());
                OfferingChanged unknown = new OfferingChanged(action.offeringId(), null, null, null, null,
                        List.of(), 0, null, null, false, 0);
                if (offering.isEmpty()) {
                    context.reject(new Candidate(unknown, campaign, action.priorityLever()), "OFFERING_UNKNOWN");
                    continue;
                }
                OfferingChanged o = offering.get();
                Candidate candidate = new Candidate(o, campaign, action.priorityLever());
                if (!Lifecycle.offeringLive(o.lifecycleStatus())
                        || (o.validFor() != null && !o.validFor().includes(request.now()))) {
                    context.reject(candidate, "OFFERING_NOT_LIVE");
                } else if (!targets(o.channels(), request.channel())) {
                    context.reject(candidate, "OFFERING_CHANNEL_MISMATCH");
                } else {
                    context.add(candidate);
                }
            }
        }
    }

    /** An empty channel list means every channel. */
    private static boolean targets(List<String> channels, String channel) {
        return channels == null || channels.isEmpty() || channels.contains(channel);
    }
}
