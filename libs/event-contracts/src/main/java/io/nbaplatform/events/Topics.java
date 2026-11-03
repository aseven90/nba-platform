package io.nbaplatform.events;

/** Kafka topic names. Keys are always {@code tenantId:aggregateId} so per-entity ordering holds. */
public final class Topics {

    /** Compacted. Latest state of each action offering (TMF620 ProductOffering). */
    public static final String CATALOG_OFFERING_CHANGED = "nba.catalog.offering.changed";

    /** Compacted. Latest state of each campaign (TMF671 Promotion). */
    public static final String CAMPAIGN_CHANGED = "nba.campaign.changed";

    /** Append-only decision log, keyed by tenantId:partyId. */
    public static final String DECISION_MADE = "nba.decision.made";

    /** Impressions and responses, keyed by tenantId:partyId. */
    public static final String INTERACTION_RECORDED = "nba.interaction.recorded";

    private Topics() {
    }

    public static String key(String tenantId, String id) {
        return tenantId + ":" + id;
    }
}
