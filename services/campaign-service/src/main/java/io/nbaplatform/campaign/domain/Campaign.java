package io.nbaplatform.campaign.domain;

import io.nbaplatform.events.CampaignAction;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;

/** Targets a set of actions to eligible parties on given channels during a validity window. */
@Entity
@Table(name = "campaign")
public class Campaign {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "lifecycle_status", nullable = false)
    private String lifecycleStatus;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Convert(converter = JsonConverters.StringList.class)
    @Column(columnDefinition = "text")
    private List<String> channels;

    @Column(name = "eligibility_rule", length = 4000)
    private String eligibilityRule;

    @Column(name = "control_group_percent", nullable = false)
    private double controlGroupPercent;

    @Column(name = "max_impressions_per_day", nullable = false)
    private int maxImpressionsPerDay;

    @Convert(converter = JsonConverters.Actions.class)
    @Column(columnDefinition = "text")
    private List<CampaignAction> actions;

    @Version
    private long version;

    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;

    protected Campaign() {
    }

    public Campaign(String id, String tenantId) {
        this.id = id;
        this.tenantId = tenantId;
    }

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(Instant validFrom) {
        this.validFrom = validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public void setValidTo(Instant validTo) {
        this.validTo = validTo;
    }

    public List<String> getChannels() {
        return channels;
    }

    public void setChannels(List<String> channels) {
        this.channels = channels;
    }

    public String getEligibilityRule() {
        return eligibilityRule;
    }

    public void setEligibilityRule(String eligibilityRule) {
        this.eligibilityRule = eligibilityRule;
    }

    public double getControlGroupPercent() {
        return controlGroupPercent;
    }

    public void setControlGroupPercent(double controlGroupPercent) {
        this.controlGroupPercent = controlGroupPercent;
    }

    public int getMaxImpressionsPerDay() {
        return maxImpressionsPerDay;
    }

    public void setMaxImpressionsPerDay(int maxImpressionsPerDay) {
        this.maxImpressionsPerDay = maxImpressionsPerDay;
    }

    public List<CampaignAction> getActions() {
        return actions;
    }

    public void setActions(List<CampaignAction> actions) {
        this.actions = actions;
    }

    public long getVersion() {
        return version;
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }

    public void touch() {
        this.lastUpdate = Instant.now();
    }
}
