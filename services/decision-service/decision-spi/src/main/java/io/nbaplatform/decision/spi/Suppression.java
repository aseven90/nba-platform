package io.nbaplatform.decision.spi;

/** A candidate removed from the decision, with the stage and reason, for explainability. */
public record Suppression(Candidate candidate, String stage, String reasonCode) {
}
