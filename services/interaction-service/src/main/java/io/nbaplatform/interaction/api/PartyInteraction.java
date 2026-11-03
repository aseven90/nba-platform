package io.nbaplatform.interaction.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nbaplatform.events.InteractionRecorded.Outcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/**
 * TMF683 PartyInteraction, extended as {@code NbaInteraction} with the decision it answers and its outcome.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PartyInteraction(
        String id,
        String href,
        Instant interactionDate,
        @NotEmpty List<@Valid Ref> relatedParty,
        @NotEmpty List<@Valid Ref> channel,
        String decisionId,
        @NotNull @Valid Ref productOffering,
        Ref promotion,
        @NotNull Outcome outcome,
        @JsonProperty("@type") String type,
        @JsonProperty("@baseType") String baseType) {

    public static final String TYPE = "NbaInteraction";
    public static final String BASE_TYPE = "PartyInteraction";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Ref(@NotBlank String id, String name, String role) {
    }
}
