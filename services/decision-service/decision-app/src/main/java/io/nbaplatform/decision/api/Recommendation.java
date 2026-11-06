package io.nbaplatform.decision.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nbaplatform.events.ValidFor;
import java.util.List;

/**
 * TMF680 Recommendation, extended as {@code NbaRecommendation}. Its id is the decision id. {@code suppressedItem}
 * and each item's {@code explanation} are only present when the caller asks for one ({@code ?explain=true}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Recommendation(
        String id,
        String name,
        String type,
        ValidFor validFor,
        Ref channel,
        List<Ref> relatedParty,
        List<RecommendationItem> recommendationItem,
        List<SuppressedItem> suppressedItem,
        boolean fallback,
        String fallbackReason,
        @JsonProperty("@type") String atType,
        @JsonProperty("@baseType") String baseType) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Ref(String id, String name, String role, @JsonProperty("@referredType") String referredType) {
    }

    /** TMF680 RecommendationItem; {@code priority} is the rank (1 = best). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecommendationItem(
            String id,
            int priority,
            Ref productOffering,
            Ref promotion,
            Double score,
            Double propensity,
            List<String> reasonCode,
            Explanation explanation,
            @JsonProperty("@type") String atType) {
    }

    /**
     * Only with {@code ?explain=true}: {@code priority = propensity x value x priorityLever}, and where the
     * propensity came from.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Explanation(
            String scorer,
            String modelName,
            String modelVersion,
            double modelPropensity,
            double propensity,
            double value,
            double priorityLever,
            double priority,
            String scoringFallback,
            List<Contribution> contributions) {
    }

    /** One feature's effect on the model output; weight and contribution are absent for opaque models. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Contribution(String feature, double value, Double weight, Double contribution) {
    }

    /** A candidate the pipeline removed, with the stage that removed it and why. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SuppressedItem(Ref productOffering, Ref promotion, String stage, String reasonCode) {
    }
}
