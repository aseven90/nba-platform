package io.nbaplatform.decision.spi;

/** Pipeline stages, executed in declaration order. */
public enum Stage {
    /** Produce candidate actions from active campaigns. */
    CANDIDATES,
    /** Remove candidates the party may not receive: consent, rules, contact policy, control groups. */
    ELIGIBILITY,
    /** Estimate response propensity per candidate. */
    SCORING,
    /** Adjust scores to balance exploration and exploitation. */
    LEARNING,
    /** Combine propensity, value and business levers into a priority. */
    ARBITRATION,
    /** Choose the final ranked list. */
    SELECTION
}
