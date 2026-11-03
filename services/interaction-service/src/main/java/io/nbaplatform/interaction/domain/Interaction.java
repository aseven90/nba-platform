package io.nbaplatform.interaction.domain;

import io.nbaplatform.events.InteractionRecorded.Outcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Immutable record of a party's interaction with a recommended action. */
@Entity
@Table(name = "party_interaction")
public class Interaction {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "party_id", nullable = false)
    private String partyId;

    @Column(name = "decision_id")
    private String decisionId;

    @Column(name = "offering_id", nullable = false)
    private String offeringId;

    @Column(name = "campaign_id")
    private String campaignId;

    @Column(nullable = false)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Outcome outcome;

    @Column(name = "interaction_date", nullable = false)
    private Instant interactionDate;

    protected Interaction() {
    }

    public Interaction(String id, String tenantId, String idempotencyKey, String partyId, String decisionId,
                       String offeringId, String campaignId, String channel, Outcome outcome, Instant interactionDate) {
        this.id = id;
        this.tenantId = tenantId;
        this.idempotencyKey = idempotencyKey;
        this.partyId = partyId;
        this.decisionId = decisionId;
        this.offeringId = offeringId;
        this.campaignId = campaignId;
        this.channel = channel;
        this.outcome = outcome;
        this.interactionDate = interactionDate;
    }

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getPartyId() {
        return partyId;
    }

    public String getDecisionId() {
        return decisionId;
    }

    public String getOfferingId() {
        return offeringId;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public String getChannel() {
        return channel;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public Instant getInteractionDate() {
        return interactionDate;
    }
}
