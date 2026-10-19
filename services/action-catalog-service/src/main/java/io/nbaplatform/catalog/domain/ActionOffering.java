package io.nbaplatform.catalog.domain;

import io.nbaplatform.events.PropensityModel;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;

/** An action that can be recommended: an offer, a message, a service action, a retention save. */
@Entity
@Table(name = "action_offering")
public class ActionOffering {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    private String description;

    private String category;

    @Column(name = "lifecycle_status", nullable = false)
    private String lifecycleStatus;

    @Convert(converter = JsonConverters.StringList.class)
    @Column(columnDefinition = "text")
    private List<String> channels;

    @Column(name = "business_value", nullable = false)
    private double businessValue;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Convert(converter = JsonConverters.Model.class)
    @Column(name = "propensity_model", columnDefinition = "text")
    private PropensityModel propensityModel;

    @Version
    private long version;

    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;

    protected ActionOffering() {
    }

    public ActionOffering(String id, String tenantId) {
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public List<String> getChannels() {
        return channels;
    }

    public void setChannels(List<String> channels) {
        this.channels = channels;
    }

    public double getBusinessValue() {
        return businessValue;
    }

    public void setBusinessValue(double businessValue) {
        this.businessValue = businessValue;
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

    public PropensityModel getPropensityModel() {
        return propensityModel;
    }

    public void setPropensityModel(PropensityModel propensityModel) {
        this.propensityModel = propensityModel;
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
