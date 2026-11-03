package io.nbaplatform.events;

/** CloudEvents {@code type} values. */
public final class EventTypes {

    public static final String OFFERING_CHANGED = "io.nbaplatform.catalog.offering.changed.v1";
    public static final String CAMPAIGN_CHANGED = "io.nbaplatform.campaign.changed.v1";
    public static final String DECISION_MADE = "io.nbaplatform.decision.made.v1";
    public static final String INTERACTION_RECORDED = "io.nbaplatform.interaction.recorded.v1";

    private EventTypes() {
    }
}
