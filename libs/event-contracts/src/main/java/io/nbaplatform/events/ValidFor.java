package io.nbaplatform.events;

import java.time.Instant;

/** TMF TimePeriod. Either bound may be null, meaning open-ended. */
public record ValidFor(Instant startDateTime, Instant endDateTime) {

    public boolean includes(Instant instant) {
        return (startDateTime == null || !instant.isBefore(startDateTime))
                && (endDateTime == null || instant.isBefore(endDateTime));
    }
}
