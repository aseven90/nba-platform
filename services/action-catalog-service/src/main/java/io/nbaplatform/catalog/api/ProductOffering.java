package io.nbaplatform.catalog.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nbaplatform.events.PropensityModel;
import io.nbaplatform.events.ValidFor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;

/**
 * TMF620 ProductOffering, extended as {@code NbaActionOffering} with {@code businessValue} and
 * {@code propensityModel}. Only the subset of TMF620 attributes the platform uses is modelled.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductOffering(
        String id,
        String href,
        @NotBlank String name,
        String description,
        String lifecycleStatus,
        String version,
        Instant lastUpdate,
        ValidFor validFor,
        List<Ref> category,
        List<Ref> channel,
        @PositiveOrZero Double businessValue,
        PropensityModel propensityModel,
        @JsonProperty("@type") String type,
        @JsonProperty("@baseType") String baseType) {

    public static final String TYPE = "NbaActionOffering";
    public static final String BASE_TYPE = "ProductOffering";

    /** TMF entity reference (CategoryRef, ChannelRef). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Ref(String id, String name) {
    }
}
