package io.nbaplatform.campaign.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nbaplatform.events.ValidFor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;

/**
 * TMF671 Promotion, extended as {@code NbaCampaign}. {@code eligibilityRule} is an expression over the
 * decision context (see docs/eligibility-rules.md); {@code action} lists the offerings the campaign recommends.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Promotion(
        String id,
        String href,
        @NotBlank String name,
        String description,
        String lifecycleStatus,
        String version,
        Instant lastUpdate,
        ValidFor validFor,
        List<Ref> channel,
        String eligibilityRule,
        @DecimalMin("0.0") @DecimalMax("50.0") Double controlGroupPercent,
        @PositiveOrZero Integer maxImpressionsPerDay,
        @NotEmpty List<@Valid PromotionAction> action,
        @JsonProperty("@type") String type,
        @JsonProperty("@baseType") String baseType) {

    public static final String TYPE = "NbaCampaign";
    public static final String BASE_TYPE = "Promotion";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Ref(String id, String name) {
    }

    /** TMF671 PromotionAction. {@code actionObjectId} references a ProductOffering in the action catalog. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PromotionAction(
            String actionType,
            @NotBlank String actionObjectId,
            @Positive Double priorityLever) {
    }
}
