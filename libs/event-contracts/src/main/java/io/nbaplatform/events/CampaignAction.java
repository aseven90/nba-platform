package io.nbaplatform.events;

/** An action offered by a campaign, with a business priority lever (1.0 = neutral). */
public record CampaignAction(String offeringId, double priorityLever) {
}
