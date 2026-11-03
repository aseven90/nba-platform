package io.nbaplatform.events;

import java.time.Instant;

/** A party's interaction with a recommended action. */
public record InteractionRecorded(
        String interactionId,
        String partyId,
        String decisionId,
        String offeringId,
        String campaignId,
        String channel,
        Outcome outcome,
        Instant occurredAt) {

    public enum Outcome {
        IMPRESSION, ACCEPTED, DECLINED, IGNORED
    }
}
