package io.nbaplatform.events;

import java.util.List;

/** Latest state of a campaign. {@code deleted=true} removes it from decisioning. */
public record CampaignChanged(
        String id,
        String name,
        String lifecycleStatus,
        ValidFor validFor,
        List<String> channels,
        String eligibilityRule,
        double controlGroupPercent,
        int maxImpressionsPerDay,
        List<CampaignAction> actions,
        boolean deleted,
        long version) {
}
