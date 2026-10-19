package io.nbaplatform.decision.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nbaplatform.events.ValidFor;
import java.util.List;

/** TMF680 Recommendation, extended as {@code NbaRecommendation}. Its id is the decision id. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Recommendation(
        String id,
        String name,
        String type,
        ValidFor validFor,
        Ref channel,
        List<Ref> relatedParty,
        List<RecommendationItem> recommendationItem,
        boolean fallback,
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
            @JsonProperty("@type") String atType) {
    }
}
