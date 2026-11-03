package io.nbaplatform.decision.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/**
 * Body of {@code POST /recommendation}: a TMF680 Recommendation without items. The first related party is the
 * subject of the decision; {@code characteristic} carries the decision context (TMF Characteristic pattern).
 */
public record RecommendationRequest(
        @NotEmpty List<@Valid Ref> relatedParty,
        @NotNull @Valid Ref channel,
        List<@Valid Characteristic> characteristic,
        @Positive Integer maxItems) {

    public record Ref(@NotBlank String id, String name, String role) {
    }

    public record Characteristic(@NotBlank String name, Object value) {
    }
}
