package io.nbaplatform.events;

import java.util.List;

/** Latest state of an action offering. {@code deleted=true} removes it from decisioning. */
public record OfferingChanged(
        String id,
        String name,
        String description,
        String category,
        String lifecycleStatus,
        List<String> channels,
        double businessValue,
        ValidFor validFor,
        PropensityModel propensityModel,
        boolean deleted,
        long version) {
}
